package com.condohub.financial.service;

import com.condohub.common.exception.BusinessException;
import com.condohub.financial.dto.LaunchChargesRequest;
import com.condohub.financial.entity.Invoice;
import com.condohub.financial.enums.InvoiceStatus;
import com.condohub.financial.repository.InvoiceRepository;
import com.condohub.financial.repository.FinancialConfigRepository;
import com.condohub.unit.repository.UnitRepository;
import com.condohub.audit.service.AuditService;
import com.condohub.auth.model.AppUserDetails;
import com.condohub.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialLaunchService {

    private final InvoiceRepository invoiceRepository;
    private final FinancialConfigRepository financialConfigRepository;
    private final UnitRepository unitRepository;
    private final AuditService auditService;

    /**
     * Lança cobranças em lote.
     *
     * SEGURANÇA:
     * 1. condominiumId é sempre resolvido do JWT via UserContext — nunca do body
     * 2. Todas as unidades alvo são validadas como pertencentes ao condomínio do JWT
     * 3. Duplicatas são bloqueadas via launchKey único
     */
    @Transactional
    public List<Invoice> launchCharges(LaunchChargesRequest request, AppUserDetails currentUser) {

        // 1. SEMPRE resolver condominiumId do JWT
        Long condominiumId = UserContext.resolveCondominiumId(currentUser);

        log.info("Lançamento de cobranças — condominiumId={} criterio={} tipo={} user={}",
                condominiumId, request.getCriterio(), request.getTipo(), currentUser.getUsername());

        // 2. Resolver lista de unitIds alvo
        List<Long> targetUnitIds = resolveTargetUnits(condominiumId, request);

        if (targetUnitIds.isEmpty()) {
            throw new BusinessException("Nenhuma unidade encontrada para os critérios informados.");
        }

        // 3. VALIDAÇÃO DE OWNERSHIP — todas as unidades DEVEM pertencer ao condomínio do JWT
        validateUnitsOwnership(condominiumId, targetUnitIds);

        // 4. Criar invoices (duplicatas bloqueadas por launchKey)
        String launchKey = buildLaunchKey(condominiumId, request);
        List<Invoice> invoices = targetUnitIds.stream()
                .filter(unitId -> !invoiceRepository.existsByCondominiumIdAndUnitIdAndLaunchKey(
                        condominiumId, unitId, launchKey))
                .map(unitId -> buildInvoice(condominiumId, unitId, request, launchKey))
                .toList();

        List<Invoice> saved = invoiceRepository.saveAll(invoices);

        // 5. Auditoria
        auditService.log(
                condominiumId,
                currentUser,
                "Financeiro",
                "Geração de cobranças em lote",
                String.format("Cobranças geradas em lote — %s • %s • %d unidades afetadas",
                        request.getCompetencia(), request.getTipo(), saved.size())
        );

        log.info("Lançamento concluído — condominiumId={} invoices={}", condominiumId, saved.size());
        return saved;
    }

    /**
     * Valida que TODOS os unitIds pertencem ao condominiumId resolvido do JWT.
     * Lança BusinessException se qualquer unidade for de outro condomínio.
     *
     * Proteção contra: FINANCEIRO/ADMIN passando unitIds arbitrários no body
     * para cobrar unidades de outros condomínios.
     */
    private void validateUnitsOwnership(Long condominiumId, List<Long> unitIds) {
        List<Long> foreignUnits = unitIds.stream()
                .filter(unitId -> !unitRepository.existsByIdAndCondominiumId(unitId, condominiumId))
                .toList();

        if (!foreignUnits.isEmpty()) {
            log.error("TENTATIVA DE ACESSO CROSS-TENANT — condominiumId={} unitIds inválidos={}",
                    condominiumId, foreignUnits);
            throw new BusinessException(
                    "Uma ou mais unidades não pertencem ao condomínio informado: " + foreignUnits
            );
        }
    }

    /**
     * Resolve a lista de unitIds com base no critério da request.
     * SEMPRE filtra por condominiumId do JWT — nunca usa IDs sem validação.
     */
    private List<Long> resolveTargetUnits(Long condominiumId, LaunchChargesRequest request) {
        return switch (request.getCriterio()) {
            case "TODAS" -> unitRepository.findIdsByCondominiumId(condominiumId);
            case "UNIDADE" -> {
                if (request.getTargetUnitId() == null) {
                    throw new BusinessException("targetUnitId é obrigatório para critério UNIDADE.");
                }
                yield List.of(request.getTargetUnitId());
            }
            case "BLOCO" -> {
                if (request.getTargetBlock() == null || request.getTargetBlock().isBlank()) {
                    throw new BusinessException("targetBlock é obrigatório para critério BLOCO.");
                }
                yield unitRepository.findIdsByCondominiumIdAndBlock(condominiumId, request.getTargetBlock());
            }
            case "LISTA" -> {
                if (request.getTargetUnitIds() == null || request.getTargetUnitIds().isEmpty()) {
                    throw new BusinessException("targetUnitIds é obrigatório para critério LISTA.");
                }
                yield request.getTargetUnitIds();
            }
            default -> throw new BusinessException("Critério inválido: " + request.getCriterio());
        };
    }

    private String buildLaunchKey(Long condominiumId, LaunchChargesRequest request) {
        return String.format("%d-%s-%s-%s",
                condominiumId,
                request.getCompetencia(),
                request.getTipo(),
                request.getCriterio());
    }

    private Invoice buildInvoice(Long condominiumId, Long unitId,
                                  LaunchChargesRequest request, String launchKey) {
        Invoice invoice = new Invoice();
        invoice.setCondominiumId(condominiumId);
        invoice.setUnitId(unitId);
        invoice.setTipo(request.getTipo());
        invoice.setValor(request.getValorPorUnidade());
        invoice.setVencimento(request.getVencimento());
        invoice.setCompetencia(request.getCompetencia());
        invoice.setDescricao(request.getDescricao());
        invoice.setTitulo(request.getTitulo());
        invoice.setFormaPrincipal(request.getFormaPrincipal());
        invoice.setLaunchKey(launchKey);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoice;
    }
}
