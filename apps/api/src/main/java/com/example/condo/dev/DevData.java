package com.example.condo.dev;

import com.example.condo.entity.Condominium;
import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("dev")
public class DevData implements CommandLineRunner {

  private final CondominiumRepository condos;
  private final UnitRepository units;
  private final ResidentRepository residents;

  public DevData(CondominiumRepository condos, UnitRepository units, ResidentRepository residents) {
    this.condos = condos;
    this.units = units;
    this.residents = residents;
  }

  private static String norm(String v) {
    return v == null ? "" : v.trim();
  }

  private static String buildCode(String number, String block) {
    String n = norm(number);
    String b = norm(block);
    if (b.isEmpty()) return n;
    return b.replaceAll("\\s+", "").toUpperCase(Locale.ROOT) + "-" + n;
  }

  @Override
  public void run(String... args) {
    final String tenant = "demo";

    // Condo Demo
    Condominium c1 = condos.findByTenantIdAndName(tenant, "Condo Demo")
        .orElseGet(() -> {
          Condominium c = new Condominium();
          c.setTenantId(tenant);
          c.setName("Condo Demo");
          c.setCnpj("11222333000181"); // sem máscara
          return condos.save(c);
        });

    // Bossa Nova (não populamos unidades/moradores para ficar com 0)
    @SuppressWarnings("unused")
    Condominium c2 = condos.findByTenantIdAndName(tenant, "Bossa Nova")
        .orElseGet(() -> {
          Condominium c = new Condominium();
          c.setTenantId(tenant);
          c.setName("Bossa Nova");
          c.setCnpj("31041096000160"); // sem máscara
          return condos.save(c);
        });

    // Popula dados do Condo Demo se estiver vazio
    if (units.countByTenantIdAndCondominiumId(tenant, c1.getId()) == 0) {
      Unit u101 = new Unit();
      u101.setTenantId(tenant);
      u101.setCondominiumId(c1.getId());
      u101.setNumber("101");
      u101.setBlock("A");
      u101.setCode(buildCode(u101.getNumber(), u101.getBlock()));
      u101 = units.save(u101);

      Unit u202 = new Unit();
      u202.setTenantId(tenant);
      u202.setCondominiumId(c1.getId());
      u202.setNumber("202");
      u202.setBlock("B");
      u202.setCode(buildCode(u202.getNumber(), u202.getBlock()));
      u202 = units.save(u202);

      Resident r1 = new Resident();
      r1.setTenantId(tenant);
      r1.setCondominiumId(c1.getId());
      r1.setUnitId(u101.getId());
      r1.setName("Gilberto Lima");
      r1.setEmail("giba@gmail.com");
      r1.setPhone("1399908122");
      residents.save(r1);

      Resident r2 = new Resident();
      r2.setTenantId(tenant);
      r2.setCondominiumId(c1.getId());
      r2.setUnitId(u202.getId());
      r2.setName("Macileide Pereira");
      r2.setEmail("mahoab@gmail.com");
      r2.setPhone("13988056914");
      residents.save(r2);
    }
  }
}