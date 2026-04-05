package com.example.condo.web;

import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/residents", "/api/residents"})
public class ResidentsController {

  private final ResidentRepository residents;
  private final UnitRepository units;
  private final CondominiumRepository condos;

  public ResidentsController(ResidentRepository residents,
                             UnitRepository units,
                             CondominiumRepository condos) {
    this.residents = residents;
    this.units = units;
    this.condos = condos;
  }

  // ===== helpers =====
  private static String norm(String s) { return s == null ? "" : s.trim(); }

  // ---- DTOs / Requests ----
  public record UnitMini(Long id, String number, String block) {
    public static UnitMini from(Unit u) {
      if (u == null) return null;
      String b = (u.getBlock() == null || u.getBlock().isBlank()) ? null : u.getBlock();
      return new UnitMini(u.getId(), u.getNumber(), b);
    }
  }
  public record ResidentDTO(Long id, String name, String email, String phone,
                            Long unitId, UnitMini unit) {
    public static ResidentDTO from(Resident r, Unit u) {
      String phone = (r.getPhone() == null || r.getPhone().isBlank()) ? null : r.getPhone();
      return new ResidentDTO(
          r.getId(), r.getName(), r.getEmail(), phone,
          r.getUnitId(), UnitMini.from(u)
      );
    }
  }
  public record NewResidentReq(Long condoId, String name, String email, String phone, Long unitId) {}
  public record UpdateResidentReq(String name, String email, String phone, Long unitId, Long condoId) {}
  public record ErrorDTO(String error) { public static ErrorDTO of(String m){ return new ErrorDTO(m); } }

  // ===== listagem (paginada) sem N+1 =====
  @GetMapping
  public Page<ResidentDTO> list(@RequestParam("condoId") Long condoId,
                                @RequestParam(value = "q", required = false) String q,
                                Pageable pageable) {
    String tenant = TenantContext.get();
    Page<Object[]> page = residents.searchWithUnit(tenant, condoId, q, pageable);
    return page.map(row -> {
      var r = (com.example.condo.entity.Resident) row[0];
      var u = (com.example.condo.entity.Unit) row[1];
      return ResidentDTO.from(r, u);
    });
  }

  // ===== criar =====
  @PostMapping
  public ResponseEntity<?> create(@RequestBody NewResidentReq req) {
    String tenant = TenantContext.get();

    var c = condos.findByTenantIdAndId(tenant, req.condoId());
    if (c.isEmpty()) {
      return ResponseEntity.status(403).body(ErrorDTO.of("Condomínio não pertence ao tenant atual."));
    }

    String name  = norm(req.name());
    String email = norm(req.email());
    String phone = norm(req.phone());
    if (name.isBlank())  return ResponseEntity.badRequest().body(ErrorDTO.of("Nome é obrigatório."));
    if (email.isBlank()) return ResponseEntity.badRequest().body(ErrorDTO.of("Email é obrigatório."));

    // valida unidade (se informada)
    Long unitId = req.unitId();
    if (unitId != null) {
      var uOpt = units.findByTenantIdAndId(tenant, unitId);
      if (uOpt.isEmpty() || !uOpt.get().getCondominiumId().equals(req.condoId())) {
        return ResponseEntity.badRequest().body(ErrorDTO.of("Unidade inválida para este condomínio."));
      }
    }

    Resident r = new Resident();
    r.setTenantId(tenant);
    r.setCondominiumId(req.condoId());
    r.setName(name);
    r.setEmail(email);
    r.setPhone(phone.isBlank() ? null : phone);
    r.setUnitId(unitId);

    Resident saved = residents.save(r);
    Unit u = (unitId == null) ? null : units.findByTenantIdAndId(tenant, unitId).orElse(null);
    return ResponseEntity.ok(ResidentDTO.from(saved, u));
  }

  // ===== atualizar =====
  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateResidentReq req) {
    String tenant = TenantContext.get();
    var ropt = residents.findByTenantIdAndId(tenant, id);
    if (ropt.isEmpty()) return ResponseEntity.status(404).body(ErrorDTO.of("Morador não encontrado"));

    Resident r = ropt.get();

    String name  = norm(req.name());
    String email = norm(req.email());
    String phone = norm(req.phone());
    if (name.isBlank())  return ResponseEntity.badRequest().body(ErrorDTO.of("Nome é obrigatório."));
    if (email.isBlank()) return ResponseEntity.badRequest().body(ErrorDTO.of("Email é obrigatório."));

    Long reqCondo = req.condoId();
    if (reqCondo != null && !reqCondo.equals(r.getCondominiumId())) {
      return ResponseEntity.badRequest().body(ErrorDTO.of("Condomínio do morador não pode ser alterado neste endpoint."));
    }

    Long unitId = req.unitId();
    if (unitId != null) {
      var uOpt = units.findByTenantIdAndId(tenant, unitId);
      if (uOpt.isEmpty() || !uOpt.get().getCondominiumId().equals(r.getCondominiumId())) {
        return ResponseEntity.badRequest().body(ErrorDTO.of("Unidade inválida para este condomínio."));
      }
    }

    r.setName(name);
    r.setEmail(email);
    r.setPhone(phone.isBlank() ? null : phone);
    r.setUnitId(unitId);

    Resident saved = residents.save(r);
    Unit u = (unitId == null) ? null : units.findByTenantIdAndId(tenant, unitId).orElse(null);
    return ResponseEntity.ok(ResidentDTO.from(saved, u));
  }

  // ===== excluir =====
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    String tenant = TenantContext.get();
    var ropt = residents.findByTenantIdAndId(tenant, id);
    if (ropt.isEmpty()) return ResponseEntity.status(404).body(ErrorDTO.of("Morador não encontrado"));
    residents.delete(ropt.get());
    return ResponseEntity.noContent().build();
  }

  // ===== agregado: contagem de moradores por unidade =====
  @GetMapping("/count-by-unit")
  public Map<Long, Long> countByUnit(@RequestParam("condoId") Long condoId) {
    String tenant = TenantContext.get();
    List<Object[]> rows = residents.countByUnit(tenant, condoId);
    Map<Long, Long> out = new HashMap<>();
    for (Object[] r : rows) {
      Long unitId = (Long) r[0];
      Long cnt = (Long) r[1];
      out.put(unitId, cnt);
    }
    return out; // ex.: { 12: 3, 18: 1, ... }
  }
}