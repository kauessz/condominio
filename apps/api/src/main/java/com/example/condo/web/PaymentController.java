package com.example.condo.web;

import com.example.condo.dto.financial.InvoiceDetailResponse;
import com.example.condo.service.FinancialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final FinancialService financialService;

    public PaymentController(FinancialService financialService) {
        this.financialService = financialService;
    }

    @PostMapping("/invoices/{id}/charge")
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN')")
    public ResponseEntity<InvoiceDetailResponse> createCharge(@PathVariable("id") Long invoiceId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(
            financialService.createExternalCharge(
                invoiceId,
                body != null && body.get("billingType") != null ? body.get("billingType").toString() : null
            )
        );
    }

    @PostMapping("/webhooks/payments")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body,
                                                       @RequestHeader(name = "asaas-access-token", required = false) String token,
                                                       @RequestHeader(name = "Asaas-Access-Token", required = false) String tokenAlt) {
        return ResponseEntity.ok(financialService.handleAsaasWebhook(body, token != null ? token : tokenAlt));
    }
}
