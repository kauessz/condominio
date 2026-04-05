package com.example.condo.service;

import com.example.condo.dto.condominium.CreateCondominiumRequest;
import com.example.condo.dto.condominium.UpdateCondominiumRequest;
import com.example.condo.dto.governance.CreateGovernanceRequest;
import com.example.condo.dto.governance.GovernanceRequestResponse;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.GovernanceRequest;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.GovernanceRequestRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class GovernanceRequestService {

    private final GovernanceRequestRepository governanceRequestRepository;
    private final CondominiumRepository condominiumRepository;
    private final CondominiumService condominiumService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public GovernanceRequestService(
        GovernanceRequestRepository governanceRequestRepository,
        CondominiumRepository condominiumRepository,
        CondominiumService condominiumService,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.governanceRequestRepository = governanceRequestRepository;
        this.condominiumRepository = condominiumRepository;
        this.condominiumService = condominiumService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public Page<GovernanceRequestResponse> list(String status, Pageable pageable) {
        GovernanceRequest.Status statusEnum = status == null
            ? GovernanceRequest.Status.PENDING
            : GovernanceRequest.Status.valueOf(status.trim().toUpperCase());
        return governanceRequestRepository
            .findByTenantIdAndStatusOrderByCreatedAtDesc(TenantContext.get(), statusEnum, pageable)
            .map(GovernanceRequestResponse::from);
    }

    @Transactional
    public GovernanceRequestResponse create(CreateGovernanceRequest request) {
        UserContext.Data ctx = requireRequesterContext();
        GovernanceRequest.RequestType requestType = parseRequestType(request.requestType());
        Long condominiumId = resolveCondominiumIdForRequest(ctx, request);

        GovernanceRequest item = new GovernanceRequest();
        item.setTenantId(TenantContext.get());
        item.setRequestType(requestType);
        item.setTargetEntityType(GovernanceRequest.TargetEntityType.CONDOMINIUM);
        item.setTargetEntityId(request.targetEntityId());
        item.setCondominiumId(condominiumId);
        item.setRequestedByUserId(ctx.userId());
        item.setRequestedByRole(ctx.role());
        item.setStatus(GovernanceRequest.Status.PENDING);
        item.setPayloadBefore(loadCurrentPayload(request.targetEntityId()));
        item.setPayloadAfter(toJson(request.payloadAfter()));

        item = governanceRequestRepository.save(item);
        auditService.log(
            "CREATE",
            "GovernanceRequest",
            item.getId(),
            condominiumId,
            null,
            GovernanceRequestResponse.from(item),
            request.justification()
        );
        return GovernanceRequestResponse.from(item);
    }

    @Transactional
    public GovernanceRequestResponse approve(Long id) {
        GovernanceRequest request = findOrThrow(id);
        if (request.getStatus() != GovernanceRequest.Status.PENDING) {
            throw new BusinessException("Solicitação já foi processada.");
        }

        applyRequest(request);
        request.setStatus(GovernanceRequest.Status.APPROVED);
        request.setApprovedByUserId(requireApproverId());
        request.setApprovedAt(Instant.now());
        request = governanceRequestRepository.save(request);
        auditService.log("APPROVE", "GovernanceRequest", request.getId(), request.getCondominiumId(), null, GovernanceRequestResponse.from(request));
        return GovernanceRequestResponse.from(request);
    }

    @Transactional
    public GovernanceRequestResponse reject(Long id, String reason) {
        GovernanceRequest request = findOrThrow(id);
        if (request.getStatus() != GovernanceRequest.Status.PENDING) {
            throw new BusinessException("Solicitação já foi processada.");
        }
        request.setStatus(GovernanceRequest.Status.REJECTED);
        request.setApprovedByUserId(requireApproverId());
        request.setApprovedAt(Instant.now());
        request.setRejectionReason(reason.trim());
        request = governanceRequestRepository.save(request);
        auditService.log("REJECT", "GovernanceRequest", request.getId(), request.getCondominiumId(), null, GovernanceRequestResponse.from(request), reason);
        return GovernanceRequestResponse.from(request);
    }

    @Transactional
    public GovernanceRequestResponse cancel(Long id) {
        GovernanceRequest request = findOrThrow(id);
        UserContext.Data ctx = requireRequesterContext();
        if (!UserContext.isSuperuser() && !ctx.userId().equals(request.getRequestedByUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode cancelar esta solicitação.");
        }
        if (request.getStatus() != GovernanceRequest.Status.PENDING) {
            throw new BusinessException("Somente solicitações pendentes podem ser canceladas.");
        }
        request.setStatus(GovernanceRequest.Status.CANCELLED);
        request = governanceRequestRepository.save(request);
        auditService.log("CANCEL", "GovernanceRequest", request.getId(), request.getCondominiumId(), null, GovernanceRequestResponse.from(request));
        return GovernanceRequestResponse.from(request);
    }

    private void applyRequest(GovernanceRequest request) {
        JsonNode payloadAfter = request.getPayloadAfter();
        switch (request.getRequestType()) {
            case CREATE_CONDOMINIUM ->
                condominiumService.create(objectMapper.convertValue(payloadAfter, CreateCondominiumRequest.class));
            case UPDATE_CONDOMINIUM ->
                condominiumService.update(requireTargetEntityId(request), objectMapper.convertValue(payloadAfter, UpdateCondominiumRequest.class));
            case DELETE_CONDOMINIUM ->
                condominiumService.delete(requireTargetEntityId(request));
            case ACTIVATE_CONDOMINIUM ->
                condominiumService.setActive(requireTargetEntityId(request), true);
            case DEACTIVATE_CONDOMINIUM ->
                condominiumService.setActive(requireTargetEntityId(request), false);
        }
    }

    private Long requireTargetEntityId(GovernanceRequest request) {
        if (request.getTargetEntityId() == null) {
            throw new BusinessException("targetEntityId é obrigatório para esta solicitação");
        }
        return request.getTargetEntityId();
    }

    private Long requireApproverId() {
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.userId() == null || !UserContext.isSuperuser()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente SUPERUSER pode aprovar solicitações.");
        }
        return ctx.userId();
    }

    private GovernanceRequest findOrThrow(Long id) {
        return governanceRequestRepository.findByTenantIdAndId(TenantContext.get(), id)
            .orElseThrow(() -> new ResourceNotFoundException("GovernanceRequest", "id", id));
    }

    private JsonNode loadCurrentPayload(Long targetEntityId) {
        if (targetEntityId == null) return null;
        Condominium condominium = condominiumRepository.findByTenantIdAndId(TenantContext.get(), targetEntityId)
            .orElseThrow(() -> new ResourceNotFoundException("Condomínio", "id", targetEntityId));
        return objectMapper.valueToTree(com.example.condo.dto.condominium.CondominiumResponse.from(condominium));
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private UserContext.Data requireRequesterContext() {
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.userId() == null || ctx.role() == null) {
            throw new BusinessException("Contexto do usuário autenticado não disponível.");
        }
        return ctx;
    }

    private Long resolveCondominiumIdForRequest(UserContext.Data ctx, CreateGovernanceRequest request) {
        if (UserContext.isSuperuser()) {
            return request.condominiumId();
        }
        if (request.targetEntityId() != null) {
            return ctx.condominiumId();
        }
        return ctx.condominiumId();
    }

    private GovernanceRequest.RequestType parseRequestType(String raw) {
        try {
            return GovernanceRequest.RequestType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("requestType inválido.");
        }
    }
}
