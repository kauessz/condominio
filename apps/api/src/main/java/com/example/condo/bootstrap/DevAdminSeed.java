package com.example.condo.bootstrap;

import com.example.condo.entity.Unit;
import com.example.condo.entity.User;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed de desenvolvimento: cria usuários padrão para testes se não existirem.
 * Apenas ativo nos profiles "dev" e "test".
 *
 * Usuários criados (tenant=demo, Condo Demo):
 *
 *   superadmin@condohub.com / SuperAdmin@2026 → SUPERUSER (sem condomínio)
 *   admin@condodemo.com     / Admin@2026      → ADMIN     (Condo Demo)
 *   sindico@condodemo.com   / Sindico@2026    → SINDICO   (Condo Demo, unit 101A)
 *   zelador@condodemo.com   / Zelador@2026    → ZELADOR   (Condo Demo)
 *   portaria@condodemo.com  / Portaria@2026   → PORTARIA  (Condo Demo)
 *   morador@condodemo.com   / Morador@2026    → MORADOR   (Condo Demo, unit 101B)
 */
@Component
@Profile({"dev", "test"})
@Order(20)
public class DevAdminSeed implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CondominiumRepository condos;
    private final UnitRepository units;

    public DevAdminSeed(
        UserRepository users,
        PasswordEncoder encoder,
        CondominiumRepository condos,
        UnitRepository units
    ) {
        this.users = users;
        this.encoder = encoder;
        this.condos = condos;
        this.units = units;
    }

    @Override
    public void run(String... args) {
        seedSuperuser();
        seedCondoUsers();
    }

    // ===== SUPERUSER =====

    private void seedSuperuser() {
        upsertUser("demo", "superadmin@condohub.com", "SuperAdmin@2026",
            Role.SUPERUSER, "Super Admin", null, null);
    }

    // ===== Usuários do "Condo Demo" =====

    private void seedCondoUsers() {
        // Resolve o primeiro condomínio do tenant demo
        Long firstCondoId = condos.pageWithCounts("demo", PageRequest.of(0, 1))
            .getContent()
            .stream()
            .findFirst()
            .map(row -> ((Number) row[0]).longValue())
            .orElse(null);

        if (firstCondoId == null) {
            System.out.println("[seed] Nenhum condomínio encontrado no tenant 'demo'. Pulando seed de usuários do condo.");
            return;
        }

        // Unidade 101A para SINDICO
        Long unit101A = resolveOrCreateUnit(firstCondoId, "101", "A");
        // Unidade 101B para MORADOR
        Long unit101B = resolveOrCreateUnit(firstCondoId, "101", "B");

        upsertUser("demo", "admin@condodemo.com",    "Admin@2026",    Role.ADMIN,    "Admin Condo Demo",   firstCondoId, null);
        upsertUser("demo", "sindico@condodemo.com",  "Sindico@2026",  Role.SINDICO,  "Síndico Demo",       firstCondoId, unit101A);
        upsertUser("demo", "financeiro@condodemo.com","Financeiro@2026", Role.FINANCEIRO, "Financeiro Demo", firstCondoId, null);
        upsertUser("demo", "operador@condodemo.com", "Operador@2026", Role.OPERADOR, "Operador Demo",      firstCondoId, null);
        upsertUser("demo", "zelador@condodemo.com",  "Zelador@2026",  Role.ZELADOR,  "Zelador Demo",       firstCondoId, null);
        upsertUser("demo", "portaria@condohub.com",  "Portaria@2026", Role.PORTARIA, "Portaria Central",   firstCondoId, null);
        upsertUser("demo", "morador@condodemo.com",  "Morador@2026",  Role.MORADOR,  "João Morador",       firstCondoId, unit101B);
    }

    // ===== Helpers =====

    private void upsertUser(
        String tenantId,
        String email,
        String rawPassword,
        Role role,
        String name,
        Long condominiumId,
        Long unitId
    ) {
        users.findByTenantAndEmail(tenantId, email).ifPresentOrElse(
            u -> {
                boolean changed = false;
                if (u.getRole() != role) { u.setRole(role); changed = true; }
                if (!objectsEqual(u.getCondominiumId(), condominiumId)) {
                    u.setCondominiumId(condominiumId); changed = true;
                }
                if (!objectsEqual(u.getUnitId(), unitId)) {
                    u.setUnitId(unitId); changed = true;
                }
                if (changed) {
                    users.save(u);
                    System.out.printf("[seed] Usuário atualizado: %s (%s)%n", email, role);
                }
            },
            () -> {
                User u = new User();
                u.setTenantId(tenantId);
                u.setEmail(email);
                u.setPasswordHash(encoder.encode(rawPassword));
                u.setRole(role);
                u.setName(name);
                u.setCondominiumId(condominiumId);
                u.setUnitId(unitId);
                users.save(u);
                System.out.printf("[seed] Usuário criado: %s (role=%s, condoId=%s, unitId=%s)%n",
                    email, role, condominiumId, unitId);
            }
        );
    }

    /**
     * Resolve ou cria uma unidade pelo number+block no condomínio informado.
     * Retorna o id da unidade encontrada/criada, ou null em caso de erro.
     */
    private Long resolveOrCreateUnit(Long condominiumId, String number, String block) {
        try {
            // Tenta localizar pelo número+bloco
            var page = units.searchWithCount("demo", condominiumId, number, PageRequest.of(0, 10));
            var opt = page.getContent().stream()
                .filter(u -> number.equalsIgnoreCase(u.getNumber())
                    && block.equalsIgnoreCase(u.getBlock() != null ? u.getBlock() : ""))
                .findFirst();

            if (opt.isPresent()) return opt.get().getId();

            // Cria a unidade
            Unit u = new Unit();
            u.setTenantId("demo");
            u.setCondominiumId(condominiumId);
            u.setNumber(number);
            u.setBlock(block);
            u.setCode(block + "-" + number);
            units.save(u);
            System.out.printf("[seed] Unidade criada: %s-%s (condoId=%s)%n", block, number, condominiumId);
            return u.getId();
        } catch (Exception e) {
            System.out.printf("[seed] Erro ao resolver unidade %s-%s: %s%n", block, number, e.getMessage());
            return null;
        }
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
