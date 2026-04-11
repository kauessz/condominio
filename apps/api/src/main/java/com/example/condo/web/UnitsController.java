package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.unit.CreateUnitRequest;
import com.example.condo.dto.unit.UpdateUnitRequest;
import com.example.condo.dto.unit.UnitResponse;
import com.example.condo.service.UnitService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de unidades (apartamentos/casas).
 *
 * GET: todos os roles autenticados (PORTARIA e MORADOR precisam para dropdowns)
 * POST/PUT/DELETE: apenas gestores (SUPERUSER, ADMIN)
 */
@RestController
@RequestMapping({"/units", "/api/units"})
public class UnitsController {

    private final UnitService unitService;

    public UnitsController(UnitService unitService) {
        this.unitService = unitService;
    }

    /**
     * GET /units
     * Acessível a todos os roles autenticados.
     * Para não-SUPERUSER, o condoId da query é ignorado — backend usa JWT.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','ZELADOR','PORTARIA','MORADOR')")
    public PageResponse<UnitResponse> list(
        @RequestParam(value = "condoId",       required = false) Long condominiumId,
        @RequestParam(value = "condominiumId", required = false) Long condominiumIdAlt,
        @RequestParam(value = "q",             required = false) String query,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "pageSize", required = false) Integer pageSize,
        @RequestParam(value = "sortBy", defaultValue = "number") String sortBy,
        @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        Long effectiveCondoId = condominiumId != null ? condominiumId : condominiumIdAlt;
        var pageable = buildPageRequest(page, size, pageSize, sortBy, sortDir);
        return PageResponse.of(unitService.search(effectiveCondoId, query, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','ZELADOR','PORTARIA','MORADOR')")
    public ResponseEntity<UnitResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(unitService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN')")
    public ResponseEntity<UnitResponse> create(
        @Valid @RequestBody CreateUnitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN')")
    public ResponseEntity<UnitResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUnitRequest request
    ) {
        return ResponseEntity.ok(unitService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private PageRequest buildPageRequest(int page, Integer size, Integer pageSize, String sortBy, String sortDir) {
        int resolvedSize = Math.max(1, Math.min(size != null ? size : (pageSize != null ? pageSize : 20), 200));
        String resolvedSortBy = switch (sortBy) {
            case "block" -> "block";
            default -> "number";
        };
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(Math.max(page, 0), resolvedSize, Sort.by(direction, resolvedSortBy));
    }
}
