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

@RestController
@RequestMapping("/residents")
public class ResidentsController {

  private final ResidentRepository residents;
  private final UnitRepository units;
  private final CondominiumRepository condos;

  public ResidentsController(ResidentRepository residents, UnitRepository units, CondominiumRepository condos) {
    this.residents = residents;
    this.units = units;
    this.condos = condos;
  }

  // DTOs/Reqs
  public record UnitLite(Long id, String number, String block) {
    public static UnitLite of(Unit u){
      if (u == null) return null;
      String b = (u.getBlock()==null || u.getBlock().isBlank()) ? null : u.getBlock();
      return new UnitLite(u.getId(), u.getNumber(), b);
    }
  }
  public record ResidentDTO(Long id, String name, String email, String phone,
                            String condoId, Long unitId, UnitLite unit) {}
  public record ErrorDTO(String error) { public static ErrorDTO of(String m){return new ErrorDTO(m);} }
  public record NewResidentReq(String name, String email, String phone, Long condoId, Long unitId) {}
  public record UpdateResidentReq(String name, String email, String phone, Long unitId) {}

  private ResidentDTO toDTO(Resident r) {
    UnitLite u = null;
    if (r.getUnitId() != null) {
      var uOpt = units.findByTenantIdAndId(r.getTenantId(), r.getUnitId());
      u = uOpt.map(UnitLite::of).orElse(null);
    }
    return new ResidentDTO(
      r.getId(), r.getName(), r.getEmail(), r.getPhone(),
      String.valueOf(r.getCondominiumId()), r.getUnitId(), u
    );
  }

  // GET /residents?condoId=...&q=&page=&size=
  @GetMapping
  public Page<ResidentDTO> list(@RequestParam("condoId") Long condoId,
                                @RequestParam(value="q", required=false) String q,
                                Pageable pageable) {
    String tenant = TenantContext.get();
    return residents.search(tenant, condoId, q, pageable).map(this::toDTO);
  }

  // POST /residents
  @PostMapping
  public ResponseEntity<?> create(@RequestBody NewResidentReq req) {
    String tenant = TenantContext.get();

    var c = condos.findByTenantIdAndId(tenant, req.condoId());
    if (c.isEmpty()) return ResponseEntity.status(403).body(ErrorDTO.of("Condomínio inválido para o tenant."));

    if (req.unitId() != null) {
      var u = units.findByTenantIdAndId(tenant, req.unitId());
      if (u.isEmpty() || !u.get().getCondominiumId().equals(req.condoId()))
        return ResponseEntity.badRequest().body(ErrorDTO.of("Unidade inválida para este condomínio."));
    }

    Resident r = new Resident();
    r.setTenantId(tenant);
    r.setCondominiumId(req.condoId());
    r.setUnitId(req.unitId());
    r.setName(req.name().trim());
    r.setEmail(req.email().trim());
    r.setPhone(req.phone().trim());
    return ResponseEntity.ok(toDTO(residents.save(r)));
  }

  // PUT /residents/{id}
  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateResidentReq req) {
    String tenant = TenantContext.get();
    var ropt = residents.findByTenantIdAndId(tenant, id);
    if (ropt.isEmpty()) return ResponseEntity.status(404).body(ErrorDTO.of("Morador não encontrado"));

    Resident r = ropt.get();

    if (req.unitId() != null) {
      var u = units.findByTenantIdAndId(tenant, req.unitId());
      if (u.isEmpty() || !u.get().getCondominiumId().equals(r.getCondominiumId()))
        return ResponseEntity.badRequest().body(ErrorDTO.of("Unidade inválida para este condomínio."));
    }

    r.setName(req.name().trim());
    r.setEmail(req.email().trim());
    r.setPhone(req.phone().trim());
    r.setUnitId(req.unitId());
    return ResponseEntity.ok(toDTO(residents.save(r)));
  }

  // DELETE /residents/{id}
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    String tenant = TenantContext.get();
    var ropt = residents.findByTenantIdAndId(tenant, id);
    if (ropt.isEmpty()) return ResponseEntity.status(404).body(ErrorDTO.of("Morador não encontrado"));
    residents.delete(ropt.get());
    return ResponseEntity.noContent().build();
  }
}