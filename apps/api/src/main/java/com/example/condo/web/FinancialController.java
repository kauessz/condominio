package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.financial.FinancialSummaryResponse;
import com.example.condo.dto.financial.InvoiceDetailResponse;
import com.example.condo.dto.financial.InvoiceListItemResponse;
import com.example.condo.entity.FinancialConfig;
import com.example.condo.entity.Invoice;
import com.example.condo.service.FinancialService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        response.put("config", config.orElse(null));
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
        return service.saveConfig(condominiumId, monthlyFee, dueDay, lateFeePct, interestPct, pixKey, pixKeyType);
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
    public FinancialSummaryResponse summary(@RequestParam(required = false) Long condominiumId) {
        return service.summary(condominiumId);
    }

    @GetMapping("/invoices")
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

    @GetMapping("/invoices/{id}")
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
}
