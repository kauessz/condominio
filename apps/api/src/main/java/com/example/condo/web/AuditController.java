package com.example.condo.web;

import com.example.condo.dto.audit.AuditLogListItemResponse;
import com.example.condo.dto.common.PageResponse;
import com.example.condo.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping({"/audit", "/api/audit"})
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ResponseEntity<PageResponse<AuditLogListItemResponse>> list(
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "condominiumId", required = false) Long condominiumId,
        @RequestParam(value = "actorUserId", required = false) Long actorUserId,
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(auditService.list(module, condominiumId, actorUserId, actor, query, from, to, page, size));
    }
}
