package com.example.condo.web;

import com.example.condo.entity.Condominium;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class CondominiumController {

  private final CondominiumRepository repo;

  public CondominiumController(CondominiumRepository repo) {
    this.repo = repo;
  }

  // GET /condominiums  e  /api/condominiums
  @GetMapping({"/condominiums", "/api/condominiums"})
  public Page<Condominium> list(
      @RequestParam(defaultValue = "1") int page,                  // front manda 1-based
      @RequestParam(required = false) Integer size,
      @RequestParam(name = "pageSize", required = false) Integer pageSize
  ) {
    // aceita page 1-based (1=>0, 2=>1, …), e protege contra negativos
    int zeroBased = Math.max(0, page - 1);

    int pageSizeFinal = (size != null) ? size : (pageSize != null ? pageSize : 20);
    Pageable pageable = PageRequest.of(zeroBased, pageSizeFinal, Sort.by("id").descending());

    String tenant = TenantContext.get();
    return repo.findByTenantId(tenant, pageable);
  }

  // GET /condominiums/{id}  e  /api/condominiums/{id}
  @GetMapping({"/condominiums/{id}", "/api/condominiums/{id}"})
  public ResponseEntity<Condominium> getOne(@PathVariable Long id) {
    String tenant = TenantContext.get();
    return repo.findByTenantIdAndId(tenant, id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}