package com.example.condo.dev;

import com.example.condo.entity.Condominium;
import com.example.condo.entity.Resident;
import com.example.condo.entity.Unit;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.repo.UnitRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile({"dev", "test"})
@Order(10)
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

    Condominium condoDemo = upsertCondominium(tenant, "Condo Demo", "11222333000181",
        Condominium.ParkingPolicyMode.DRAW, Condominium.ParkingDrawFrequency.QUARTERLY, null);
    Condominium bossaNova = upsertCondominium(tenant, "Bossa Nova", "31041096000160",
        Condominium.ParkingPolicyMode.FIXED, Condominium.ParkingDrawFrequency.CUSTOM, 12);
    Condominium parqueCentral = upsertCondominium(tenant, "Parque Central", "45098765000144",
        Condominium.ParkingPolicyMode.DRAW, Condominium.ParkingDrawFrequency.MONTHLY, null);

    seedCondoUnitsAndResidents(tenant, condoDemo, new String[][]{
        {"101", "A", "Gilberto Lima", "giba@gmail.com", "1399908122"},
        {"101", "B", "Macileide Pereira", "mahoab@gmail.com", "13988056914"},
        {"202", "B", "Carla Souza", "carla@condodemo.com", "13988990011"}
    });

    seedCondoUnitsAndResidents(tenant, bossaNova, new String[][]{
        {"11", "T1", "Rafaela Prado", "rafaela@bossanova.com", "11990000001"},
        {"12", "T1", "Bruno Mello", "bruno@bossanova.com", "11990000002"}
    });

    seedCondoUnitsAndResidents(tenant, parqueCentral, new String[][]{
        {"301", "C", "Amanda Lopes", "amanda@parquecentral.com", "21990000001"},
        {"302", "C", "Diego Rocha", "diego@parquecentral.com", "21990000002"}
    });
  }

  private Condominium upsertCondominium(
      String tenant,
      String name,
      String cnpj,
      Condominium.ParkingPolicyMode parkingPolicyMode,
      Condominium.ParkingDrawFrequency parkingDrawFrequency,
      Integer drawIntervalMonths
  ) {
    Condominium condominium = condos.findByTenantIdAndName(tenant, name)
        .orElseGet(() -> {
          Condominium c = new Condominium();
          c.setTenantId(tenant);
          c.setName(name);
          c.setCnpj(cnpj);
          return c;
        });

    condominium.setParkingPolicyMode(parkingPolicyMode);
    condominium.setParkingDrawFrequency(parkingDrawFrequency);
    condominium.setDrawIntervalMonths(drawIntervalMonths);
    condominium.setAllowManualAssignments(true);
    condominium.setAllowResidentRegistration(parkingPolicyMode == Condominium.ParkingPolicyMode.DRAW);
    condominium.setMaxVehiclesPerUnit(1);
    condominium.setParkingRules(parkingPolicyMode == Condominium.ParkingPolicyMode.FIXED
        ? "Vagas fixas definidas pela administracao do condominio."
        : "Inscricoes abertas conforme politica vigente do condominio.");

    return condos.save(condominium);
  }

  private void seedCondoUnitsAndResidents(String tenant, Condominium condominium, String[][] fixtures) {
    for (String[] fixture : fixtures) {
      Unit unit = ensureUnit(tenant, condominium.getId(), fixture[0], fixture[1]);
      ensureResident(tenant, condominium.getId(), unit.getId(), fixture[2], fixture[3], fixture[4]);
    }
  }

  private Unit ensureUnit(String tenant, Long condominiumId, String number, String block) {
    var existing = units.searchWithCount(tenant, condominiumId, number, org.springframework.data.domain.PageRequest.of(0, 20))
        .getContent()
        .stream()
        .filter(u -> number.equalsIgnoreCase(u.getNumber()) && block.equalsIgnoreCase(u.getBlock() == null ? "" : u.getBlock()))
        .findFirst();

    if (existing.isPresent()) {
      return units.findByTenantIdAndId(tenant, existing.get().getId()).orElseThrow();
    }

    Unit unit = new Unit();
    unit.setTenantId(tenant);
    unit.setCondominiumId(condominiumId);
    unit.setNumber(number);
    unit.setBlock(block);
    unit.setCode(buildCode(number, block));
    return units.save(unit);
  }

  private void ensureResident(String tenant, Long condominiumId, Long unitId, String name, String email, String phone) {
    boolean exists = residents.searchWithUnit(tenant, condominiumId, null, email, org.springframework.data.domain.PageRequest.of(0, 20))
        .getContent()
        .stream()
        .map(row -> (Resident) row[0])
        .anyMatch(r -> email.equalsIgnoreCase(r.getEmail()));

    if (exists) {
      return;
    }

    Resident resident = new Resident();
    resident.setTenantId(tenant);
    resident.setCondominiumId(condominiumId);
    resident.setUnitId(unitId);
    resident.setName(name);
    resident.setEmail(email);
    resident.setPhone(phone);
    residents.save(resident);
  }
}
