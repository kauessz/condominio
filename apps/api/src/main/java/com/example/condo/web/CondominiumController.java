package com.example.condo.web;

import com.example.condo.entity.Condominium;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping({ "/condominiums", "/api/condominiums" })
public class CondominiumController {

  private final CondominiumRepository repo;
  private final UnitRepository units;
  private final ResidentRepository residents;

  public CondominiumController(CondominiumRepository repo, UnitRepository units, ResidentRepository residents) {
    this.repo = repo;
    this.units = units;
    this.residents = residents;
  }

  // ===== DTOs =====
  public record ErrorDTO(String error) {
    public static ErrorDTO of(String m) {
      return new ErrorDTO(m);
    }
  }

  public record CondoDTO(Long id, String name, String cnpj,
      Instant createdAt,
      Counts _count) {
    public record Counts(long units, long residents) {
    }
  }

  public record PageResp<T>(List<T> items, long total, int page, int pageSize) {
  }

  public record NewCondoReq(String name, String cnpj) {
  }

  public record UpdateCondoReq(String name, String cnpj) {
  }

  // ===== LIST (com contadores) =====
  @GetMapping
  public PageResp<CondoDTO> list(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    String tenant = TenantContext.get();
    Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "created_at"));

    Page<Object[]> p = repo.pageWithCounts(tenant, pageable);

    List<CondoDTO> items = p.getContent().stream().map(row -> {
      Long id = ((Number) row[0]).longValue();
      String name = (String) row[1];
      String cnpj = (String) row[2];
      Instant createdAt = ((java.sql.Timestamp) row[3]).toInstant();
      long units = ((Number) row[4]).longValue();
      long residents = ((Number) row[5]).longValue();
      return new CondoDTO(id, name, cnpj, createdAt, new CondoDTO.Counts(units, residents));
    }).toList();

    return new PageResp<>(items, p.getTotalElements(), p.getNumber() + 1, p.getSize());
  }

  // detalhe do condomínio (com contadores)
  @GetMapping("/{id}")
  public ResponseEntity<?> getOne(@PathVariable Long id) {
    String tenant = TenantContext.get();
    var opt = repo.findByTenantIdAndId(tenant, id);
    if (opt.isEmpty())
      return ResponseEntity.status(404).body(ErrorDTO.of("Condomínio não encontrado"));

    var c = opt.get();
    long u = units.countByTenantIdAndCondominiumId(tenant, c.getId());
    long r = residents.countByTenantIdAndCondominiumId(tenant, c.getId());

    return ResponseEntity.ok(
        java.util.Map.of(
            "id", c.getId(),
            "name", c.getName(),
            "cnpj", c.getCnpj(),
            "_count", java.util.Map.of("units", u, "residents", r)));
  }

  // ===== CREATE =====
  @PostMapping
  public ResponseEntity<?> create(@RequestBody NewCondoReq req) {
    String tenant = TenantContext.get();
    if (req.name() == null || req.name().isBlank()) {
      return ResponseEntity.badRequest().body(ErrorDTO.of("Nome é obrigatório"));
    }
    Condominium c = new Condominium();
    c.setTenantId(tenant);
    c.setName(req.name().trim());
    c.setCnpj(req.cnpj() == null ? "" : req.cnpj().trim());
    // c.setCreatedAt(Instant.now()); // <-- REMOVIDO: sua entidade não tem esse
    // setter
    repo.save(c);
    return ResponseEntity.ok().build();
  }

  // ===== UPDATE =====
  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateCondoReq req) {
    String tenant = TenantContext.get();
    var cOpt = repo.findByTenantIdAndId(tenant, id);
    if (cOpt.isEmpty())
      return ResponseEntity.status(404).body(ErrorDTO.of("Condomínio não encontrado"));

    Condominium c = cOpt.get();
    c.setName(req.name() == null ? "" : req.name().trim());
    c.setCnpj(req.cnpj() == null ? "" : req.cnpj().trim());
    repo.save(c);
    return ResponseEntity.ok().build();
  }

  // ===== DELETE (com bloqueio se houver vínculos) =====
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    String tenant = TenantContext.get();
    var cOpt = repo.findByTenantIdAndId(tenant, id);
    if (cOpt.isEmpty())
      return ResponseEntity.status(404).body(ErrorDTO.of("Condomínio não encontrado"));

    long u = units.countByTenantIdAndCondominiumId(tenant, id);
    long r = residents.countByTenantIdAndCondominiumId(tenant, id);
    if (u > 0 || r > 0) {
      return ResponseEntity.badRequest().body(ErrorDTO.of("Exclusão bloqueada: há unidades/moradores vinculados."));
    }
    repo.delete(cOpt.get());
    return ResponseEntity.noContent().build();
  }
}