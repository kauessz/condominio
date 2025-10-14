package com.example.condo.web;

import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.VisitorRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/condominiums", "/api/condominiums"})
public class CondoCountersController {

  private final UnitRepository units;
  private final ResidentRepository residents;
  private final VisitorRepository visitors;

  public CondoCountersController(UnitRepository units,
                                 ResidentRepository residents,
                                 VisitorRepository visitors) {
    this.units = units;
    this.residents = residents;
    this.visitors = visitors;
  }

  @GetMapping("/{id}/counters")
  public Map<String, Long> counters(@PathVariable("id") Long condoId,
                                    @RequestHeader(value = "X-Tenant", required = false) String tenantHeader) {
    final String tenant = (tenantHeader == null || tenantHeader.isBlank())
        ? TenantContext.get() : tenantHeader.trim();

    long unitCount       = units.countByTenantIdAndCondominiumId(tenant, condoId);
    long residentCount   = residents.countByTenantIdAndCondominiumId(tenant, condoId);
    long pendingVisitors = visitors.countPendingByCondo(tenant, condoId);

    return Map.of(
        "units", unitCount,
        "residents", residentCount,
        "pendingVisitors", pendingVisitors
    );
  }
}