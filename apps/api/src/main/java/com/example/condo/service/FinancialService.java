package com.example.condo.service;

import com.example.condo.audit.AuditAction;
import com.example.condo.audit.AuditModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.condo.dto.financial.FinancialBlockDelinquencyResponse;
import com.example.condo.dto.financial.FinancialPeriodSummaryResponse;
import com.example.condo.dto.financial.FinancialStatusBreakdownResponse;
import com.example.condo.dto.financial.FinancialSummaryResponse;
import com.example.condo.dto.financial.InvoiceDetailResponse;
import com.example.condo.dto.financial.InvoiceEventResponse;
import com.example.condo.dto.financial.InvoiceListItemResponse;
import com.example.condo.dto.financial.InvoiceNotificationResponse;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.FinancialConfig;
import com.example.condo.entity.FinancialNotification;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.InvoiceEvent;
import com.example.condo.entity.InvoiceWebhookEvent;
import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.FinancialConfigRepository;
import com.example.condo.repo.InvoiceEventRepository;
import com.example.condo.repo.InvoiceRepository;
import com.example.condo.repo.InvoiceWebhookEventRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FinancialService {

    private static final Logger log = LoggerFactory.getLogger(FinancialService.class);
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private enum AmountMode {
        PER_UNIT,
        TOTAL
    }

    private final FinancialConfigRepository configRepo;
    private final InvoiceRepository invoiceRepo;
    private final UnitRepository unitRepo;
    private final ResidentRepository residentRepo;
    private final CondominiumRepository condominiumRepo;
    private final InvoiceEventRepository invoiceEventRepo;
    private final InvoiceWebhookEventRepository invoiceWebhookEventRepo;
    private final FinancialNotificationService financialNotificationService;
    private final AuditService auditService;
    private final AsaasGatewayService asaasGatewayService;
    private final ObjectMapper objectMapper;

    public FinancialService(FinancialConfigRepository configRepo,
                            InvoiceRepository invoiceRepo,
                            UnitRepository unitRepo,
                            ResidentRepository residentRepo,
                            CondominiumRepository condominiumRepo,
                            InvoiceEventRepository invoiceEventRepo,
                            InvoiceWebhookEventRepository invoiceWebhookEventRepo,
                            FinancialNotificationService financialNotificationService,
                            AuditService auditService,
                            AsaasGatewayService asaasGatewayService,
                            ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.invoiceRepo = invoiceRepo;
        this.unitRepo = unitRepo;
        this.residentRepo = residentRepo;
        this.condominiumRepo = condominiumRepo;
        this.invoiceEventRepo = invoiceEventRepo;
        this.invoiceWebhookEventRepo = invoiceWebhookEventRepo;
        this.financialNotificationService = financialNotificationService;
        this.auditService = auditService;
        this.asaasGatewayService = asaasGatewayService;
        this.objectMapper = objectMapper;
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
    public FinancialConfig saveConfig(Long condoIdParam,
                                      BigDecimal monthlyFee,
                                      int dueDay,
                                      BigDecimal lateFeePct,
                                      BigDecimal interestPct,
                                      String pixKey,
                                      String pixKeyType,
                                      String defaultBillingType,
                                      Boolean notificationEmailEnabled,
                                      Boolean notificationWhatsappEnabled,
                                      Boolean asaasEnabled,
                                      String asaasWebhookToken) {
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
        config.setDefaultBillingType(resolveBillingType(defaultBillingType, null).name());
        config.setNotificationEmailEnabled(notificationEmailEnabled == null || notificationEmailEnabled);
        config.setNotificationWhatsappEnabled(Boolean.TRUE.equals(notificationWhatsappEnabled));
        config.setAsaasEnabled(Boolean.TRUE.equals(asaasEnabled));
        config.setAsaasWebhookToken(normalizeSecret(asaasWebhookToken));
        config.setUpdatedAt(Instant.now());
        config = configRepo.save(config);
        auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.UPDATE_FINANCIAL_CONFIG,
                "FinancialConfig",
                config.getId(),
                condoId,
                "Configuração financeira atualizada para o condomínio #" + condoId + ".",
                before.getId() == null ? null : sanitizeConfigForAudit(before),
                sanitizeConfigForAudit(config),
                financialConfigDetails(config)
        );
        return config;
    }

    @Transactional
    public Page<InvoiceListItemResponse> listInvoices(Long condoIdParam, Long unitIdParam, String statusStr, Pageable pageable) {
        return searchInvoices(condoIdParam, unitIdParam, null, statusStr, null, null, null, null, null, null, "dueDate", "DESC", pageable);
    }

    @Transactional
    public Page<InvoiceListItemResponse> searchInvoices(Long condoIdParam,
                                                        Long unitIdParam,
                                                        Long residentIdParam,
                                                        String statusStr,
                                                        String chargeTypeStr,
                                                        String query,
                                                        String referenceMonthFrom,
                                                        String referenceMonthTo,
                                                        LocalDate dueDateFrom,
                                                        LocalDate dueDateTo,
                                                        String sortByParam,
                                                        String directionParam,
                                                        Pageable pageable) {
        refreshOverdueInvoices();
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        Long effectiveUnitId = resolveScopedUnitId(unitIdParam, condoId);
        Long effectiveResidentId = resolveScopedResidentId(residentIdParam, condoId, effectiveUnitId);
        Invoice.Status status = parseStatus(statusStr);
        Invoice.ChargeType chargeType = parseChargeTypeOrNull(chargeTypeStr);
        String normalizedQuery = normalizeOptionalText(query);
        String searchPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        String normalizedReferenceMonthFrom = normalizeReferenceMonthBoundary(referenceMonthFrom, "inicial");
        String normalizedReferenceMonthTo = normalizeReferenceMonthBoundary(referenceMonthTo, "final");
        String sortBy = normalizeInvoiceSort(sortByParam);
        String direction = "ASC".equalsIgnoreCase(directionParam) ? "ASC" : "DESC";

        if (!UserContext.isSuperuser() && condoId == null) {
            return Page.empty(pageable);
        }

        Page<Invoice> invoices = invoiceRepo.searchAdvanced(
                tenant,
                condoId,
                effectiveUnitId,
                effectiveResidentId,
                status != null ? status.name() : null,
                chargeType != null ? chargeType.name() : null,
                normalizedReferenceMonthFrom,
                normalizedReferenceMonthTo,
                dueDateFrom,
                dueDateTo,
                searchPattern,
                sortBy,
                direction,
                pageable
        );

        Map<Long, String> condoNames = resolveCondoNames(
                invoices.getContent().stream().map(Invoice::getCondominiumId).collect(Collectors.toSet())
        );
        Map<Long, String> unitLabels = resolveUnitLabels(
                invoices.getContent().stream().map(Invoice::getUnitId).collect(Collectors.toSet())
        );
        Map<String, String> residentNames = resolveResidentNames(invoices.getContent());
        return invoices.map(invoice -> toListItem(invoice, condoNames, unitLabels, residentNames));
    }

    @Transactional
    public Page<InvoiceListItemResponse> listResidentInvoices(String statusStr,
                                                              String query,
                                                              String referenceMonthFrom,
                                                              String referenceMonthTo,
                                                              String sortBy,
                                                              String direction,
                                                              Pageable pageable) {
        if (!canAccessMyInvoicesPortal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Portal do morador indisponível para o perfil atual");
        }
        if (UserContext.unitId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário autenticado sem unidade vinculada");
        }
        return searchInvoices(
                UserContext.resolveCondominiumId(null),
                UserContext.unitId(),
                null,
                statusStr,
                null,
                query,
                referenceMonthFrom,
                referenceMonthTo,
                null,
                null,
                sortBy,
                direction,
                pageable
        );
    }

    @Transactional
    public InvoiceDetailResponse getInvoice(Long id) {
        refreshOverdueInvoices();
        Invoice invoice = getInvoiceEntity(id);
        Map<Long, String> condoNames = resolveCondoNames(Set.of(invoice.getCondominiumId()));
        Map<Long, String> unitLabels = resolveUnitLabels(Set.of(invoice.getUnitId()));
        return toDetail(invoice, condoNames, unitLabels);
    }

    @Transactional
    public Map<String, Object> launchCharges(Long condoIdParam,
                                             String criterion,
                                             String appliesTo,
                                             String chargeTypeStr,
                                             String amountModeStr,
                                             String billingTypeStr,
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
        AmountMode amountMode = parseAmountMode(amountModeStr);
        Invoice.BillingType billingType = resolveBillingType(billingTypeStr, loadConfig(condoId));
        String launchKey = buildLaunchKey(normalizedCriterion, chargeType, effectiveReferenceMonth, effectiveDueDate, effectiveTitle)
                + ":" + normalizedAppliesTo
                + ":" + amountMode.name();
        String apportionmentGroup = units.size() > 1 ? UUID.randomUUID().toString() : null;
        List<BigDecimal> apportionedAmounts = apportionAmounts(amountMode, amount, units.size());

        List<Invoice> saved = new ArrayList<>();
        int skipped = 0;
        for (int index = 0; index < units.size(); index++) {
            Unit unit = units.get(index);
            if (invoiceRepo.existsByTenantIdAndUnitIdAndLaunchKey(tenant, unit.getId(), launchKey)) {
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
            invoice.setAmount(apportionedAmounts.get(index));
            invoice.setDueDate(effectiveDueDate);
            invoice.setBillingType(billingType);
            invoice.setStatus(Invoice.Status.PENDING);
            invoice.setApportionmentGroup(apportionmentGroup);
            invoice.setApportionmentMode(amountMode == AmountMode.TOTAL ? Invoice.ApportionmentMode.EQUAL : Invoice.ApportionmentMode.NONE);
            invoice.setCreatedAt(Instant.now());
            try {
                Invoice persisted = invoiceRepo.saveAndFlush(invoice);
                saved.add(persisted);
                registerInvoiceCreated(persisted, normalizedCriterion, normalizedAppliesTo, launchKey, targetUnitId, targetUnitIds, targetBlocks, amountMode, amount);
            } catch (DataIntegrityViolationException ex) {
                if (isDuplicateLaunchConflict(ex)) {
                    skipped++;
                    continue;
                }
                throw ex;
            }
        }
        if (!saved.isEmpty()) {
            String batchAuditEntityId = buildBatchAuditEntityId(launchKey);
            auditService.log(
                    AuditModule.FINANCIAL,
                    AuditAction.GENERATE_CHARGE_BATCH,
                    "Invoice",
                    batchAuditEntityId,
                    condoId,
                    saved.size() + " cobrança(s) geradas em lote.",
                    null,
                    null,
                    batchDetails(saved.size(), skipped, launchKey, normalizedCriterion, normalizedAppliesTo, chargeType.name(), effectiveReferenceMonth, amountMode.name())
            );
            if (amountMode == AmountMode.TOTAL && saved.size() > 1) {
                auditService.log(
                        AuditModule.FINANCIAL,
                        AuditAction.EXTRA_FEE_APPORTIONMENT,
                        "Invoice",
                        apportionmentGroup,
                        condoId,
                        "Rateio extraordinário distribuído entre " + saved.size() + " unidade(s).",
                        null,
                        null,
                        apportionmentDetails(saved, amount, amountMode, normalizedAppliesTo)
                );
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("createdCount", saved.size());
        response.put("skippedCount", skipped);
        response.put("criterion", normalizedCriterion);
        response.put("appliesTo", normalizedAppliesTo);
        response.put("chargeType", chargeType.name());
        response.put("launchKey", launchKey);
        response.put("referenceMonth", effectiveReferenceMonth);
        response.put("dueDate", effectiveDueDate);
        response.put("targetUnitCount", units.size());
        response.put("amountMode", amountMode.name());
        response.put("billingType", billingType.name());
        return response;
    }

    @Transactional
    public InvoiceDetailResponse createExternalCharge(Long id, String billingTypeStr) {
        Invoice invoice = getInvoiceEntity(id);
        FinancialConfig config = loadConfig(invoice.getCondominiumId());
        if (config == null || !config.isAsaasEnabled()) {
            throw new BusinessException("Cobrança externa não está configurada para este condomínio");
        }
        if (invoice.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("Não é possível gerar cobrança externa para uma fatura já paga");
        }
        if (invoice.getStatus() == Invoice.Status.CANCELLED || invoice.getStatus() == Invoice.Status.WAIVED) {
            throw new BusinessException("Não é possível gerar cobrança externa para esta fatura");
        }
        if (invoice.getExternalProvider() == Invoice.Provider.ASAAS && invoice.getExternalChargeId() != null && !invoice.getExternalChargeId().isBlank()) {
            return toDetail(invoice, resolveCondoNames(Set.of(invoice.getCondominiumId())), resolveUnitLabels(Set.of(invoice.getUnitId())));
        }

        Invoice before = copyInvoice(invoice);
        Unit unit = unitRepo.findByTenantIdAndId(TenantContext.get(), invoice.getUnitId())
                .orElseThrow(() -> new BusinessException("Unidade da cobrança não encontrada"));
        Resident resident = residentRepo.findByTenantIdAndCondominiumIdAndUnitIdIn(TenantContext.get(), invoice.getCondominiumId(), List.of(invoice.getUnitId()))
                .stream()
                .findFirst()
                .orElse(null);

        Invoice.BillingType billingType = resolveBillingType(billingTypeStr, config);
        invoice.setBillingType(billingType);
        if (invoice.getExternalReference() == null || invoice.getExternalReference().isBlank()) {
            invoice.setExternalReference("invoice:" + invoice.getId());
        }

        AsaasGatewayService.GatewayChargeResult chargeResult = asaasGatewayService.createCharge(invoice, resident, unit);
        invoice.setExternalProvider(Invoice.Provider.ASAAS);
        invoice.setExternalCustomerId(chargeResult.customerId());
        invoice.setExternalChargeId(chargeResult.chargeId());
        invoice.setExternalInvoiceNumber(chargeResult.invoiceNumber());
        invoice.setExternalStatus(chargeResult.status());
        invoice.setBoletoUrl(chargeResult.boletoUrl());
        invoice.setPixQrCode(chargeResult.pixQrCode());
        invoice.setPixCopyPaste(chargeResult.pixCopyPaste());
        invoice.setExternalCreatedAt(Instant.now());
        invoice.setExternalUpdatedAt(Instant.now());
        invoice.setExternalLastError(null);
        invoice.setFailureReason(null);
        invoice.setFailedAt(null);
        invoice.setStatus(resolveInternalStatusFromGateway(chargeResult.status(), invoice.getDueDate()));
        invoice = invoiceRepo.save(invoice);

        recordInvoiceEvent(invoice, "EXTERNAL_CHARGE_CREATED", "gateway", "Cobrança externa criada no Asaas.", Map.of(
                "provider", Invoice.Provider.ASAAS.name(),
                "billingType", billingType.name(),
                "externalChargeId", chargeResult.chargeId(),
                "externalStatus", safeText(chargeResult.status()),
                "boletoUrl", chargeResult.boletoUrl() != null,
                "pixAvailable", chargeResult.pixCopyPaste() != null
        ));
        auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.CREATE_EXTERNAL_CHARGE,
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                "Cobrança externa criada para a invoice #" + invoice.getId() + ".",
                before,
                invoice,
                Map.of(
                        "invoiceId", invoice.getId(),
                        "externalProvider", invoice.getExternalProvider().name(),
                        "externalChargeId", invoice.getExternalChargeId(),
                        "billingType", invoice.getBillingType().name(),
                        "externalStatus", invoice.getExternalStatus()
                )
        );

        return toDetail(invoice, resolveCondoNames(Set.of(invoice.getCondominiumId())), resolveUnitLabels(Set.of(invoice.getUnitId())));
    }

    /**
     * Valida o token de webhook contra a configuração do condomínio informado.
     * Lançado pelo endpoint {@code /webhooks/asaas/{condominiumId}} antes de processar o evento.
     *
     * @throws ResponseStatusException HTTP 401 se o token for inválido
     */
    public void validateWebhookTokenForCondo(Long condominiumId, String token) {
        String tenant = TenantContext.get();
        // Quando chamado pelo Asaas (não autenticado), o TenantContext pode estar vazio.
        // Nesse caso, buscamos a config diretamente pelo condominiumId sem filtro de tenant.
        Optional<FinancialConfig> config = tenant != null && !tenant.isBlank()
                ? configRepo.findByTenantIdAndCondominiumId(tenant, condominiumId)
                : configRepo.findFirstByCondominiumId(condominiumId);
        if (config.isEmpty()) {
            // Condomínio não encontrado — rejeitar silenciosamente (sem revelar detalhes)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook inválido");
        }
        String expectedToken = config.get().getAsaasWebhookToken();
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (!expectedToken.equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook inválido");
            }
        } else {
            // Token não configurado: cair no fallback global do AsaasGatewayService
            if (!asaasGatewayService.validateWebhookToken(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook inválido");
            }
        }
    }

    private boolean isWebhookTokenAuthorized(Invoice invoice, String token) {
        if (invoice == null) {
            return asaasGatewayService.validateWebhookToken(token);
        }
        validateWebhookTokenForCondo(invoice.getCondominiumId(), token);
        return true;
    }

    @Transactional
    public Map<String, Object> handleAsaasWebhook(Map<String, Object> payload, String token) {
        Map<String, Object> payment = nestedMap(payload, "payment");
        String eventType = firstText(payload.get("event"), payload.get("type"), payload.get("eventType"));
        String externalEventId = firstText(payload.get("id"), payload.get("eventId"));
        String externalChargeId = firstText(
                payment.get("id"),
                payload.get("paymentId"),
                payload.get("externalChargeId")
        );
        String dedupKey = buildWebhookDedupKey(eventType, externalEventId, externalChargeId, payload);
        Invoice invoice = resolveInvoiceForWebhook(externalChargeId, payment);
        if (!isWebhookTokenAuthorized(invoice, token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook inválido");
        }

        Optional<InvoiceWebhookEvent> existingEvent = invoiceWebhookEventRepo.findByProviderAndExternalEventId("ASAAS", dedupKey);
        if (existingEvent.isPresent()) {
            log.info("Webhook Asaas duplicado ignorado — dedupKey={} externalChargeId={}", dedupKey, externalChargeId);
            return Map.of("status", "duplicate", "dedupKey", dedupKey);
        }

        InvoiceWebhookEvent webhookEvent = new InvoiceWebhookEvent();
        webhookEvent.setProvider("ASAAS");
        webhookEvent.setExternalEventId(dedupKey);
        webhookEvent.setExternalChargeId(externalChargeId);
        webhookEvent.setEventType(eventType != null ? eventType : "UNKNOWN");
        webhookEvent.setProcessingStatus(InvoiceWebhookEvent.ProcessingStatus.RECEIVED.name());
        webhookEvent.setPayload(writeJson(payload));
        webhookEvent.setReceivedAt(Instant.now());
        webhookEvent = invoiceWebhookEventRepo.save(webhookEvent);

        if (invoice == null) {
            log.warn("Webhook Asaas ignorado por cobrança não localizada — dedupKey={} externalChargeId={} eventType={}",
                    dedupKey, externalChargeId, eventType);
            webhookEvent.setProcessingStatus(InvoiceWebhookEvent.ProcessingStatus.IGNORED.name());
            webhookEvent.setErrorMessage("Cobrança não localizada");
            webhookEvent.setProcessedAt(Instant.now());
            invoiceWebhookEventRepo.save(webhookEvent);
            return Map.of("status", "ignored", "reason", "invoice_not_found");
        }

        boolean tenantSet = false;
        try {
            TenantContext.set(invoice.getTenantId());
            tenantSet = true;
            webhookEvent.setTenantId(invoice.getTenantId());
            webhookEvent.setCondominiumId(invoice.getCondominiumId());
            webhookEvent.setInvoiceId(invoice.getId());

            Invoice before = copyInvoice(invoice);
            Map<String, Object> details = applyWebhookToInvoice(invoice, payment, eventType, dedupKey);
            invoice.setLastWebhookAt(Instant.now());
            invoice.setExternalUpdatedAt(Instant.now());
            invoice.setExternalLastEventId(dedupKey);
            invoiceRepo.save(invoice);

            webhookEvent.setProcessingStatus(InvoiceWebhookEvent.ProcessingStatus.PROCESSED.name());
            webhookEvent.setProcessedAt(Instant.now());
            invoiceWebhookEventRepo.save(webhookEvent);

            recordInvoiceEvent(invoice, "WEBHOOK_RECEIVED", "webhook", "Webhook do Asaas processado.", Map.of(
                    "eventType", eventType,
                    "externalChargeId", externalChargeId,
                    "status", invoice.getStatus().name()
            ));
            auditService.log(
                    AuditModule.FINANCIAL,
                    AuditAction.WEBHOOK_RECEIVED,
                    "Invoice",
                    invoice.getId(),
                    invoice.getCondominiumId(),
                    "Webhook do Asaas recebido para a cobrança #" + invoice.getId() + ".",
                    before,
                    invoice,
                    details
            );

            return Map.of(
                    "status", "processed",
                    "invoiceId", invoice.getId(),
                    "internalStatus", invoice.getStatus().name(),
                    "externalChargeId", externalChargeId
            );
        } catch (RuntimeException ex) {
            webhookEvent.setProcessingStatus(InvoiceWebhookEvent.ProcessingStatus.FAILED.name());
            webhookEvent.setErrorMessage(ex.getMessage());
            webhookEvent.setProcessedAt(Instant.now());
            invoiceWebhookEventRepo.save(webhookEvent);
            throw ex;
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
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
            if (invoiceRepo.existsByTenantIdAndUnitIdAndLaunchKey(tenant, unit.getId(), launchKey)) {
                continue;
            }
            Invoice invoice = new Invoice();
            invoice.setTenantId(tenant);
            invoice.setCondominiumId(condoId);
            invoice.setUnitId(unit.getId());
            invoice.setReferenceMonth(referenceMonth);
            invoice.setChargeType(Invoice.ChargeType.CONDOMINIO);
            invoice.setTitle("Taxa condominial " + referenceMonth);
            invoice.setDescription("Cobrança mensal automática do condomínio");
            invoice.setLaunchKey(launchKey);
            invoice.setAmount(config.getMonthlyFee());
            invoice.setDueDate(dueDate);
            invoice.setBillingType(resolveBillingType(config.getDefaultBillingType(), config));
            invoice.setStatus(Invoice.Status.PENDING);
            invoice.setCreatedAt(Instant.now());
            created.add(invoice);
        }
        List<Invoice> saved = created.isEmpty() ? List.of() : invoiceRepo.saveAll(created);
        for (Invoice invoice : saved) {
            registerInvoiceCreated(invoice, "MONTHLY", "ALL_UNITS", launchKey, null, List.of(), List.of(), AmountMode.PER_UNIT, invoice.getAmount());
        }
        if (!saved.isEmpty()) {
            String batchAuditEntityId = buildBatchAuditEntityId(launchKey);
            auditService.log(
                    AuditModule.FINANCIAL,
                    AuditAction.GENERATE_CHARGE_BATCH,
                    "Invoice",
                    batchAuditEntityId,
                    condoId,
                    "Cobranças mensais automáticas geradas para " + referenceMonth + ".",
                    null,
                    null,
                    batchDetails(saved.size(), 0, launchKey, "MONTHLY", "ALL_UNITS", Invoice.ChargeType.CONDOMINIO.name(), referenceMonth, AmountMode.PER_UNIT.name())
            );
        }
        return saved;
    }

    @Transactional
    public Invoice registerPayment(Long id, BigDecimal paidAmount, String paymentMethodStr, String notes) {
        Invoice invoice = getInvoiceEntity(id);
        Invoice before = copyInvoice(invoice);
        if (invoice.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("Fatura já está paga");
        }
        if (invoice.getStatus() == Invoice.Status.CANCELLED || invoice.getStatus() == Invoice.Status.WAIVED) {
            throw new BusinessException("Não é possível registrar pagamento de fatura cancelada/dispensada");
        }

        Invoice.PaymentMethod method = parsePaymentMethod(paymentMethodStr);
        BigDecimal alreadyPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal amountToRegister = paidAmount != null
                ? paidAmount.setScale(2, RoundingMode.HALF_UP)
                : invoice.getAmount().subtract(alreadyPaid).setScale(2, RoundingMode.HALF_UP);
        if (amountToRegister.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor pago deve ser maior que zero");
        }
        BigDecimal totalPaid = alreadyPaid.add(amountToRegister);
        if (totalPaid.compareTo(invoice.getAmount()) > 0) {
            throw new BusinessException("Valor pago não pode ser maior que o valor da cobrança");
        }

        invoice.setPaidAt(Instant.now());
        invoice.setPaymentReceivedAt(Instant.now());
        invoice.setPaidAmount(totalPaid);
        invoice.setPaymentMethod(method);
        invoice.setPaymentNotes(notes);
        invoice.setExternalStatus(invoice.getExternalStatus() == null ? "MANUAL_CONFIRMED" : invoice.getExternalStatus());
        invoice.setStatus(totalPaid.compareTo(invoice.getAmount()) < 0 ? Invoice.Status.PARTIALLY_PAID : Invoice.Status.PAID);
        invoice.setRegisteredBy(UserContext.userId());
        invoice = invoiceRepo.save(invoice);

        recordInvoiceEvent(invoice, "PAYMENT_REGISTERED", "manual", "Pagamento registrado manualmente.", Map.of(
                "paymentMethod", method.name(),
                "amountRegistered", amountToRegister,
                "paidAmount", invoice.getPaidAmount()
        ));
        if (invoice.getStatus() == Invoice.Status.PAID) {
            notifyPaymentConfirmed(invoice, "Pagamento confirmado manualmente.");
        }
        auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.REGISTER_PAYMENT,
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                "Pagamento registrado para a cobrança " + invoice.getReferenceMonth() + " da unidade " + invoice.getUnitId() + ".",
                before,
                invoice,
                paymentDetails(invoice)
        );
        return invoice;
    }

    @Transactional
    public FinancialSummaryResponse summary(Long condoIdParam) {
        return summary(condoIdParam, null, null);
    }

    @Transactional
    public FinancialSummaryResponse summary(Long condoIdParam, String referenceMonthFrom, String referenceMonthTo) {
        refreshOverdueInvoices();
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        Long unitId = isMorador() ? UserContext.unitId() : null;
        String normalizedReferenceMonthFrom = normalizeReferenceMonthBoundary(referenceMonthFrom, "inicial");
        String normalizedReferenceMonthTo = normalizeReferenceMonthBoundary(referenceMonthTo, "final");

        if (!UserContext.isSuperuser() && condoId == null) {
            return new FinancialSummaryResponse(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
        }

        UserContext.Data ctx = UserContext.get();
        Long scopedCondominiumId = UserContext.isSuperuser()
                ? condoId
                : (ctx != null ? ctx.condominiumId() : null);

        Object[] row = normalizeSummaryRow(invoiceRepo.summaryAdvanced(
                tenant,
                scopedCondominiumId,
                unitId,
                normalizedReferenceMonthFrom,
                normalizedReferenceMonthTo
        ));

        long totalInvoices = asLong(row, 0);
        BigDecimal totalAmount = asBigDecimal(row, 1);
        BigDecimal paidAmount = asBigDecimal(row, 2);
        BigDecimal pendingAmount = asBigDecimal(row, 3);
        BigDecimal overdueAmount = asBigDecimal(row, 4);
        double delinquencyPct = totalAmount.signum() > 0
                ? overdueAmount.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new FinancialSummaryResponse(
                totalInvoices,
                totalAmount,
                paidAmount,
                pendingAmount,
                overdueAmount,
                delinquencyPct,
                mapStatusBreakdown(invoiceRepo.summaryByStatus(
                        tenant,
                        scopedCondominiumId,
                        unitId,
                        normalizedReferenceMonthFrom,
                        normalizedReferenceMonthTo
                )),
                mapBlockDelinquency(invoiceRepo.delinquencyByBlock(
                        tenant,
                        scopedCondominiumId,
                        unitId,
                        normalizedReferenceMonthFrom,
                        normalizedReferenceMonthTo
                )),
                mapPeriodSummary(invoiceRepo.summaryByReferenceMonth(
                        tenant,
                        scopedCondominiumId,
                        unitId,
                        normalizedReferenceMonthFrom,
                        normalizedReferenceMonthTo
                ))
        );
    }

    @Transactional
    public InvoiceDetailResponse cancelInvoice(Long id, String reason) {
        Invoice invoice = getInvoiceEntity(id);
        ensureManualStatusChangeAllowed(invoice, "cancelar");
        Invoice before = copyInvoice(invoice);

        // Se há cobrança externa ativa, cancelar no Asaas antes de salvar
        if (invoice.getExternalChargeId() != null && !invoice.getExternalChargeId().isBlank()
                && invoice.getExternalProvider() == Invoice.Provider.ASAAS) {
            try {
                asaasGatewayService.cancelCharge(invoice.getExternalChargeId(), invoice.getCondominiumId());
                invoice.setExternalStatus("CANCELLED");
            } catch (Exception ex) {
                // Loga mas não bloqueia o cancelamento interno
                log.warn("Falha ao cancelar cobrança no Asaas durante cancelamento manual — invoiceId={} chargeId={}: {}",
                        invoice.getId(), invoice.getExternalChargeId(), ex.getMessage());
            }
        }
        invoice.setStatus(Invoice.Status.CANCELLED);
        invoice.setCancelledAt(Instant.now());
        invoice.setFailureReason(reason);
        invoice.setExternalStatus(invoice.getExternalStatus() == null ? "MANUAL_CANCELLED" : invoice.getExternalStatus());
        invoice = invoiceRepo.save(invoice);

        Map<String, Object> reasonDetails = manualReasonDetails(reason);
        recordInvoiceEvent(invoice, "INVOICE_CANCELLED", "manual", "Cobrança cancelada manualmente.", reasonDetails);
        auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.CANCEL,
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                "Cobrança cancelada manualmente.",
                before,
                invoice,
                reasonDetails
        );
        return toDetail(invoice, resolveCondoNames(Set.of(invoice.getCondominiumId())), resolveUnitLabels(Set.of(invoice.getUnitId())));
    }

    @Transactional
    public InvoiceDetailResponse waiveInvoice(Long id, String reason) {
        Invoice invoice = getInvoiceEntity(id);
        ensureManualStatusChangeAllowed(invoice, "dispensar");
        Invoice before = copyInvoice(invoice);

        invoice.setStatus(Invoice.Status.WAIVED);
        invoice.setFailureReason(reason);
        invoice.setExternalStatus(invoice.getExternalStatus() == null ? "MANUAL_WAIVED" : invoice.getExternalStatus());
        invoice = invoiceRepo.save(invoice);

        Map<String, Object> reasonDetails = manualReasonDetails(reason);
        recordInvoiceEvent(invoice, "INVOICE_WAIVED", "manual", "Cobrança dispensada manualmente.", reasonDetails);
        auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.STATUS_CHANGE,
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                "Cobrança dispensada manualmente.",
                before,
                invoice,
                withStatus(reasonDetails, Invoice.Status.WAIVED.name())
        );
        return toDetail(invoice, resolveCondoNames(Set.of(invoice.getCondominiumId())), resolveUnitLabels(Set.of(invoice.getUnitId())));
    }

    private void registerInvoiceCreated(Invoice invoice,
                                        String criterion,
                                        String appliesTo,
                                        String launchKey,
                                        Long targetUnitId,
                                        List<Long> targetUnitIds,
                                        List<String> targetBlocks,
                                        AmountMode amountMode,
                                        BigDecimal originalAmount) {
        recordInvoiceEvent(invoice, "INVOICE_CREATED", "system", "Cobrança criada.", Map.of(
                "criterion", criterion,
                "appliesTo", appliesTo,
                "amountMode", amountMode.name(),
                "originalAmount", originalAmount
        ));
        financialNotificationService.logInvoiceNotification(
                invoice,
                FinancialNotification.Type.CHARGE_CREATED,
                "Sua cobrança " + invoice.getTitle() + " foi gerada para " + resolveSingleUnitLabel(invoice.getUnitId()) + ".",
                Map.of(
                        "referenceMonth", invoice.getReferenceMonth(),
                        "dueDate", invoice.getDueDate(),
                        "amount", invoice.getAmount()
                )
        );
        invoice.setLastNotificationAt(Instant.now());
        invoice.setLastNotificationType(FinancialNotification.Type.CHARGE_CREATED.name());
        invoiceRepo.save(invoice);
        auditService.log(
                AuditModule.FINANCIAL,
                AuditAction.CREATE_INVOICE,
                "Invoice",
                invoice.getId(),
                invoice.getCondominiumId(),
                "Cobrança lançada para a unidade " + invoice.getUnitId() + ".",
                null,
                invoice,
                invoiceCreationDetails(invoice, criterion, appliesTo, launchKey, targetUnitId, targetUnitIds, targetBlocks, amountMode.name())
        );
    }

    private void notifyPaymentConfirmed(Invoice invoice, String message) {
        if (!financialNotificationService.hasNotification(invoice.getId(), FinancialNotification.Type.PAYMENT_CONFIRMED)) {
            financialNotificationService.logInvoiceNotification(
                    invoice,
                    FinancialNotification.Type.PAYMENT_CONFIRMED,
                    message,
                    Map.of(
                            "paidAmount", invoice.getPaidAmount(),
                            "paymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null
                    )
            );
            invoice.setLastNotificationAt(Instant.now());
            invoice.setLastNotificationType(FinancialNotification.Type.PAYMENT_CONFIRMED.name());
            invoiceRepo.save(invoice);
        }
    }

    private Map<String, Object> applyWebhookToInvoice(Invoice invoice,
                                                      Map<String, Object> payment,
                                                      String eventType,
                                                      String dedupKey) {
        Invoice.Status beforeStatus = invoice.getStatus();
        String externalStatus = firstText(payment.get("status"), eventType);
        invoice.setExternalProvider(Invoice.Provider.ASAAS);
        invoice.setExternalStatus(externalStatus);
        invoice.setExternalChargeId(firstText(payment.get("id"), invoice.getExternalChargeId()));
        invoice.setExternalCustomerId(firstText(payment.get("customer"), invoice.getExternalCustomerId()));
        invoice.setExternalInvoiceNumber(firstText(payment.get("invoiceNumber"), invoice.getExternalInvoiceNumber()));
        invoice.setBoletoUrl(firstText(payment.get("bankSlipUrl"), invoice.getBoletoUrl()));
        invoice.setInvoiceUrl(firstText(payment.get("invoiceUrl"), invoice.getInvoiceUrl()));

        String eventCode = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
        if (eventCode.contains("RECEIVED") || eventCode.contains("CONFIRMED")) {
            invoice.setPaidAmount(asBigDecimal(payment.get("value"), invoice.getAmount()));
            invoice.setPaidAt(parseInstantOrNow(payment.get("clientPaymentDate"), payment.get("paymentDate"), payment.get("confirmedDate")));
            invoice.setPaymentReceivedAt(invoice.getPaidAt());
            invoice.setPaymentMethod(resolvePaymentMethod(payment.get("billingType")));
            invoice.setStatus(invoice.getPaidAmount().compareTo(invoice.getAmount()) < 0 ? Invoice.Status.PARTIALLY_PAID : Invoice.Status.PAID);
            recordInvoiceEvent(invoice, "PAYMENT_CONFIRMED", "webhook", "Pagamento confirmado via webhook.", Map.of(
                    "eventType", eventType,
                    "paidAmount", invoice.getPaidAmount(),
                    "paymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null
            ));
            notifyPaymentConfirmed(invoice, "Recebemos o pagamento da sua cobrança.");
            auditService.log(
                    AuditModule.FINANCIAL,
                    AuditAction.PAYMENT_CONFIRMED,
                    "Invoice",
                    invoice.getId(),
                    invoice.getCondominiumId(),
                    "Pagamento confirmado via webhook para a cobrança #" + invoice.getId() + ".",
                    null,
                    invoice,
                    Map.of("eventType", eventType, "externalChargeId", invoice.getExternalChargeId(), "paidAmount", invoice.getPaidAmount())
            );
        } else if (eventCode.contains("OVERDUE")) {
            invoice.setStatus(invoice.getStatus() == Invoice.Status.PAID ? Invoice.Status.PAID : Invoice.Status.OVERDUE);
            if (!financialNotificationService.hasNotification(invoice.getId(), FinancialNotification.Type.INVOICE_OVERDUE)) {
                financialNotificationService.logInvoiceNotification(
                        invoice,
                        FinancialNotification.Type.INVOICE_OVERDUE,
                        "Sua cobrança está vencida.",
                        Map.of("eventType", eventType, "dueDate", invoice.getDueDate())
                );
                invoice.setLastNotificationAt(Instant.now());
                invoice.setLastNotificationType(FinancialNotification.Type.INVOICE_OVERDUE.name());
            }
        } else if (eventCode.contains("DELETED") || eventCode.contains("CANCELLED") || eventCode.contains("REFUNDED")) {
            invoice.setStatus(Invoice.Status.CANCELLED);
            invoice.setCancelledAt(Instant.now());
        } else if (eventCode.contains("FAILED") || eventCode.contains("ERROR")) {
            invoice.setStatus(Invoice.Status.FAILED);
            invoice.setFailedAt(Instant.now());
            invoice.setFailureReason("Webhook de falha: " + eventType);
            recordInvoiceEvent(invoice, "PAYMENT_FAILED", "webhook", "Falha operacional reportada pelo gateway.", Map.of(
                    "eventType", eventType,
                    "externalChargeId", invoice.getExternalChargeId()
            ));
            auditService.log(
                    AuditModule.FINANCIAL,
                    AuditAction.PAYMENT_FAILED,
                    "Invoice",
                    invoice.getId(),
                    invoice.getCondominiumId(),
                    "Falha operacional reportada pelo gateway para a cobrança #" + invoice.getId() + ".",
                    null,
                    invoice,
                    Map.of("eventType", eventType, "externalChargeId", invoice.getExternalChargeId())
            );
        } else {
            invoice.setStatus(resolveInternalStatusFromGateway(externalStatus, invoice.getDueDate()));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invoiceId", invoice.getId());
        details.put("eventType", eventType);
        details.put("externalChargeId", invoice.getExternalChargeId());
        details.put("externalStatus", externalStatus);
        details.put("previousStatus", beforeStatus.name());
        details.put("currentStatus", invoice.getStatus().name());
        details.put("dedupKey", dedupKey);
        return details;
    }

    private Invoice resolveInvoiceForWebhook(String externalChargeId, Map<String, Object> payment) {
        if (externalChargeId != null && !externalChargeId.isBlank()) {
            Optional<Invoice> byChargeId = invoiceRepo.findByExternalProviderAndExternalChargeId(Invoice.Provider.ASAAS, externalChargeId);
            if (byChargeId.isPresent()) {
                return byChargeId.get();
            }
        }
        String externalReference = firstText(payment.get("externalReference"));
        if (externalReference != null && !externalReference.isBlank() && externalReference.startsWith("invoice:")) {
            try {
                long invoiceId = Long.parseLong(externalReference.substring("invoice:".length()));
                return invoiceRepo.findById(invoiceId).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Invoice getInvoiceEntity(Long id) {
        String tenant = TenantContext.get();
        Invoice invoice = invoiceRepo.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura", "id", id));
        enforceSameCondominium(invoice.getCondominiumId());
        if (isMorador() && !Objects.equals(invoice.getUnitId(), UserContext.unitId())) {
            throw new ResourceNotFoundException("Fatura", "id", id);
        }
        return invoice;
    }

    private InvoiceListItemResponse toListItem(Invoice invoice,
                                               Map<Long, String> condoNames,
                                               Map<Long, String> unitLabels,
                                               Map<String, String> residentNames) {
        return new InvoiceListItemResponse(
                invoice.getId(),
                invoice.getCondominiumId(),
                condoNames.get(invoice.getCondominiumId()),
                invoice.getUnitId(),
                unitLabels.get(invoice.getUnitId()),
                residentNames.get(invoiceResidentKey(invoice)),
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
                invoice.getExternalProvider() != null ? invoice.getExternalProvider().name() : null,
                invoice.getExternalChargeId(),
                invoice.getExternalStatus(),
                invoice.getBillingType() != null ? invoice.getBillingType().name() : null,
                invoice.getBoletoUrl(),
                invoice.getInvoiceUrl(),
                invoice.getPixCopyPaste(),
                invoice.getPixQrCode(),
                invoice.getPixExpiresAt(),
                invoice.getLastWebhookAt(),
                invoice.getLastNotificationAt(),
                invoice.getLastNotificationType()
        );
    }

    private InvoiceDetailResponse toDetail(Invoice invoice,
                                           Map<Long, String> condoNames,
                                           Map<Long, String> unitLabels) {
        List<InvoiceEventResponse> events = invoiceEventRepo.findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(TenantContext.get(), invoice.getId()).stream()
                .map(this::toEventResponse)
                .toList();
        List<InvoiceNotificationResponse> notifications = financialNotificationService.listInvoiceNotifications(invoice.getId()).stream()
                .map(this::toNotificationResponse)
                .toList();
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
                invoice.getExternalProvider() != null ? invoice.getExternalProvider().name() : null,
                invoice.getExternalChargeId(),
                invoice.getExternalInvoiceNumber(),
                invoice.getExternalStatus(),
                invoice.getBillingType() != null ? invoice.getBillingType().name() : null,
                invoice.getBoletoUrl(),
                invoice.getInvoiceUrl(),
                invoice.getPixQrCode(),
                invoice.getPixCopyPaste(),
                invoice.getExternalCreatedAt(),
                invoice.getLastWebhookAt(),
                invoice.getApportionmentMode() != null ? invoice.getApportionmentMode().name() : null,
                invoice.getApportionmentGroup(),
                invoice.getRegisteredBy(),
                invoice.getCreatedAt(),
                events,
                notifications
        );
    }

    private InvoiceEventResponse toEventResponse(InvoiceEvent event) {
        return new InvoiceEventResponse(
                event.getId(),
                event.getEventType(),
                extractMetadataText(event.getMetadata(), "source"),
                event.getTitle(),
                event.getCreatedAt(),
                parseMetadata(event.getMetadata())
        );
    }

    private InvoiceNotificationResponse toNotificationResponse(FinancialNotification notification) {
        return new InvoiceNotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getRecipientName(),
                notification.getRecipientEmail(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }

    private void recordInvoiceEvent(Invoice invoice, String type, String source, String description, Map<String, Object> metadata) {
        InvoiceEvent event = new InvoiceEvent();
        event.setTenantId(invoice.getTenantId());
        event.setCondominiumId(invoice.getCondominiumId());
        event.setInvoiceId(invoice.getId());
        event.setEventType(type);
        event.setTitle(description);
        event.setMessage(description);
        Map<String, Object> payload = new LinkedHashMap<>(metadata);
        payload.put("source", source);
        event.setMetadata(writeJson(payload));
        event.setCreatedAt(Instant.now());
        invoiceEventRepo.save(event);
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

    private Map<String, String> resolveResidentNames(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return Map.of();
        }
        String tenant = TenantContext.get();
        Map<String, String> namesByInvoiceScope = new LinkedHashMap<>();
        Map<Long, Set<Long>> unitIdsByCondominium = invoices.stream()
                .filter(invoice -> invoice.getCondominiumId() != null && invoice.getUnitId() != null)
                .collect(Collectors.groupingBy(
                        Invoice::getCondominiumId,
                        Collectors.mapping(Invoice::getUnitId, Collectors.toSet())
                ));

        unitIdsByCondominium.forEach((condominiumId, unitIds) ->
                residentRepo.findByTenantIdAndCondominiumIdAndUnitIdIn(tenant, condominiumId, new ArrayList<>(unitIds)).stream()
                        .sorted(Comparator.comparing(Resident::getId, Comparator.nullsLast(Long::compareTo)))
                        .forEach(resident -> namesByInvoiceScope.putIfAbsent(
                                invoiceResidentKey(condominiumId, resident.getUnitId()),
                                resident.getName()
                        ))
        );
        return namesByInvoiceScope;
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

    private String normalizeInvoiceSort(String property) {
        if (property == null || property.isBlank()) {
            return "dueDate";
        }
        return switch (property) {
            case "createdAt", "amount", "status", "title", "referenceMonth", "dueDate" -> property;
            default -> "dueDate";
        };
    }

    private Invoice.Status parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return Invoice.Status.valueOf(statusStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Status inválido: " + statusStr);
        }
    }

    private Invoice.ChargeType parseChargeType(String chargeTypeStr) {
        try {
            return Invoice.ChargeType.valueOf((chargeTypeStr == null || chargeTypeStr.isBlank() ? "CONDOMINIO" : chargeTypeStr).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de cobrança inválido: " + chargeTypeStr);
        }
    }

    private Invoice.PaymentMethod parsePaymentMethod(String paymentMethodStr) {
        try {
            return Invoice.PaymentMethod.valueOf((paymentMethodStr == null || paymentMethodStr.isBlank() ? "OTHER" : paymentMethodStr).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Invoice.PaymentMethod.OTHER;
        }
    }

    private Invoice.BillingType resolveBillingType(String billingTypeStr, FinancialConfig config) {
        String source = billingTypeStr;
        if ((source == null || source.isBlank()) && config != null) {
            source = config.getDefaultBillingType();
        }
        String normalized = (source == null || source.isBlank() ? "BOLETO" : source).trim().toUpperCase(Locale.ROOT);
        try {
            return Invoice.BillingType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de cobrança externa inválido: " + billingTypeStr);
        }
    }

    private AmountMode parseAmountMode(String amountModeStr) {
        String normalized = (amountModeStr == null || amountModeStr.isBlank() ? "PER_UNIT" : amountModeStr).trim().toUpperCase(Locale.ROOT);
        try {
            return AmountMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Modo de valor inválido: " + amountModeStr);
        }
    }

    private String normalizeCriterion(String criterion) {
        String normalized = criterion == null || criterion.isBlank() ? "MONTHLY" : criterion.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("MONTHLY") && !normalized.equals("ONE_TIME")) {
            throw new BusinessException("Critério de cobrança inválido: " + criterion);
        }
        return normalized;
    }

    private String normalizeAppliesTo(String appliesTo) {
        String normalized = appliesTo == null || appliesTo.isBlank() ? "ALL_UNITS" : appliesTo.trim().toUpperCase(Locale.ROOT);
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
                + ":" + title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private String buildBatchAuditEntityId(String launchKey) {
        String source = launchKey == null || launchKey.isBlank() ? "batch:unknown" : launchKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return "batch:" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao gerar identificador curto para auditoria financeira", e);
        }
    }

    private boolean isDuplicateLaunchConflict(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause != null ? cause.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("uq_invoice_unit_launch_key")
                || normalized.contains("uq_invoice_unit_month")
                || (normalized.contains("duplicate key value") && normalized.contains("invoice(unit_id, launch_key)"));
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
                        .map(value -> value.toUpperCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                List<Unit> matchedUnits = condoUnits.stream()
                        .filter(unit -> unit.getBlock() != null && normalizedBlocks.contains(unit.getBlock().trim().toUpperCase(Locale.ROOT)))
                        .toList();
                if (matchedUnits.isEmpty()) {
                    throw new BusinessException("Nenhuma unidade encontrada para os blocos selecionados");
                }
                yield matchedUnits;
            }
            default -> throw new BusinessException("Escopo de cobrança inválido: " + appliesTo);
        };
    }

    private void refreshOverdueInvoices() {
        List<Invoice> overdueInvoices = invoiceRepo.findOverdue(LocalDate.now());
        if (overdueInvoices.isEmpty()) {
            return;
        }
        overdueInvoices.forEach(invoice -> invoice.setStatus(Invoice.Status.OVERDUE));
        invoiceRepo.saveAll(overdueInvoices);
    }

    private void ensureManualStatusChangeAllowed(Invoice invoice, String actionLabel) {
        if (invoice.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("Não é possível " + actionLabel + " uma cobrança já paga");
        }
        if (invoice.getStatus() == Invoice.Status.CANCELLED) {
            throw new BusinessException("A cobrança já está cancelada");
        }
        if (invoice.getStatus() == Invoice.Status.WAIVED) {
            throw new BusinessException("A cobrança já está dispensada");
        }
        if (invoice.getStatus() == Invoice.Status.PARTIALLY_PAID) {
            throw new BusinessException("Não é possível " + actionLabel + " uma cobrança com pagamento parcial registrado");
        }
    }

    private Map<String, Object> manualReasonDetails(String reason) {
        if (reason == null || reason.isBlank()) {
            return Map.of();
        }
        return Map.of("reason", reason.trim());
    }

    private Map<String, Object> withStatus(Map<String, Object> baseDetails, String status) {
        Map<String, Object> details = new LinkedHashMap<>(baseDetails);
        details.put("status", status);
        return details;
    }

    private List<BigDecimal> apportionAmounts(AmountMode amountMode, BigDecimal amount, int count) {
        if (count <= 0) {
            return List.of();
        }
        if (amountMode == AmountMode.PER_UNIT) {
            return java.util.Collections.nCopies(count, amount.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal total = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal countDecimal = BigDecimal.valueOf(count);
        BigDecimal base = total.divide(countDecimal, 2, RoundingMode.DOWN);
        BigDecimal consumed = base.multiply(countDecimal);
        BigDecimal remainder = total.subtract(consumed);
        List<BigDecimal> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BigDecimal value = base;
            if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                value = value.add(new BigDecimal("0.01"));
                remainder = remainder.subtract(new BigDecimal("0.01"));
            }
            values.add(value);
        }
        return values;
    }

    private Long resolveScopedUnitId(Long unitIdParam, Long condoId) {
        if (isMorador()) {
            return UserContext.unitId();
        }
        if (canAccessMyInvoicesPortal() && Objects.equals(unitIdParam, UserContext.unitId())) {
            return UserContext.unitId();
        }
        if (unitIdParam == null) {
            return null;
        }
        Unit unit = unitRepo.findByTenantIdAndId(TenantContext.get(), unitIdParam)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade", "id", unitIdParam));
        if (condoId != null && !Objects.equals(unit.getCondominiumId(), condoId)) {
            throw new BusinessException("A unidade informada não pertence ao condomínio selecionado");
        }
        return unitIdParam;
    }

    private Long resolveScopedResidentId(Long residentIdParam, Long condoId, Long unitId) {
        if (residentIdParam == null) {
            return null;
        }
        Resident resident = residentRepo.findByTenantIdAndId(TenantContext.get(), residentIdParam)
                .orElseThrow(() -> new ResourceNotFoundException("Morador", "id", residentIdParam));
        if (condoId != null && !Objects.equals(resident.getCondominiumId(), condoId)) {
            throw new BusinessException("O morador informado não pertence ao condomínio selecionado");
        }
        if (unitId != null && !Objects.equals(resident.getUnitId(), unitId)) {
            throw new BusinessException("O morador informado não pertence à unidade filtrada");
        }
        if (isMorador() && !Objects.equals(resident.getUnitId(), UserContext.unitId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        return resident.getId();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeReferenceMonthBoundary(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim(), YEAR_MONTH_FORMAT).format(YEAR_MONTH_FORMAT);
        } catch (Exception ex) {
            throw new BusinessException("Competência " + label + " inválida. Use o formato yyyy-MM");
        }
    }

    private Invoice.ChargeType parseChargeTypeOrNull(String chargeTypeStr) {
        if (chargeTypeStr == null || chargeTypeStr.isBlank()) {
            return null;
        }
        return parseChargeType(chargeTypeStr);
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
        return asBigDecimal(value, BigDecimal.ZERO);
    }

    private BigDecimal asBigDecimal(Object value, BigDecimal defaultValue) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        }
        return defaultValue;
    }

    private List<FinancialStatusBreakdownResponse> mapStatusBreakdown(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .map(row -> new FinancialStatusBreakdownResponse(
                        row[0] != null ? row[0].toString() : "UNKNOWN",
                        asLong(row, 1),
                        asBigDecimal(row, 2)
                ))
                .toList();
    }

    private List<FinancialBlockDelinquencyResponse> mapBlockDelinquency(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .map(row -> new FinancialBlockDelinquencyResponse(
                        row[0] != null ? row[0].toString() : "Sem bloco",
                        asLong(row, 1),
                        asBigDecimal(row, 2),
                        asBigDecimal(row, 3)
                ))
                .toList();
    }

    private List<FinancialPeriodSummaryResponse> mapPeriodSummary(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .map(row -> new FinancialPeriodSummaryResponse(
                        row[0] != null ? row[0].toString() : null,
                        asLong(row, 1),
                        asBigDecimal(row, 2),
                        asBigDecimal(row, 3),
                        asBigDecimal(row, 4)
                ))
                .toList();
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private boolean canAccessMyInvoicesPortal() {
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) {
            return false;
        }
        return switch (ctx.role().toUpperCase(Locale.ROOT)) {
            case "MORADOR", "SINDICO", "ZELADOR" -> true;
            default -> false;
        };
    }

    private String invoiceResidentKey(Invoice invoice) {
        return invoiceResidentKey(invoice.getCondominiumId(), invoice.getUnitId());
    }

    private String invoiceResidentKey(Long condominiumId, Long unitId) {
        return condominiumId + ":" + unitId;
    }

    private void enforceSameCondominium(Long condoId) {
        Long effective = UserContext.resolveCondominiumId(condoId);
        if (effective != null && !effective.equals(condoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private FinancialConfig loadConfig(Long condoId) {
        if (condoId == null) {
            return null;
        }
        return configRepo.findByTenantIdAndCondominiumId(TenantContext.get(), condoId).orElse(null);
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
        copy.setDefaultBillingType(source.getDefaultBillingType());
        copy.setNotificationEmailEnabled(source.isNotificationEmailEnabled());
        copy.setNotificationWhatsappEnabled(source.isNotificationWhatsappEnabled());
        copy.setAsaasEnabled(source.isAsaasEnabled());
        copy.setAsaasWebhookToken(source.getAsaasWebhookToken());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private FinancialConfig sanitizeConfigForAudit(FinancialConfig source) {
        FinancialConfig copy = copyConfig(source);
        copy.setAsaasWebhookToken(maskSecret(copy.getAsaasWebhookToken()));
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
        copy.setExternalProvider(source.getExternalProvider());
        copy.setExternalChargeId(source.getExternalChargeId());
        copy.setExternalInvoiceNumber(source.getExternalInvoiceNumber());
        copy.setExternalCustomerId(source.getExternalCustomerId());
        copy.setExternalReference(source.getExternalReference());
        copy.setExternalStatus(source.getExternalStatus());
        copy.setBillingType(source.getBillingType());
        copy.setPixQrCode(source.getPixQrCode());
        copy.setPixCopyPaste(source.getPixCopyPaste());
        copy.setBoletoUrl(source.getBoletoUrl());
        copy.setInvoiceUrl(source.getInvoiceUrl());
        copy.setPixExpiresAt(source.getPixExpiresAt());
        copy.setLastWebhookAt(source.getLastWebhookAt());
        copy.setLastNotificationAt(source.getLastNotificationAt());
        copy.setLastNotificationType(source.getLastNotificationType());
        copy.setExternalCreatedAt(source.getExternalCreatedAt());
        copy.setExternalUpdatedAt(source.getExternalUpdatedAt());
        copy.setExternalLastError(source.getExternalLastError());
        copy.setCancelledAt(source.getCancelledAt());
        copy.setFailedAt(source.getFailedAt());
        copy.setApportionmentGroup(source.getApportionmentGroup());
        copy.setApportionmentMode(source.getApportionmentMode());
        copy.setExternalLastEventId(source.getExternalLastEventId());
        copy.setPaymentReceivedAt(source.getPaymentReceivedAt());
        copy.setFailureReason(source.getFailureReason());
        copy.setStatus(source.getStatus());
        copy.setRegisteredBy(source.getRegisteredBy());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private Map<String, Object> financialConfigDetails(FinancialConfig config) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("monthlyFee", config.getMonthlyFee());
        details.put("dueDay", config.getDueDay());
        details.put("pixKeyType", config.getPixKeyType());
        details.put("defaultBillingType", config.getDefaultBillingType());
        details.put("notificationEmailEnabled", config.isNotificationEmailEnabled());
        details.put("notificationWhatsappEnabled", config.isNotificationWhatsappEnabled());
        details.put("asaasEnabled", config.isAsaasEnabled());
        details.put("asaasWebhookTokenConfigured", config.getAsaasWebhookToken() != null && !config.getAsaasWebhookToken().isBlank());
        return details;
    }

    private Map<String, Object> invoiceCreationDetails(Invoice invoice,
                                                       String criterion,
                                                       String appliesTo,
                                                       String launchKey,
                                                       Long targetUnitId,
                                                       List<Long> targetUnitIds,
                                                       List<String> targetBlocks,
                                                       String amountMode) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invoiceId", invoice.getId());
        details.put("invoiceTitle", invoice.getTitle());
        details.put("unitId", invoice.getUnitId());
        details.put("unitLabel", resolveSingleUnitLabel(invoice.getUnitId()));
        details.put("criterion", criterion);
        details.put("appliesTo", appliesTo);
        details.put("launchKey", launchKey);
        details.put("chargeType", invoice.getChargeType().name());
        details.put("referenceMonth", invoice.getReferenceMonth());
        details.put("targetUnitId", targetUnitId);
        details.put("targetUnitIds", targetUnitIds == null ? List.of() : targetUnitIds);
        details.put("targetBlocks", targetBlocks == null ? List.of() : targetBlocks);
        details.put("amountMode", amountMode);
        details.put("billingType", invoice.getBillingType() != null ? invoice.getBillingType().name() : null);
        return details;
    }

    private Map<String, Object> batchDetails(int createdCount,
                                             int skippedCount,
                                             String launchKey,
                                             String criterion,
                                             String appliesTo,
                                             String chargeType,
                                             String referenceMonth,
                                             String amountMode) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("createdCount", createdCount);
        details.put("skippedCount", skippedCount);
        details.put("launchKey", launchKey);
        details.put("criterion", criterion);
        details.put("appliesTo", appliesTo);
        details.put("chargeType", chargeType);
        details.put("referenceMonth", referenceMonth);
        details.put("amountMode", amountMode);
        return details;
    }

    private Map<String, Object> apportionmentDetails(List<Invoice> invoices,
                                                     BigDecimal originalAmount,
                                                     AmountMode amountMode,
                                                     String appliesTo) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invoiceIds", invoices.stream().map(Invoice::getId).toList());
        details.put("unitIds", invoices.stream().map(Invoice::getUnitId).toList());
        details.put("originalAmount", originalAmount);
        details.put("amountMode", amountMode.name());
        details.put("appliesTo", appliesTo);
        details.put("apportionmentGroup", invoices.isEmpty() ? null : invoices.get(0).getApportionmentGroup());
        return details;
    }

    private Map<String, Object> paymentDetails(Invoice invoice) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invoiceId", invoice.getId());
        details.put("invoiceTitle", invoice.getTitle());
        details.put("unitId", invoice.getUnitId());
        details.put("unitLabel", resolveSingleUnitLabel(invoice.getUnitId()));
        details.put("referenceMonth", invoice.getReferenceMonth());
        details.put("paymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null);
        details.put("paidAmount", invoice.getPaidAmount());
        return details;
    }

    private String resolveSingleUnitLabel(Long unitId) {
        if (unitId == null) {
            return null;
        }
        return unitRepo.findByTenantIdAndId(TenantContext.get(), unitId)
                .map(this::formatUnitLabel)
                .orElse("Unidade #" + unitId);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar metadados financeiros", e);
        }
    }

    private Map<String, Object> parseMetadata(String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawMetadata, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of("raw", rawMetadata);
        }
    }

    private String extractMetadataText(String rawMetadata, String key) {
        Object value = parseMetadata(rawMetadata).get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String buildWebhookDedupKey(String eventType,
                                        String externalEventId,
                                        String externalChargeId,
                                        Map<String, Object> payload) {
        if (externalEventId != null && !externalEventId.isBlank()) {
            return externalEventId;
        }
        return (eventType == null ? "UNKNOWN" : eventType)
                + ":"
                + (externalChargeId == null ? "NA" : externalChargeId)
                + ":"
                + Integer.toHexString(writeJson(payload).hashCode());
    }

    private Invoice.Status resolveInternalStatusFromGateway(String externalStatus, LocalDate dueDate) {
        String normalized = externalStatus == null ? "" : externalStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("RECEIVED") || normalized.contains("CONFIRMED")) {
            return Invoice.Status.PAID;
        }
        if (normalized.contains("OVERDUE")) {
            return Invoice.Status.OVERDUE;
        }
        if (normalized.contains("DELETED") || normalized.contains("CANCELLED")) {
            return Invoice.Status.CANCELLED;
        }
        if (normalized.contains("ERROR") || normalized.contains("FAILED")) {
            return Invoice.Status.FAILED;
        }
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            return Invoice.Status.OVERDUE;
        }
        return Invoice.Status.AWAITING_PAYMENT;
    }

    private Invoice.PaymentMethod resolvePaymentMethod(Object billingType) {
        String normalized = safeText(firstText(billingType)).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PIX" -> Invoice.PaymentMethod.PIX;
            case "BOLETO", "UNDEFINED" -> Invoice.PaymentMethod.BOLETO;
            default -> Invoice.PaymentMethod.OTHER;
        };
    }

    private Instant parseInstantOrNow(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof String text && !text.isBlank()) {
                try {
                    return Instant.parse(text);
                } catch (Exception ignored) {
                    try {
                        return LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                    } catch (Exception ignoredAgain) {
                        // ignore
                    }
                }
            }
        }
        return Instant.now();
    }

    private Map<String, Object> nestedMap(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> converted.put(String.valueOf(nestedKey), nestedValue));
            return converted;
        }
        return Map.of();
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeSecret(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= 4) {
            return "*".repeat(normalized.length());
        }
        return "*".repeat(normalized.length() - 4) + normalized.substring(normalized.length() - 4);
    }
}
