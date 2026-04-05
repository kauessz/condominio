package com.example.condo.service;

import com.example.condo.dto.financial.FinancialSummaryResponse;
import com.example.condo.dto.financial.InvoiceDetailResponse;
import com.example.condo.dto.financial.InvoiceListItemResponse;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.FinancialConfig;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.Unit;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.FinancialConfigRepository;
import com.example.condo.repo.InvoiceRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FinancialService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FinancialConfigRepository configRepo;
    private final InvoiceRepository invoiceRepo;
    private final UnitRepository unitRepo;
    private final CondominiumRepository condominiumRepo;
    private final AuditService auditService;

    public FinancialService(FinancialConfigRepository configRepo,
                            InvoiceRepository invoiceRepo,
                            UnitRepository unitRepo,
                            CondominiumRepository condominiumRepo,
                            AuditService auditService) {
        this.configRepo = configRepo;
        this.invoiceRepo = invoiceRepo;
        this.unitRepo = unitRepo;
        this.condominiumRepo = condominiumRepo;
        this.auditService = auditService;
    }

    public Optional<FinancialConfig> getConfig(Long condoIdParam) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) {
            return Optional.empty();
        }
        return configRepo.findByTenantIdAndCondominiumId(tenant, condoId);
    }

    @Transactional
    public FinancialConfig saveConfig(Long condoIdParam, BigDecimal monthlyFee, int dueDay,
                                      BigDecimal lateFeePct, BigDecimal interestPct,
                                      String pixKey, String pixKeyType) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) {
            throw new BusinessException("condominiumId é obrigatório");
        }
        if (dueDay < 1 || dueDay > 28) {
            throw new BusinessException("Dia de vencimento deve estar entre 1 e 28");
        }

        FinancialConfig config = configRepo.findByTenantIdAndCondominiumId(tenant, condoId)
            .orElse(new FinancialConfig());
        FinancialConfig before = copyConfig(config);

        config.setTenantId(tenant);
        config.setCondominiumId(condoId);
        config.setMonthlyFee(monthlyFee != null ? monthlyFee : BigDecimal.ZERO);
        config.setDueDay(dueDay);
        config.setLateFeePct(lateFeePct != null ? lateFeePct : new BigDecimal("2.00"));
        config.setInterestPct(interestPct != null ? interestPct : new BigDecimal("1.00"));
        config.setPixKey(pixKey);
        config.setPixKeyType(pixKeyType);
        config.setUpdatedAt(Instant.now());
        config = configRepo.save(config);
        auditService.log("CONFIG_CHANGED", "FinancialConfig", config.getId(), condoId, before.getId() == null ? null : before, config);
        return config;
    }

    public Page<InvoiceListItemResponse> listInvoices(Long condoIdParam, Long unitIdParam, String statusStr, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        Long effectiveUnitId = resolveScopedUnitId(unitIdParam);
        Invoice.Status status = parseStatus(statusStr);

        Page<Invoice> invoices;
        if (UserContext.isSuperuser() && condoId == null) {
            invoices = invoiceRepo.searchByTenant(tenant, effectiveUnitId, status, pageable);
        } else if (condoId == null) {
            return Page.empty(pageable);
        } else {
            invoices = invoiceRepo.search(tenant, condoId, effectiveUnitId, status, pageable);
        }

        return invoices.map(this::toListItem);
    }

    public InvoiceDetailResponse getInvoice(Long id) {
        Invoice inv = getInvoiceEntity(id);
        return toDetail(inv);
    }

    @Transactional
    public Map<String, Object> launchCharges(Long condoIdParam,
                                             String criterion,
                                             String appliesTo,
                                             String chargeTypeStr,
                                             BigDecimal amount,
                                             String title,
                                             String description,
                                             String referenceMonth,
                                             LocalDate dueDate,
                                             Long targetUnitId,
                                             List<Long> targetUnitIds,
                                             List<String> targetBlocks) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) {
            throw new BusinessException("condominiumId é obrigatório");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor da cobrança deve ser maior que zero");
        }
        String normalizedCriterion = normalizeCriterion(criterion);
        Invoice.ChargeType chargeType = parseChargeType(chargeTypeStr);
        LocalDate effectiveDueDate = dueDate != null ? dueDate : LocalDate.now().plusDays(10);
        String effectiveReferenceMonth = normalizeReferenceMonth(referenceMonth, effectiveDueDate);
        String effectiveTitle = title != null && !title.isBlank()
            ? title.trim()
            : defaultTitle(chargeType, normalizedCriterion, effectiveReferenceMonth);
        String normalizedAppliesTo = normalizeAppliesTo(appliesTo);
        List<Unit> units = resolveLaunchUnits(tenant, condoId, normalizedAppliesTo, targetUnitId, targetUnitIds, targetBlocks);
        String launchKey = buildLaunchKey(normalizedCriterion, chargeType, effectiveReferenceMonth, effectiveDueDate, effectiveTitle)
            + ":" + normalizedAppliesTo;

        List<Invoice> created = new ArrayList<>();
        int skipped = 0;
        for (Unit unit : units) {
            if (invoiceRepo.existsByUnitIdAndLaunchKey(unit.getId(), launchKey)) {
                skipped++;
                continue;
            }
            Invoice invoice = new Invoice();
            invoice.setTenantId(tenant);
            invoice.setCondominiumId(condoId);
            invoice.setUnitId(unit.getId());
            invoice.setReferenceMonth(effectiveReferenceMonth);
            invoice.setChargeType(chargeType);
            invoice.setTitle(effectiveTitle);
            invoice.setDescription(description);
            invoice.setLaunchKey(launchKey);
            invoice.setAmount(amount);
            invoice.setDueDate(effectiveDueDate);
            invoice.setStatus(Invoice.Status.PENDING);
            invoice.setCreatedAt(Instant.now());
            created.add(invoice);
        }

        List<Invoice> saved = created.isEmpty() ? List.of() : invoiceRepo.saveAll(created);
        for (Invoice invoice : saved) {
            auditService.log(
                "CREATE",
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                null,
                invoice,
                Map.of(
                    "criterion", normalizedCriterion,
                    "appliesTo", normalizedAppliesTo,
                    "launchKey", launchKey,
                    "chargeType", invoice.getChargeType().name(),
                    "targetUnitId", targetUnitId,
                    "targetUnitIds", targetUnitIds == null ? List.of() : targetUnitIds,
                    "targetBlocks", targetBlocks == null ? List.of() : targetBlocks
                )
            );
        }

        return Map.of(
            "createdCount", saved.size(),
            "skippedCount", skipped,
            "criterion", normalizedCriterion,
            "appliesTo", normalizedAppliesTo,
            "chargeType", chargeType.name(),
            "launchKey", launchKey,
            "referenceMonth", effectiveReferenceMonth,
            "dueDate", effectiveDueDate,
            "targetUnitCount", units.size()
        );
    }

    @Transactional
    public List<Invoice> generateMonthlyInvoices(Long condoId, String tenant, YearMonth month) {
        FinancialConfig config = configRepo.findByTenantIdAndCondominiumId(tenant, condoId)
            .orElseThrow(() -> new BusinessException("Configuração financeira não encontrada para o condomínio " + condoId));
        if (config.getMonthlyFee().compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        String referenceMonth = month.format(YEAR_MONTH_FORMAT);
        LocalDate dueDate = month.atDay(Math.min(config.getDueDay(), month.lengthOfMonth()));
        String launchKey = buildLaunchKey("MONTHLY", Invoice.ChargeType.CONDOMINIO, referenceMonth, dueDate, "Taxa condominial");

        List<Unit> units = unitRepo.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc(tenant, condoId);
        List<Invoice> created = new ArrayList<>();
        for (Unit unit : units) {
            if (invoiceRepo.existsByUnitIdAndLaunchKey(unit.getId(), launchKey)) {
                continue;
            }
            Invoice inv = new Invoice();
            inv.setTenantId(tenant);
            inv.setCondominiumId(condoId);
            inv.setUnitId(unit.getId());
            inv.setReferenceMonth(referenceMonth);
            inv.setChargeType(Invoice.ChargeType.CONDOMINIO);
            inv.setTitle("Taxa condominial " + referenceMonth);
            inv.setDescription("Cobrança mensal automática do condomínio");
            inv.setLaunchKey(launchKey);
            inv.setAmount(config.getMonthlyFee());
            inv.setDueDate(dueDate);
            inv.setStatus(Invoice.Status.PENDING);
            inv.setCreatedAt(Instant.now());
            created.add(inv);
        }
        return created.isEmpty() ? List.of() : invoiceRepo.saveAll(created);
    }

    @Transactional
    public Invoice registerPayment(Long id, BigDecimal paidAmount, String paymentMethodStr, String notes) {
        Invoice inv = getInvoiceEntity(id);
        Invoice before = copyInvoice(inv);
        if (inv.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("Fatura já está paga");
        }
        if (inv.getStatus() == Invoice.Status.CANCELLED || inv.getStatus() == Invoice.Status.WAIVED) {
            throw new BusinessException("Não é possível registrar pagamento de fatura cancelada/dispensada");
        }

        Invoice.PaymentMethod method;
        try {
            method = Invoice.PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
        } catch (Exception ignored) {
            method = Invoice.PaymentMethod.OTHER;
        }

        inv.setPaidAt(Instant.now());
        inv.setPaidAmount(paidAmount != null ? paidAmount : inv.getAmount());
        inv.setPaymentMethod(method);
        inv.setPaymentNotes(notes);
        inv.setStatus(Invoice.Status.PAID);
        inv.setRegisteredBy(UserContext.userId());
        inv = invoiceRepo.save(inv);
        auditService.log("PAYMENT_REGISTERED", "Invoice", inv.getId(), inv.getCondominiumId(), before, inv);
        return inv;
    }

    public FinancialSummaryResponse summary(Long condoIdParam) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        Long unitId = isMorador() ? UserContext.unitId() : null;

        if (!UserContext.isSuperuser() && condoId == null) {
            return new FinancialSummaryResponse(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
        }

        UserContext.Data ctx = UserContext.get();
        Long scopedCondominiumId = UserContext.isSuperuser()
            ? condoId
            : (ctx != null ? ctx.condominiumId() : null);

        Object[] row = normalizeSummaryRow(invoiceRepo.summary(
            tenant,
            scopedCondominiumId,
            unitId,
            null
        ));

        long totalInvoices = asLong(row, 0);
        BigDecimal totalAmount = asBigDecimal(row, 1);
        BigDecimal paidAmount = asBigDecimal(row, 2);
        BigDecimal pendingAmount = asBigDecimal(row, 3);
        BigDecimal overdueAmount = asBigDecimal(row, 4);
        double delinquencyPct = totalAmount.signum() > 0
            ? overdueAmount.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, java.math.RoundingMode.HALF_UP).doubleValue()
            : 0.0;

        return new FinancialSummaryResponse(
            totalInvoices,
            totalAmount,
            paidAmount,
            pendingAmount,
            overdueAmount,
            delinquencyPct
        );
    }

    private Invoice getInvoiceEntity(Long id) {
        String tenant = TenantContext.get();
        Invoice inv = invoiceRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Fatura", "id", id));
        enforceSameCondominium(inv.getCondominiumId());
        if (isMorador() && !Objects.equals(inv.getUnitId(), UserContext.unitId())) {
            throw new ResourceNotFoundException("Fatura", "id", id);
        }
        return inv;
    }

    private InvoiceListItemResponse toListItem(Invoice invoice) {
        Map<Long, String> condoNames = resolveCondoNames(Set.of(invoice.getCondominiumId()));
        Map<Long, String> unitLabels = resolveUnitLabels(Set.of(invoice.getUnitId()));
        return new InvoiceListItemResponse(
            invoice.getId(),
            invoice.getCondominiumId(),
            condoNames.get(invoice.getCondominiumId()),
            invoice.getUnitId(),
            unitLabels.get(invoice.getUnitId()),
            invoice.getReferenceMonth(),
            invoice.getChargeType().name(),
            invoice.getTitle(),
            invoice.getDescription(),
            invoice.getAmount(),
            invoice.getDueDate(),
            invoice.getStatus().name(),
            invoice.getPaidAt(),
            invoice.getPaidAmount(),
            invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null
        );
    }

    private InvoiceDetailResponse toDetail(Invoice invoice) {
        Map<Long, String> condoNames = resolveCondoNames(Set.of(invoice.getCondominiumId()));
        Map<Long, String> unitLabels = resolveUnitLabels(Set.of(invoice.getUnitId()));
        return new InvoiceDetailResponse(
            invoice.getId(),
            invoice.getCondominiumId(),
            condoNames.get(invoice.getCondominiumId()),
            invoice.getUnitId(),
            unitLabels.get(invoice.getUnitId()),
            invoice.getReferenceMonth(),
            invoice.getChargeType().name(),
            invoice.getTitle(),
            invoice.getDescription(),
            invoice.getAmount(),
            invoice.getDueDate(),
            invoice.getStatus().name(),
            invoice.getPaidAt(),
            invoice.getPaidAmount(),
            invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null,
            invoice.getPaymentNotes(),
            invoice.getRegisteredBy(),
            invoice.getCreatedAt()
        );
    }

    private Map<Long, String> resolveCondoNames(Set<Long> condominiumIds) {
        if (condominiumIds.isEmpty()) {
            return Map.of();
        }
        return condominiumRepo.findAllById(condominiumIds).stream()
            .collect(Collectors.toMap(Condominium::getId, Condominium::getName));
    }

    private Map<Long, String> resolveUnitLabels(Set<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return unitRepo.findByTenantIdAndIdIn(TenantContext.get(), unitIds).stream()
            .collect(Collectors.toMap(Unit::getId, this::formatUnitLabel));
    }

    private String formatUnitLabel(Unit unit) {
        String base = unit.getNumber() != null && !unit.getNumber().isBlank()
            ? unit.getNumber()
            : unit.getCode();
        if (unit.getBlock() != null && !unit.getBlock().isBlank()) {
            return "Unidade " + base + " • Bloco " + unit.getBlock();
        }
        return "Unidade " + base;
    }

    private Invoice.Status parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return Invoice.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Status inválido: " + statusStr);
        }
    }

    private Invoice.ChargeType parseChargeType(String chargeTypeStr) {
        try {
            return Invoice.ChargeType.valueOf((chargeTypeStr == null || chargeTypeStr.isBlank() ? "CONDOMINIO" : chargeTypeStr).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de cobrança inválido: " + chargeTypeStr);
        }
    }

    private String normalizeCriterion(String criterion) {
        String normalized = criterion == null || criterion.isBlank() ? "MONTHLY" : criterion.trim().toUpperCase();
        if (!normalized.equals("MONTHLY") && !normalized.equals("ONE_TIME")) {
            throw new BusinessException("Critério de cobrança inválido: " + criterion);
        }
        return normalized;
    }

    private String normalizeAppliesTo(String appliesTo) {
        String normalized = appliesTo == null || appliesTo.isBlank() ? "ALL_UNITS" : appliesTo.trim().toUpperCase();
        if (!Set.of("ALL_UNITS", "SINGLE_UNIT", "SPECIFIC_UNITS", "SPECIFIC_BLOCKS").contains(normalized)) {
            throw new BusinessException("Escopo de cobrança inválido: " + appliesTo);
        }
        return normalized;
    }

    private String normalizeReferenceMonth(String referenceMonth, LocalDate dueDate) {
        if (referenceMonth != null && !referenceMonth.isBlank()) {
            try {
                return YearMonth.parse(referenceMonth, YEAR_MONTH_FORMAT).format(YEAR_MONTH_FORMAT);
            } catch (Exception e) {
                throw new BusinessException("Competência inválida. Use o formato yyyy-MM");
            }
        }
        return YearMonth.from(dueDate).format(YEAR_MONTH_FORMAT);
    }

    private String defaultTitle(Invoice.ChargeType chargeType, String criterion, String referenceMonth) {
        String prefix = switch (chargeType) {
            case REFORMA -> "Rateio de reforma";
            case EXTRA -> "Taxa extraordinária";
            case FUNDO_RESERVA -> "Contribuição ao fundo de reserva";
            case MULTA -> "Multa";
            case OUTROS -> "Cobrança avulsa";
            case CONDOMINIO -> "Mensalidade ordinária";
        };
        return criterion.equals("ONE_TIME") ? prefix : prefix + " " + referenceMonth;
    }

    private String buildLaunchKey(String criterion, Invoice.ChargeType chargeType, String referenceMonth, LocalDate dueDate, String title) {
        return criterion
            + ":" + chargeType.name()
            + ":" + referenceMonth
            + ":" + dueDate
            + ":" + title.trim().toLowerCase().replaceAll("\\s+", "-");
    }

    private List<Unit> resolveLaunchUnits(String tenant,
                                          Long condoId,
                                          String appliesTo,
                                          Long targetUnitId,
                                          List<Long> targetUnitIds,
                                          List<String> targetBlocks) {
        List<Unit> condoUnits = unitRepo.findByTenantIdAndCondominiumIdOrderByBlockAscNumberAsc(tenant, condoId);
        if (condoUnits.isEmpty()) {
            throw new BusinessException("Nenhuma unidade cadastrada para o condomínio selecionado");
        }

        return switch (appliesTo) {
            case "ALL_UNITS" -> condoUnits;
            case "SINGLE_UNIT" -> {
                if (targetUnitId == null) {
                    throw new BusinessException("Selecione a unidade de destino para a cobrança");
                }
                yield condoUnits.stream()
                    .filter(unit -> unit.getId().equals(targetUnitId))
                    .findFirst()
                    .map(List::of)
                    .orElseThrow(() -> new BusinessException("A unidade selecionada não pertence ao condomínio informado"));
            }
            case "SPECIFIC_UNITS" -> {
                if (targetUnitIds == null || targetUnitIds.isEmpty()) {
                    throw new BusinessException("Selecione ao menos uma unidade de destino");
                }
                Set<Long> allowedIds = condoUnits.stream().map(Unit::getId).collect(Collectors.toSet());
                if (!allowedIds.containsAll(targetUnitIds)) {
                    throw new BusinessException("Uma ou mais unidades selecionadas não pertencem ao condomínio informado");
                }
                yield condoUnits.stream()
                    .filter(unit -> targetUnitIds.contains(unit.getId()))
                    .toList();
            }
            case "SPECIFIC_BLOCKS" -> {
                if (targetBlocks == null || targetBlocks.isEmpty()) {
                    throw new BusinessException("Selecione ao menos um bloco para a cobrança");
                }
                Set<String> normalizedBlocks = targetBlocks.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
                List<Unit> matchedUnits = condoUnits.stream()
                    .filter(unit -> unit.getBlock() != null && normalizedBlocks.contains(unit.getBlock().trim().toUpperCase()))
                    .toList();
                if (matchedUnits.isEmpty()) {
                    throw new BusinessException("Nenhuma unidade encontrada para os blocos selecionados");
                }
                yield matchedUnits;
            }
            default -> throw new BusinessException("Escopo de cobrança inválido: " + appliesTo);
        };
    }

    private Long resolveScopedUnitId(Long unitIdParam) {
        if (isMorador()) {
            return UserContext.unitId();
        }
        return unitIdParam;
    }

    private Object[] normalizeSummaryRow(Object rawRow) {
        if (rawRow instanceof Object[] row) {
            if (row.length > 0 && row[0] instanceof Object[]) {
                return (Object[]) row[0];
            }
            return row;
        }
        return new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private long asLong(Object[] row, int index) {
        Object value = row.length > index ? row[index] : null;
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal asBigDecimal(Object[] row, int index) {
        Object value = row.length > index ? row[index] : null;
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private void enforceSameCondominium(Long condoId) {
        Long effective = UserContext.resolveCondominiumId(condoId);
        if (effective != null && !effective.equals(condoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private FinancialConfig copyConfig(FinancialConfig source) {
        FinancialConfig copy = new FinancialConfig();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setMonthlyFee(source.getMonthlyFee());
        copy.setDueDay(source.getDueDay());
        copy.setLateFeePct(source.getLateFeePct());
        copy.setInterestPct(source.getInterestPct());
        copy.setPixKey(source.getPixKey());
        copy.setPixKeyType(source.getPixKeyType());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private Invoice copyInvoice(Invoice source) {
        Invoice copy = new Invoice();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setUnitId(source.getUnitId());
        copy.setReferenceMonth(source.getReferenceMonth());
        copy.setChargeType(source.getChargeType());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setLaunchKey(source.getLaunchKey());
        copy.setAmount(source.getAmount());
        copy.setDueDate(source.getDueDate());
        copy.setPaidAt(source.getPaidAt());
        copy.setPaidAmount(source.getPaidAmount());
        copy.setPaymentMethod(source.getPaymentMethod());
        copy.setPaymentNotes(source.getPaymentNotes());
        copy.setStatus(source.getStatus());
        copy.setRegisteredBy(source.getRegisteredBy());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }
}
