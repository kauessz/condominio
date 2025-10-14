package com.example.condo.web;

import com.example.condo.entity.Visitor;
import com.example.condo.repo.VisitorRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/visitors", "/api/visitors"})
public class VisitorsController {

  private final VisitorRepository visitors;
  private final UnitRepository units;
  private final CondominiumRepository condos;

  public VisitorsController(VisitorRepository visitors, UnitRepository units, CondominiumRepository condos) {
    this.visitors = visitors;
    this.units = units;
    this.condos = condos;
  }

  // ===== DTOs =====
  public record PageResp<T>(List<T> items, long total, int page, int pageSize) {}
  public record VisitorDTO(
      Long id,
      String tenantId,
      Long condominiumId,
      Long unitId,
      String name,
      String document,
      String plate,
      String phone,
      String email,
      String note,
      Instant checkInAt,
      Instant checkOutAt,
      Visitor.Status status,
      Visitor.Type type,
      Instant approvedAt,
      String approvedBy,
      String rejectionReason,
      Instant expectedInAt,
      Instant expectedOutAt
  ) {}

  public record CreateVisitorReq(
      Long condominiumId,
      Long unitId,
      String name,
      String document,
      String plate,
      String phone,
      String email,
      String note,
      Instant checkInAt,        // opcional; se nulo, servidor usa now()
      Instant expectedInAt,     // opcional
      Visitor.Status status,    // default PENDING
      Visitor.Type type         // default VISITOR
  ) {}

  public record UpdateVisitorReq(
      Long unitId,
      String name,
      String document,
      String plate,
      String phone,
      String email,
      String note,
      Instant checkInAt,
      Instant checkOutAt,
      Visitor.Status status,
      Visitor.Type type,
      Instant approvedAt,
      String approvedBy,
      String rejectionReason,
      Instant expectedInAt,
      Instant expectedOutAt
  ) {}

  private VisitorDTO toDTO(Visitor v) {
    return new VisitorDTO(
        v.getId(),
        v.getTenantId(),
        v.getCondominiumId(),
        v.getUnitId(),
        v.getName(),
        v.getDocument(),
        v.getPlate(),
        v.getPhone(),
        v.getEmail(),
        v.getNote(),
        v.getCheckInAt(),
        v.getCheckOutAt(),
        v.getStatus(),
        v.getType(),
        v.getApprovedAt(),
        v.getApprovedBy(),
        v.getRejectionReason(),
        v.getExpectedInAt(),
        v.getExpectedOutAt()
    );
  }

  // ===== LIST / SEARCH =====
  @GetMapping
  public PageResp<VisitorDTO> list(
      @RequestParam(name = "condoId") Long condoId,
      @RequestParam(name = "unitId", required = false) Long unitId,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "8") int pageSize,
      @RequestParam(defaultValue = "checkInAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      @RequestHeader(value = "X-Tenant", required = false) String tenantHeader
  ) {
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? TenantContext.get() : tenantHeader.trim();

    Sort sort = Sort.by(
        "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC,
        mapSort(sortBy)
    );
    Pageable pageable = PageRequest.of(page, pageSize, sort);

    // Conversão p/ LocalDateTime (repositório espera LDT)
    LocalDateTime fromDt = parseLocalDateTimeFromParam(from);
    LocalDateTime toDt   = parseLocalDateTimeFromParam(to);

    Page<Visitor> p = visitors.search(
        tenant,
        condoId,
        unitId,
        (q == null || q.isBlank()) ? null : q.trim(),
        fromDt,
        toDt,
        pageable
    );

    List<VisitorDTO> items = p.map(this::toDTO).getContent();
    return new PageResp<>(items, p.getTotalElements(), p.getNumber(), p.getSize());
  }

  private static String mapSort(String sortBy) {
    return switch (sortBy) {
      case "name" -> "name";
      case "status" -> "status";
      case "type" -> "type";
      case "checkOutAt" -> "checkOutAt";
      default -> "checkInAt";
    };
  }

  /**
   * Aceita "yyyy-MM-ddTHH:mm" (datetime-local do browser) ou ISO instant.
   * Converte para LocalDateTime na timezone do servidor.
   */
  private static LocalDateTime parseLocalDateTimeFromParam(String v) {
    if (v == null || v.isBlank()) return null;
    try {
      // Ex.: 2025-10-12T21:00 (input type="datetime-local")
      return LocalDateTime.parse(v);
    } catch (Exception ignored) {
      try {
        // Ex.: 2025-10-12T00:00:00Z -> converte para LDT no fuso do servidor
        Instant ins = Instant.parse(v);
        return LocalDateTime.ofInstant(ins, ZoneId.systemDefault());
      } catch (Exception e) {
        return null;
      }
    }
  }

  // ===== CREATE =====
  @PostMapping
  public ResponseEntity<?> create(
      @RequestBody CreateVisitorReq req,
      @RequestHeader(value = "X-Tenant", required = false) String tenantHeader
  ) {
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? TenantContext.get() : tenantHeader.trim();

    if (tenant == null || tenant.isBlank()) {
      return ResponseEntity.status(403).body(Map.of("error", "tenant_required"));
    }
    if (req == null || req.name() == null || req.name().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "name_required"));
    }
    if (req.condominiumId() == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "condo_required"));
    }

    if (condos.findByTenantIdAndId(tenant, req.condominiumId()).isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "condo_not_found"));
    }
    if (req.unitId() != null) {
      boolean ok = units.existsByTenantIdAndIdAndCondominiumId(tenant, req.unitId(), req.condominiumId());
      if (!ok) {
        return ResponseEntity.badRequest().body(Map.of("error", "unit_not_in_condo"));
      }
    }

    var v = new Visitor();
    v.setTenantId(tenant);
    v.setCondominiumId(req.condominiumId());
    v.setUnitId(req.unitId());
    v.setName(req.name().trim());
    v.setDocument(req.document() == null ? null : req.document().trim());
    v.setPlate(req.plate() == null ? null : req.plate().trim());
    v.setPhone(req.phone() == null ? null : req.phone().trim());
    v.setEmail(req.email() == null ? null : req.email().trim());
    v.setNote(req.note() == null ? null : req.note().trim());

    // se vier nulo, usa agora
    v.setCheckInAt(req.checkInAt() != null ? req.checkInAt() : Instant.now());
    // previsão (opcional)
    v.setExpectedInAt(req.expectedInAt());

    v.setStatus(req.status() != null ? req.status() : Visitor.Status.PENDING);
    v.setType(req.type() != null ? req.type() : Visitor.Type.VISITOR);

    var saved = visitors.save(v);
    return ResponseEntity.ok(Map.of("id", saved.getId()));
  }

  // ===== UPDATE =====
  @PutMapping("/{id}")
  public ResponseEntity<?> update(
      @PathVariable Long id,
      @RequestBody UpdateVisitorReq req,
      @RequestHeader(value = "X-Tenant", required = false) String tenantHeader
  ) {
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? TenantContext.get() : tenantHeader.trim();

    Optional<Visitor> opt = visitors.findByTenantIdAndId(tenant, id);
    if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "not_found"));

    var v = opt.get();

    if (req.name() != null) v.setName(req.name().trim());
    if (req.document() != null) v.setDocument(req.document().trim());
    if (req.plate() != null) v.setPlate(req.plate().trim());
    if (req.phone() != null) v.setPhone(req.phone().trim());
    if (req.email() != null) v.setEmail(req.email().trim());
    if (req.note() != null) v.setNote(req.note().trim());

    if (req.unitId() != null) {
      Long condoId = v.getCondominiumId();
      boolean ok = units.existsByTenantIdAndIdAndCondominiumId(tenant, req.unitId(), condoId);
      if (!ok) {
        return ResponseEntity.badRequest().body(Map.of("error", "unit_not_in_condo"));
      }
      v.setUnitId(req.unitId());
    }

    if (req.checkInAt() != null) v.setCheckInAt(req.checkInAt());
    if (req.checkOutAt() != null) v.setCheckOutAt(req.checkOutAt());
    if (req.status() != null) v.setStatus(req.status());
    if (req.type() != null) v.setType(req.type());
    if (req.approvedAt() != null) v.setApprovedAt(req.approvedAt());
    if (req.approvedBy() != null) v.setApprovedBy(req.approvedBy());
    if (req.rejectionReason() != null) v.setRejectionReason(req.rejectionReason());
    if (req.expectedInAt() != null) v.setExpectedInAt(req.expectedInAt());
    if (req.expectedOutAt() != null) v.setExpectedOutAt(req.expectedOutAt());

    visitors.save(v);
    return ResponseEntity.noContent().build();
  }

  // ===== DELETE =====
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @PathVariable Long id,
      @RequestHeader(value = "X-Tenant", required = false) String tenantHeader
  ) {
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? TenantContext.get() : tenantHeader.trim();

    Optional<Visitor> opt = visitors.findByTenantIdAndId(tenant, id);
    if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "not_found"));

    visitors.delete(opt.get());
    return ResponseEntity.noContent().build();
  }
}