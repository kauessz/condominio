package com.example.condo.web;

import com.example.condo.dto.resident.CreateResidentRequest;
import com.example.condo.dto.resident.ResidentResponse;
import com.example.condo.dto.resident.UpdateResidentRequest;
import com.example.condo.service.ResidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller de moradores.
 *
 * O parâmetro condoId é opcional na listagem:
 * - SUPERUSER: deve fornecer condoId para filtrar por condomínio.
 * - Outros roles: condoId é ignorado; backend usa condominiumId do JWT.
 *
 * Roles com acesso:
 * - GET:              SUPERUSER, SINDICO, ADMIN, PORTARIA, MORADOR
 * - POST:             SUPERUSER, SINDICO, ADMIN, MORADOR (apenas própria unidade)
 * - DELETE:           SUPERUSER, SINDICO, ADMIN
 * - PUT:              SUPERUSER, SINDICO, ADMIN, MORADOR (somente moradores da própria unidade)
 */
@RestController
@RequestMapping({"/residents", "/api/residents"})
public class ResidentsController {

    private final ResidentService residentService;

    public ResidentsController(ResidentService residentService) {
        this.residentService = residentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN','PORTARIA','MORADOR')")
    public Page<ResidentResponse> list(
        @RequestParam(value = "condoId", required = false) Long condominiumId,
        @RequestParam(value = "condominiumId", required = false) Long condominiumIdAlt,
        @RequestParam(value = "q", required = false) String query,
        Pageable pageable
    ) {
        Long effectiveCondoId = condominiumId != null ? condominiumId : condominiumIdAlt;
        return residentService.search(effectiveCondoId, query, pageable);
    }

    @GetMapping("/count-by-unit")
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN','PORTARIA')")
    public Map<Long, Long> countByUnit(
        @RequestParam(value = "condoId", required = false) Long condominiumId,
        @RequestParam(value = "condominiumId", required = false) Long condominiumIdAlt
    ) {
        Long effectiveCondoId = condominiumId != null ? condominiumId : condominiumIdAlt;
        return residentService.countByUnit(effectiveCondoId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN','PORTARIA','MORADOR')")
    public ResponseEntity<ResidentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(residentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN','MORADOR')")
    public ResponseEntity<ResidentResponse> create(
        @Valid @RequestBody CreateResidentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN','MORADOR')")
    public ResponseEntity<ResidentResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateResidentRequest request
    ) {
        return ResponseEntity.ok(residentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','SINDICO','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        residentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
