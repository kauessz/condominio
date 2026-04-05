package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.entity.CommonArea;
import com.example.condo.entity.Reservation;
import com.example.condo.exception.BusinessException;
import com.example.condo.service.ReservationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    // ── Áreas Comuns ──────────────────────────────────────────────

    @GetMapping("/common-areas")
    public PageResponse<CommonArea> listAreas(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        Page<CommonArea> p = service.listAreas(condominiumId, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @GetMapping("/common-areas/{id}")
    public CommonArea getArea(@PathVariable Long id) {
        return service.getArea(id);
    }

    @PostMapping("/common-areas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public CommonArea createArea(@RequestBody Map<String, Object> body) {
        Long condoId = body.get("condominiumId") != null ? ((Number) body.get("condominiumId")).longValue() : null;
        String name = (String) body.get("name");
        Integer capacity = body.get("capacity") != null ? ((Number) body.get("capacity")).intValue() : null;
        String rules = (String) body.get("rules");
        int maxHours = body.get("maxHoursPerReservation") != null ? ((Number) body.get("maxHoursPerReservation")).intValue() : 4;
        boolean requiresApproval = Boolean.TRUE.equals(body.get("requiresApproval"));
        Integer allowedStartHour = body.get("allowedStartHour") != null ? ((Number) body.get("allowedStartHour")).intValue() : null;
        Integer allowedEndHour = body.get("allowedEndHour") != null ? ((Number) body.get("allowedEndHour")).intValue() : null;
        String reservationDescription = (String) body.get("reservationDescription");
        String reservationApprovalMode = (String) body.get("reservationApprovalMode");
        boolean allowOverrideFromCondominiumDefault = Boolean.TRUE.equals(body.get("allowOverrideFromCondominiumDefault"));
        return service.createArea(
            condoId,
            name,
            capacity,
            rules,
            maxHours,
            requiresApproval,
            allowedStartHour,
            allowedEndHour,
            reservationDescription,
            reservationApprovalMode,
            allowOverrideFromCondominiumDefault
        );
    }

    @PutMapping("/common-areas/{id}")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public CommonArea updateArea(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer capacity = body.get("capacity") != null ? ((Number) body.get("capacity")).intValue() : null;
        String rules = (String) body.get("rules");
        int maxHours = body.get("maxHoursPerReservation") != null ? ((Number) body.get("maxHoursPerReservation")).intValue() : 0;
        boolean requiresApproval = Boolean.TRUE.equals(body.get("requiresApproval"));
        boolean active = !Boolean.FALSE.equals(body.get("active"));
        Integer allowedStartHour = body.get("allowedStartHour") != null ? ((Number) body.get("allowedStartHour")).intValue() : null;
        Integer allowedEndHour = body.get("allowedEndHour") != null ? ((Number) body.get("allowedEndHour")).intValue() : null;
        String reservationDescription = (String) body.get("reservationDescription");
        String reservationApprovalMode = (String) body.get("reservationApprovalMode");
        boolean allowOverrideFromCondominiumDefault = Boolean.TRUE.equals(body.get("allowOverrideFromCondominiumDefault"));
        return service.updateArea(
            id,
            name,
            capacity,
            rules,
            maxHours,
            requiresApproval,
            active,
            allowedStartHour,
            allowedEndHour,
            reservationDescription,
            reservationApprovalMode,
            allowOverrideFromCondominiumDefault
        );
    }

    @DeleteMapping("/common-areas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public void deleteArea(@PathVariable Long id) {
        service.deleteArea(id);
    }

    // ── Reservas ──────────────────────────────────────────────────

    @GetMapping("/reservations")
    public PageResponse<Reservation> listReservations(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(required = false) Long areaId,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<Reservation> p = service.listReservations(condominiumId, areaId, unitId, status, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @GetMapping("/reservations/{id}")
    public Reservation getReservation(@PathVariable Long id) {
        return service.getReservation(id);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation createReservation(@RequestBody Map<String, Object> body) {
        Long condoId = body.get("condominiumId") != null ? ((Number) body.get("condominiumId")).longValue() : null;
        Long areaId = requiredLong(body, "commonAreaId");
        Long unitId = body.get("unitId") != null ? ((Number) body.get("unitId")).longValue() : null;
        Instant start = Instant.parse(requiredString(body, "startDatetime"));
        Instant end = Instant.parse(requiredString(body, "endDatetime"));
        String title = (String) body.get("title");
        String notes = (String) body.get("notes");
        return service.createReservation(condoId, areaId, unitId, start, end, title, notes);
    }

    @PatchMapping("/reservations/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Reservation approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PatchMapping("/reservations/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Reservation reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return service.reject(id, reason);
    }

    @PatchMapping("/reservations/{id}/cancel")
    public Reservation cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    private Long requiredLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Number number)) {
            throw new BusinessException(field + " é obrigatório");
        }
        return number.longValue();
    }

    private String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessException(field + " é obrigatório");
        }
        return text;
    }
}
