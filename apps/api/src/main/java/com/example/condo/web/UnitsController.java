package com.example.condo.web;

import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UnitRepository.UnitCountView;
import com.example.condo.entity.Unit;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/units", "/api/units"})
public class UnitsController {

  private final UnitRepository units;
  private final CondominiumRepository condos;

  public UnitsController(UnitRepository units, CondominiumRepository condos) {
    this.units = units;
    this.condos = condos;
  }

  private static String norm(String s) { return s == null ? "" : s.trim(); }

  private static String buildCode(String number, String block) {
    String n = norm(number);
    String b = norm(block);
    if (b.isEmpty()) return n;
    return b.replaceAll("\\s+", "").toUpperCase() + "-" + n;
  }

  // DTOs/Reqs
  public record UnitDTO(Long id, String number, String block, Long residentCount) {
    public static UnitDTO from(Unit u) {
      String b = (u.getBlock() == null || u.getBlock().isBlank()) ? null : u.getBlock();
      return new UnitDTO(u.getId(), u.getNumber(), b, null);
    }
    public static UnitDTO fromCountView(UnitCountView v) {
      String b = (v.getBlock() == null || v.getBlock().isBlank()) ? null : v.getBlock();
      return new UnitDTO(v.getId(), v.getNumber(), b, v.getResidentCount() == null ? 0L : v.getResidentCount());
    }
  }
  public record NewUnitReq(Long condoId, String number, String block) {}
  public record UpdateUnitReq(String number, String block) {}
  public record ErrorDTO(String error) { public static ErrorDTO of(String m){ return new ErrorDTO(m); } }

  @GetMapping
  public Page<UnitDTO> list(@RequestParam("condoId") Long condoId,
                            @RequestParam(value = "q", required = false) String q,
                            Pageable pageable) {
    String tenant = TenantContext.get();
    // usa a query com contagem
    Page<UnitCountView> page = units.searchWithCount(tenant, condoId, q, pageable);
    return page.map(UnitDTO::fromCountView);
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody NewUnitReq req) {
    String tenant = TenantContext.get();

    var c = condos.findByTenantIdAndId(tenant, req.condoId());
    if (c.isEmpty()) {
      return ResponseEntity.status(403).body(ErrorDTO.of("Condomínio não pertence ao tenant atual."));
    }

    String number = norm(req.number());
    String block  = norm(req.block());
    if (number.isBlank()) return ResponseEntity.badRequest().body(ErrorDTO.of("Número é obrigatório."));

    String bLowerOrNull = block.isBlank() ? null : block.toLowerCase();
    boolean dup = units.existsDuplicate(tenant, req.condoId(), number, bLowerOrNull, null);
    if (dup) return ResponseEntity.badRequest().body(ErrorDTO.of("Já existe uma unidade com esse número/bloco neste condomínio."));

    Unit u = new Unit();
    u.setTenantId(tenant);
    u.setCondominiumId(req.condoId());
    u.setNumber(number);
    u.setBlock(block.isBlank() ? null : block);
    u.setCode(buildCode(number, block));

    Unit saved = units.save(u);
    return ResponseEntity.ok(UnitDTO.from(saved));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateUnitReq req) {
    String tenant = TenantContext.get();
    var uOpt = units.findByTenantIdAndId(tenant, id);
    if (uOpt.isEmpty()) return ResponseEntity.status(404).body(ErrorDTO.of("Unidade não encontrada"));

    Unit u = uOpt.get();
    String number = norm(req.number());
    String block  = norm(req.block());
    if (number.isBlank()) return ResponseEntity.badRequest().body(ErrorDTO.of("Número é obrigatório."));

    String bLowerOrNull = block.isBlank() ? null : block.toLowerCase();
    boolean dup = units.existsDuplicate(tenant, u.getCondominiumId(), number, bLowerOrNull, id);
    if (dup) return ResponseEntity.badRequest().body(ErrorDTO.of("Já existe uma unidade com esse número/bloco neste condomínio."));

    u.setNumber(number);
    u.setBlock(block.isBlank() ? null : block);
    u.setCode(buildCode(number, block));

    Unit saved = units.save(u);
    return ResponseEntity.ok(UnitDTO.from(saved));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    String tenant = TenantContext.get();
    var uOpt = units.findByTenantIdAndId(tenant, id);
    if (uOpt.isEmpty()) return ResponseEntity.status(404).body(ErrorDTO.of("Unidade não encontrada"));
    units.delete(uOpt.get());
    return ResponseEntity.noContent().build();
  }
}