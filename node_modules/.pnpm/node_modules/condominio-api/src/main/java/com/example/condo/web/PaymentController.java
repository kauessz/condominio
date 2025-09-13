package com.example.condo.web;

import com.example.condo.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping("/invoices/{id}/charge")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<?> createCharge(@PathVariable("id") Long invoiceId) {
        String chargeId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of(
                "invoiceId", invoiceId,
                "chargeId", chargeId,
                "method", "PIX",
                "qrCode", "00020126580014BR.GOV.BCB.PIX...",
                "createdAt", Instant.now().toString(),
                "tenant", TenantContext.get()
        ));
    }

    @PostMapping("/webhooks/payments")
    public ResponseEntity<?> webhook(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("status", "received", "payload", body));
    }
}