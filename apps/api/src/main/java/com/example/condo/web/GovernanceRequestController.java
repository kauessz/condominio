package com.example.condo.web;

import com.example.condo.dto.governance.CreateGovernanceRequest;
import com.example.condo.dto.governance.GovernanceRequestResponse;
import com.example.condo.dto.governance.RejectGovernanceRequest;
import com.example.condo.service.GovernanceRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/governance/requests", "/api/governance/requests"})
public class GovernanceRequestController {

    private final GovernanceRequestService governanceRequestService;

    public GovernanceRequestController(GovernanceRequestService governanceRequestService) {
        this.governanceRequestService = governanceRequestService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Page<GovernanceRequestResponse> list(
        @RequestParam(defaultValue = "PENDING") String status,
        Pageable pageable
    ) {
        return governanceRequestService.list(status, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SINDICO')")
    public ResponseEntity<GovernanceRequestResponse> create(@Valid @RequestBody CreateGovernanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceRequestService.create(request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<GovernanceRequestResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(governanceRequestService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<GovernanceRequestResponse> reject(
        @PathVariable Long id,
        @Valid @RequestBody RejectGovernanceRequest request
    ) {
        return ResponseEntity.ok(governanceRequestService.reject(id, request.reason()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ResponseEntity<GovernanceRequestResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(governanceRequestService.cancel(id));
    }
}
