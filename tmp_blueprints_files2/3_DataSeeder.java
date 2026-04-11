package com.condohub.config;

import com.condohub.condominium.entity.Condominium;
import com.condohub.condominium.repository.CondominiumRepository;
import com.condohub.financial.entity.FinancialConfig;
import com.condohub.financial.repository.FinancialConfigRepository;
import com.condohub.resident.entity.Resident;
import com.condohub.resident.repository.ResidentRepository;
import com.condohub.unit.entity.Unit;
import com.condohub.unit.repository.UnitRepository;
import com.condohub.user.entity.User;
import com.condohub.user.enums.Role;
import com.condohub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seed de desenvolvimento — popula banco com dados realistas para demo e testes.
 *
 * Ativado SOMENTE com spring.profiles.active=dev
 * Idempotente: verifica existência antes de criar.
 *
 * Credenciais criadas:
 *   superadmin@condohub.com   / SuperAdmin@2026  → SUPERUSER
 *   admin@bossanova.com       / Admin@2026        → ADMIN      (Bossa Nova)
 *   sindico@bossanova.com     / Sindico@2026      → SINDICO    (Bossa Nova, Apto 101-A)
 *   financeiro@bossanova.com  / Financeiro@2026   → FINANCEIRO (Bossa Nova)
 *   operador@bossanova.com    / Operador@2026     → OPERADOR   (Bossa Nova)
 *   zelador@bossanova.com     / Zelador@2026      → ZELADOR    (Bossa Nova, Apto 201-A)
 *   portaria@bossanova.com    / Portaria@2026     → PORTARIA   (Bossa Nova)
 *   morador@bossanova.com     / Morador@2026      → MORADOR    (Bossa Nova, Apto 102-A)
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CondominiumRepository condominiumRepository;
    private final UnitRepository unitRepository;
    private final ResidentRepository residentRepository;
    private final UserRepository userRepository;
    private final FinancialConfigRepository financialConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail("superadmin@condohub.com")) {
            log.info("[DataSeeder] Dados de dev já existem — pulando seed.");
            return;
        }

        log.info("[DataSeeder] Iniciando seed de desenvolvimento...");

        // ── SUPERUSER ───────────────────────────────────────────────
        createUser(null, "Super Admin", "superadmin@condohub.com",
                "SuperAdmin@2026", Role.SUPERUSER, null, null);

        // ── CONDOMÍNIO BOSSA NOVA ───────────────────────────────────
        Condominium bossaNova = createCondominium("Bossa Nova",
                "Rua das Palmeiras, 100 — Santos, SP");

        // ── UNIDADES (Bloco A e Bloco T) ────────────────────────────
        Unit u101A = createUnit(bossaNova, "101", "A");
        Unit u102A = createUnit(bossaNova, "102", "A");
        Unit u201A = createUnit(bossaNova, "201", "A");
        Unit u202A = createUnit(bossaNova, "202", "A");
        Unit u301A = createUnit(bossaNova, "301", "A");
        Unit uT1   = createUnit(bossaNova, "1",   "T1");
        Unit uT2   = createUnit(bossaNova, "2",   "T1");
        Unit u242A = createUnit(bossaNova, "242", "A");

        // ── MORADORES DEMO ──────────────────────────────────────────
        createResident(bossaNova, u101A, "Gilberto Lima",       "gilberto@email.com", "(13) 99001-0001");
        createResident(bossaNova, u102A, "Maria Fernanda",      "maria@email.com",    "(13) 99001-0002");
        createResident(bossaNova, u201A, "Carlos Zelador",      "carlos@email.com",   "(13) 99001-0003");
        createResident(bossaNova, u202A, "Ana Oliveira",        "ana@email.com",      "(13) 99001-0004");
        createResident(bossaNova, u301A, "Paulo Santos",        "paulo@email.com",    "(13) 99001-0005");
        createResident(bossaNova, u242A, "Fernando Diniz",      "fernando@email.com", "(13) 99001-0006");

        // ── USUÁRIOS POR ROLE ───────────────────────────────────────
        createUser(bossaNova, "Admin Bossa Nova",    "admin@bossanova.com",
                "Admin@2026",      Role.ADMIN,      null,  null);

        createUser(bossaNova, "Gilberto Lima",       "sindico@bossanova.com",
                "Sindico@2026",    Role.SINDICO,    u101A, "101-A");

        createUser(bossaNova, "Financeiro BN",       "financeiro@bossanova.com",
                "Financeiro@2026", Role.FINANCEIRO, null,  null);

        createUser(bossaNova, "Operador BN",         "operador@bossanova.com",
                "Operador@2026",   Role.OPERADOR,   null,  null);

        createUser(bossaNova, "Carlos Zelador",      "zelador@bossanova.com",
                "Zelador@2026",    Role.ZELADOR,    u201A, "201-A");

        createUser(bossaNova, "Portaria BN",         "portaria@bossanova.com",
                "Portaria@2026",   Role.PORTARIA,   null,  null);

        createUser(bossaNova, "Maria Fernanda",      "morador@bossanova.com",
                "Morador@2026",    Role.MORADOR,    u102A, "102-A");

        // ── FINANCIAL CONFIG ────────────────────────────────────────
        createFinancialConfig(bossaNova);

        log.info("[DataSeeder] Seed concluído — 8 usuários criados para condomínio '{}'.", bossaNova.getName());
        log.info("[DataSeeder] Credenciais disponíveis em: README.md ou comentário do DataSeeder.");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Condominium createCondominium(String name, String address) {
        return condominiumRepository.findByName(name).orElseGet(() -> {
            Condominium c = new Condominium();
            c.setName(name);
            c.setAddress(address);
            c.setActive(true);
            return condominiumRepository.save(c);
        });
    }

    private Unit createUnit(Condominium condo, String number, String block) {
        return unitRepository
                .findByCondominiumIdAndNumberAndBlock(condo.getId(), number, block)
                .orElseGet(() -> {
                    Unit u = new Unit();
                    u.setCondominium(condo);
                    u.setNumber(number);
                    u.setBlock(block);
                    return unitRepository.save(u);
                });
    }

    private void createResident(Condominium condo, Unit unit, String name, String email, String phone) {
        if (residentRepository.existsByEmail(email)) return;
        Resident r = new Resident();
        r.setCondominium(condo);
        r.setUnit(unit);
        r.setName(name);
        r.setEmail(email);
        r.setPhone(phone);
        residentRepository.save(r);
    }

    private void createUser(Condominium condo, String name, String email,
                             String password, Role role, Unit unit, String unitDisplay) {
        if (userRepository.existsByEmail(email)) return;
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        u.setCondominium(condo);
        u.setUnit(unit);
        u.setUnitDisplay(unitDisplay);
        u.setActive(true);
        userRepository.save(u);
    }

    private void createFinancialConfig(Condominium condo) {
        if (financialConfigRepository.findByCondominiumId(condo.getId()).isPresent()) return;
        FinancialConfig fc = new FinancialConfig();
        fc.setCondominium(condo);
        fc.setMonthlyFee(new BigDecimal("620.00"));
        fc.setDueDay(12);
        fc.setLateFeePercent(new BigDecimal("2.0"));
        fc.setMonthlyInterestPercent(new BigDecimal("1.0"));
        fc.setPixKey("admin@bossanova.com");
        fc.setPixKeyType("EMAIL");
        fc.setDefaultPaymentMethod("BOLETO");
        fc.setEmailNotificationsEnabled(true);
        fc.setAsaasEnabled(false);
        financialConfigRepository.save(fc);
    }
}
