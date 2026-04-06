package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.parking.ParkingAssignmentRequest;
import com.example.condo.dto.parking.ParkingAssignmentResponse;
import com.example.condo.dto.parking.ParkingDrawRegistrationResponse;
import com.example.condo.entity.*;
import com.example.condo.service.ParkingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService service;

    public ParkingController(ParkingService service) {
        this.service = service;
    }

    // ── Vagas ─────────────────────────────────────────────────────

    @GetMapping("/spots")
    public PageResponse<ParkingSpot> listSpots(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Page<ParkingSpot> p = service.listSpots(condominiumId, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @PostMapping("/spots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ParkingSpot createSpot(@RequestBody Map<String, Object> body) {
        Long condoId = body.get("condominiumId") != null ? ((Number) body.get("condominiumId")).longValue() : null;
        return service.createSpot(condoId, (String) body.get("code"), (String) body.get("description"));
    }

    @PutMapping("/spots/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ParkingSpot updateSpot(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String description = (String) body.get("description");
        boolean active = !Boolean.FALSE.equals(body.get("active"));
        return service.updateSpot(id, code, description, active);
    }

    @DeleteMapping("/spots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public void deleteSpot(@PathVariable Long id) {
        service.deleteSpot(id);
    }

    // ── Sorteios ──────────────────────────────────────────────────

    @GetMapping("/draws")
    public PageResponse<ParkingDraw> listDraws(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Page<ParkingDraw> p = service.listDraws(condominiumId, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @GetMapping("/draws/{id}")
    public ParkingDraw getDraw(@PathVariable Long id) {
        return service.getDraw(id);
    }

    @PostMapping("/draws")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ParkingDraw createDraw(@RequestBody Map<String, Object> body) {
        Long condoId = body.get("condominiumId") != null ? ((Number) body.get("condominiumId")).longValue() : null;
        Instant regOpen = Instant.parse((String) body.get("registrationOpenAt"));
        Instant regClose = Instant.parse((String) body.get("registrationCloseAt"));
        LocalDate validFrom = LocalDate.parse((String) body.get("validFrom"));
        LocalDate validUntil = LocalDate.parse((String) body.get("validUntil"));
        return service.createDraw(condoId, (String) body.get("name"), regOpen, regClose, validFrom, validUntil);
    }

    @GetMapping("/draws/{id}/registrations")
    public List<ParkingDrawRegistrationResponse> getRegistrations(@PathVariable Long id) {
        return service.getRegistrations(id);
    }

    @PostMapping("/draws/{id}/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MORADOR','SINDICO','ZELADOR')")
    public ParkingDrawRegistration register(@PathVariable Long id) {
        return service.registerForDraw(id);
    }

    @DeleteMapping("/draws/{id}/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MORADOR','SINDICO','ZELADOR')")
    public void unregister(@PathVariable Long id) {
        service.unregisterFromDraw(id);
    }

    @PostMapping("/draws/{id}/execute")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public List<ParkingSpotAssignment> execute(@PathVariable Long id) {
        return service.executeDraw(id);
    }

    // ── Atribuições ───────────────────────────────────────────────

    @GetMapping("/my-assignment")
    public Map<String, Object> myAssignment(@RequestParam(required = false) Long condominiumId) {
        Optional<ParkingSpotAssignment> assignment = service.getMyAssignment(condominiumId);
        Map<String, Object> response = new HashMap<>();
        response.put("assignment", assignment.orElse(null));
        response.put("hasAssignment", assignment.isPresent());
        return response;
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO','ZELADOR')")
    public List<ParkingAssignmentResponse> allAssignments(@RequestParam(required = false) Long condominiumId) {
        return service.getAllAssignments(condominiumId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ParkingAssignmentResponse createAssignment(@RequestBody ParkingAssignmentRequest request) {
        return service.createManualAssignment(request);
    }

    @PatchMapping("/assignments/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public ParkingAssignmentResponse updateAssignment(@PathVariable Long id, @RequestBody ParkingAssignmentRequest request) {
        return service.updateAssignment(id, request);
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public void cancelAssignment(@PathVariable Long id) {
        service.cancelAssignment(id);
    }
}
