package com.example.condo.service;

import com.example.condo.dto.onboarding.CondominiumRequestDto;
import com.example.condo.dto.onboarding.CondominiumRequestResponse;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.CondominiumRequest;
import com.example.condo.entity.User;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.CondominiumRequestRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service de onboarding de condomínios.
 *
 * Fluxo:
 * 1. createRequest() — salva solicitação pública (PENDING)
 * 2. approve()       — cria Condominium + usuário ADMIN com senha temporária
 * 3. reject()        — marca REJECTED com motivo
 */
@Service
@Transactional(readOnly = true)
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final CondominiumRequestRepository requestRepo;
    private final CondominiumRepository condominiumRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public OnboardingService(
        CondominiumRequestRepository requestRepo,
        CondominiumRepository condominiumRepo,
        UserRepository userRepo,
        PasswordEncoder passwordEncoder
    ) {
        this.requestRepo = requestRepo;
        this.condominiumRepo = condominiumRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cria uma solicitação pública de cadastro de condomínio.
     */
    @Transactional
    public void createRequest(CondominiumRequestDto dto) {
        CondominiumRequest request = new CondominiumRequest();
        request.setCondominiumName(dto.condominiumName().trim());
        request.setCnpj(dto.cnpj() != null ? dto.cnpj().trim() : null);
        request.setAddress(dto.address() != null ? dto.address().trim() : null);
        request.setRequesterName(dto.requesterName().trim());
        request.setRequesterEmail(dto.requesterEmail().trim().toLowerCase());
        request.setRequesterPhone(dto.requesterPhone() != null ? dto.requesterPhone().trim() : null);
        request.setRequesterRole(dto.requesterRole());
        request.setStatus(CondominiumRequest.Status.PENDING);

        requestRepo.save(request);

        log.info("[Onboarding] Nova solicitação recebida: condomínio='{}' email='{}'",
            request.getCondominiumName(), request.getRequesterEmail());
    }

    /**
     * Lista solicitações por status (para o painel do SUPERUSER).
     */
    public Page<CondominiumRequestResponse> list(String status, Pageable pageable) {
        CondominiumRequest.Status statusEnum = status != null
            ? CondominiumRequest.Status.valueOf(status.toUpperCase())
            : CondominiumRequest.Status.PENDING;

        return requestRepo
            .findByStatusOrderByCreatedAtDesc(statusEnum, pageable)
            .map(CondominiumRequestResponse::from);
    }

    /**
     * Contagem de solicitações pendentes (para badge no dashboard).
     */
    public long countPending() {
        return requestRepo.countByStatus(CondominiumRequest.Status.PENDING);
    }

    /**
     * Aprova uma solicitação:
     * 1. Cria o condomínio
     * 2. Cria usuário ADMIN com senha temporária
     * 3. Atualiza status da solicitação para APPROVED
     */
    @Transactional
    public void approve(Long requestId, Long reviewerId) {
        CondominiumRequest request = requestRepo.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Solicitação", "id", requestId));

        if (request.getStatus() != CondominiumRequest.Status.PENDING) {
            throw new com.example.condo.exception.BusinessException(
                "Esta solicitação já foi " + request.getStatus().name().toLowerCase());
        }

        // 1. Criar o condomínio com tenantId derivado do nome
        String tenantId = generateTenantId(request.getCondominiumName());

        Condominium condo = new Condominium();
        condo.setTenantId(tenantId);
        condo.setName(request.getCondominiumName());
        condo.setCnpj(request.getCnpj());
        condominiumRepo.save(condo);

        // 2. Criar usuário ADMIN com senha temporária
        String tempPassword = generateTempPassword();

        User admin = new User();
        admin.setTenantId(tenantId);
        admin.setEmail(request.getRequesterEmail());
        admin.setName(request.getRequesterName());
        admin.setPasswordHash(passwordEncoder.encode(tempPassword));
        admin.setRole(Role.ADMIN);
        admin.setCondominiumId(condo.getId());
        admin.setMustChangePassword(true);
        userRepo.save(admin);

        // 3. Atualizar solicitação
        request.setStatus(CondominiumRequest.Status.APPROVED);
        request.setReviewedAt(Instant.now());
        request.setReviewedBy(reviewerId);
        requestRepo.save(request);

        log.info("[Onboarding] Solicitação {} aprovada: condomínio='{}' (tenant='{}') admin='{}'",
            requestId, condo.getName(), tenantId, admin.getEmail());
        log.info("[Onboarding] Senha temporária para {}: {} (deve ser trocada no primeiro login)",
            admin.getEmail(), tempPassword);

        // TODO Fase 2: enviar email com credenciais via Resend/SendGrid
        // emailService.sendWelcome(admin.getEmail(), tempPassword, condo.getName());
    }

    /**
     * Rejeita uma solicitação com motivo.
     */
    @Transactional
    public void reject(Long requestId, String reason, Long reviewerId) {
        CondominiumRequest request = requestRepo.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Solicitação", "id", requestId));

        if (request.getStatus() != CondominiumRequest.Status.PENDING) {
            throw new com.example.condo.exception.BusinessException(
                "Esta solicitação já foi " + request.getStatus().name().toLowerCase());
        }

        request.setStatus(CondominiumRequest.Status.REJECTED);
        request.setRejectionReason(reason.trim());
        request.setReviewedAt(Instant.now());
        request.setReviewedBy(reviewerId);
        requestRepo.save(request);

        log.info("[Onboarding] Solicitação {} rejeitada: motivo='{}'", requestId, reason);

        // TODO Fase 2: enviar email de rejeição ao solicitante
    }

    // ========== Helpers ==========

    /**
     * Gera um tenantId URL-safe a partir do nome do condomínio.
     * Ex: "Condomínio Jardim das Flores" → "condominio-jardim-das-flores-{uuid_short}"
     */
    private String generateTenantId(String condominiumName) {
        String slug = condominiumName.toLowerCase()
            .replaceAll("[áàãâä]", "a")
            .replaceAll("[éèêë]", "e")
            .replaceAll("[íìîï]", "i")
            .replaceAll("[óòõôö]", "o")
            .replaceAll("[úùûü]", "u")
            .replaceAll("[ç]", "c")
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");

        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        String tenant = slug + "-" + shortUuid;
        return tenant.length() > 64 ? tenant.substring(0, 64) : tenant;
    }

    /**
     * Gera uma senha temporária segura para o usuário ADMIN.
     * Formato: Condo@{4-digit-random}
     */
    private String generateTempPassword() {
        int suffix = (int) (Math.random() * 9000) + 1000;
        return "Condo@" + suffix;
    }
}
