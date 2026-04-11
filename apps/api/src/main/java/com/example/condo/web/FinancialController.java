package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.financial.FinancialSummaryResponse;
import com.example.condo.dto.financial.InvoiceDetailResponse;
import com.example.condo.dto.financial.InvoiceListItemResponse;
import com.example.condo.entity.FinancialConfig;
import com.example.condo.entity.Invoice;
import com.example.condo.service.FinancialService;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/financial")
public class FinancialController {

    private final FinancialService service;

    public FinancialController(FinancialService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO','MORADOR')")
    public Map<String, Object> getConfig(@RequestParam(required = false) Long condominiumId) {
        Optional<FinancialConfig> config = service.getConfig(condominiumId);
        Map<String, Object> response = new HashMap<>();
        response.put("config", config.map(this::sanitizeConfigResponse).orElse(null));
        return response;
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public FinancialConfig saveConfig(@RequestParam(required = false) Long condominiumId,
                                      @RequestBody Map<String, Object> body) {
        BigDecimal monthlyFee = body.get("monthlyFee") != null
            ? new BigDecimal(body.get("monthlyFee").toString()) : null;
        int dueDay = body.get("dueDay") != null ? ((Number) body.get("dueDay")).intValue() : 10;
        BigDecimal lateFeePct = body.get("lateFeePct") != null
            ? new BigDecimal(body.get("lateFeePct").toString()) : null;
        BigDecimal interestPct = body.get("interestPct") != null
            ? new BigDecimal(body.get("interestPct").toString()) : null;
        String pixKey = (String) body.get("pixKey");
        String pixKeyType = (String) body.get("pixKeyType");
        return service.saveConfig(
            condominiumId,
            monthlyFee,
            dueDay,
            lateFeePct,
            interestPct,
            pixKey,
            pixKeyType,
            body.get("defaultBillingType") != null ? body.get("defaultBillingType").toString() : null,
            parseBoolean(body.get("notificationEmailEnabled")),
            parseBoolean(body.get("notificationWhatsappEnabled")),
            parseBoolean(body.get("asaasEnabled")),
            body.get("asaasWebhookToken") != null ? body.get("asaasWebhookToken").toString() : null
        );
    }

    @PostMapping("/invoices/launch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public Map<String, Object> launchInvoices(@RequestParam(required = false) Long condominiumId,
                                              @RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("amount") != null ? new BigDecimal(body.get("amount").toString()) : null;
        LocalDate dueDate = body.get("dueDate") != null ? LocalDate.parse(body.get("dueDate").toString()) : null;
        Long targetUnitId = body.get("targetUnitId") instanceof Number number ? number.longValue() : null;
        return service.launchCharges(
            condominiumId,
            body.get("criterion") != null ? body.get("criterion").toString() : null,
            body.get("appliesTo") != null ? body.get("appliesTo").toString() : null,
            body.get("chargeType") != null ? body.get("chargeType").toString() : null,
            body.get("amountMode") != null ? body.get("amountMode").toString() : null,
            body.get("billingType") != null ? body.get("billingType").toString() : null,
            amount,
            body.get("title") != null ? body.get("title").toString() : null,
            body.get("description") != null ? body.get("description").toString() : null,
            body.get("referenceMonth") != null ? body.get("referenceMonth").toString() : null,
            dueDate,
            targetUnitId,
            parseLongList(body.get("targetUnitIds")),
            parseStringList(body.get("targetBlocks"))
        );
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO','MORADOR')")
    public FinancialSummaryResponse summary(@RequestParam(required = false) Long condominiumId,
                                            @RequestParam(required = false) String referenceMonthFrom,
                                            @RequestParam(required = false) String referenceMonthTo) {
        return service.summary(condominiumId, referenceMonthFrom, referenceMonthTo);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public PageResponse<InvoiceListItemResponse> listInvoices(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<InvoiceListItemResponse> p = service.listInvoices(condominiumId, unitId, status, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @GetMapping("/invoices/search")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO','MORADOR')")
    public PageResponse<InvoiceListItemResponse> searchInvoices(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) Long residentId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String chargeType,
        @RequestParam(required = false, name = "q") String query,
        @RequestParam(required = false) String referenceMonthFrom,
        @RequestParam(required = false) String referenceMonthTo,
        @RequestParam(required = false) LocalDate dueDateFrom,
        @RequestParam(required = false) LocalDate dueDateTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "dueDate") String sortBy,
        @RequestParam(defaultValue = "DESC") String direction
    ) {
        Page<InvoiceListItemResponse> p = service.searchInvoices(
            condominiumId,
            unitId,
            residentId,
            status,
            chargeType,
            query,
            referenceMonthFrom,
            referenceMonthTo,
            dueDateFrom,
            dueDateTo,
            sortBy,
            direction,
            buildInvoicePageRequest(page, size, sortBy, direction)
        );
        return PageResponse.of(p);
    }

    @GetMapping({"/invoices/mine", "/my-invoices"})
    @PreAuthorize("hasAnyRole('MORADOR','SINDICO','ZELADOR')")
    public PageResponse<InvoiceListItemResponse> listResidentInvoices(
        @RequestParam(required = false) String status,
        @RequestParam(required = false, name = "q") String query,
        @RequestParam(required = false) String referenceMonthFrom,
        @RequestParam(required = false) String referenceMonthTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "dueDate") String sortBy,
        @RequestParam(defaultValue = "DESC") String direction
    ) {
        Page<InvoiceListItemResponse> p = service.listResidentInvoices(
            status,
            query,
            referenceMonthFrom,
            referenceMonthTo,
            sortBy,
            direction,
            buildInvoicePageRequest(page, size, sortBy, direction)
        );
        return PageResponse.of(p);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO','MORADOR')")
    public InvoiceDetailResponse getInvoice(@PathVariable Long id) {
        return service.getInvoice(id);
    }

    @PatchMapping("/invoices/{id}/pay")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public Invoice registerPayment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal paidAmount = body.get("paidAmount") != null
            ? new BigDecimal(body.get("paidAmount").toString()) : null;
        String method = (String) body.get("paymentMethod");
        String notes = (String) body.get("notes");
        return service.registerPayment(id, paidAmount, method, notes);
    }

    @PatchMapping("/invoices/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public InvoiceDetailResponse cancelInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.cancelInvoice(id, body != null && body.get("reason") != null ? body.get("reason").toString() : null);
    }

    @PatchMapping("/invoices/{id}/waive")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public InvoiceDetailResponse waiveInvoice(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.waiveInvoice(id, body != null && body.get("reason") != null ? body.get("reason").toString() : null);
    }

    @PostMapping("/invoices/{id}/external-charge")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','FINANCEIRO')")
    public InvoiceDetailResponse createExternalCharge(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.createExternalCharge(id, body != null && body.get("billingType") != null ? body.get("billingType").toString() : null);
    }

    /**
     * Endpoint global de webhook Asaas.
     * O token é validado por condomínio usando a invoice localizada pelo externalChargeId.
     */
    @PostMapping("/webhooks/asaas")
    public Map<String, Object> asaasWebhook(@RequestBody Map<String, Object> body,
                                            @RequestHeader(name = "asaas-access-token", required = false) String token,
                                            @RequestHeader(name = "Asaas-Access-Token", required = false) String tokenAlt) {
        return service.handleAsaasWebhook(body, token != null ? token : tokenAlt);
    }

    /**
     * Endpoint de webhook Asaas por condomínio.
     * Valida o token diretamente contra a configuração do condomínio {condominiumId}
     * antes de processar o evento — não depende de localizar a invoice primeiro.
     *
     * Configure no painel Asaas Sandbox/Produção como:
     *   https://SEU_DOMINIO/api/financial/webhooks/asaas/{ID_DO_CONDO}
     */
    @PostMapping("/webhooks/asaas/{condominiumId}")
    public Map<String, Object> asaasWebhookForCondo(
        @PathVariable Long condominiumId,
        @RequestBody Map<String, Object> body,
        @RequestHeader(name = "asaas-access-token", required = false) String token,
        @RequestHeader(name = "Asaas-Access-Token", required = false) String tokenAlt
    ) {
        String resolvedToken = token != null ? token : tokenAlt;
        service.validateWebhookTokenForCondo(condominiumId, resolvedToken);
        return service.handleAsaasWebhook(body, resolvedToken);
    }

    private List<Long> parseLongList(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Long> values = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Number number) {
                values.add(number.longValue());
            }
        }
        return values;
    }

    private List<String> parseStringList(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : iterable) {
            if (item != null) {
                values.add(item.toString());
            }
        }
        return values;
    }

    private Boolean parseBoolean(Object raw) {
        return raw instanceof Boolean value ? value : null;
    }

    private PageRequest buildInvoicePageRequest(int page, int size, String sortBy, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize, Sort.unsorted());
    }

    private FinancialConfig sanitizeConfigResponse(FinancialConfig config) {
        if (canManageFinancialConfig()) {
            return config;
        }
        FinancialConfig copy = new FinancialConfig();
        copy.setId(config.getId());
        copy.setTenantId(config.getTenantId());
        copy.setCondominiumId(config.getCondominiumId());
        copy.setMonthlyFee(config.getMonthlyFee());
        copy.setDueDay(config.getDueDay());
        copy.setLateFeePct(config.getLateFeePct());
        copy.setInterestPct(config.getInterestPct());
        copy.setPixKey(config.getPixKey());
        copy.setPixKeyType(config.getPixKeyType());
        copy.setDefaultBillingType(config.getDefaultBillingType());
        copy.setNotificationEmailEnabled(config.isNotificationEmailEnabled());
        copy.setNotificationWhatsappEnabled(config.isNotificationWhatsappEnabled());
        copy.setAsaasEnabled(config.isAsaasEnabled());
        copy.setUpdatedAt(config.getUpdatedAt());
        copy.setAsaasWebhookToken(null);
        return copy;
    }

    private boolean canManageFinancialConfig() {
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) {
            return false;
        }
        return switch (ctx.role().toUpperCase()) {
            case "SUPERUSER", "ADMIN", "SINDICO", "FINANCEIRO" -> true;
            default -> false;
        };
    }
}
