package com.example.condo.dev;

import com.example.condo.entity.*;
import com.example.condo.repo.*;
import com.example.condo.security.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Profile({"dev", "test"})
@Order(30)
public class DevModuleSeed implements CommandLineRunner {

    private static final String TENANT = "demo";

    private final CondominiumRepository condominiums;
    private final UnitRepository units;
    private final UserRepository users;
    private final ResidentRepository residents;
    private final VisitorRepository visitors;
    private final CommonAreaRepository commonAreas;
    private final ReservationRepository reservations;
    private final WorkOrderRepository workOrders;
    private final WorkOrderCategoryRepository workOrderCategories;
    private final WorkOrderSubcategoryRepository workOrderSubcategories;
    private final WorkOrderUpdateRepository workOrderUpdates;
    private final ParkingSpotRepository parkingSpots;
    private final ParkingDrawRepository parkingDraws;
    private final ParkingDrawRegistrationRepository parkingRegistrations;
    private final ParkingSpotAssignmentRepository parkingAssignments;
    private final AssemblyRepository assemblies;
    private final AssemblyAgendaItemRepository assemblyAgendaItems;
    private final AssemblyVoteRepository assemblyVotes;
    private final FinancialConfigRepository financialConfigs;
    private final InvoiceRepository invoices;
    private final PasswordEncoder passwordEncoder;

    public DevModuleSeed(
        CondominiumRepository condominiums,
        UnitRepository units,
        UserRepository users,
        ResidentRepository residents,
        VisitorRepository visitors,
        CommonAreaRepository commonAreas,
        ReservationRepository reservations,
        WorkOrderRepository workOrders,
        WorkOrderCategoryRepository workOrderCategories,
        WorkOrderSubcategoryRepository workOrderSubcategories,
        WorkOrderUpdateRepository workOrderUpdates,
        ParkingSpotRepository parkingSpots,
        ParkingDrawRepository parkingDraws,
        ParkingDrawRegistrationRepository parkingRegistrations,
        ParkingSpotAssignmentRepository parkingAssignments,
        AssemblyRepository assemblies,
        AssemblyAgendaItemRepository assemblyAgendaItems,
        AssemblyVoteRepository assemblyVotes,
        FinancialConfigRepository financialConfigs,
        InvoiceRepository invoices,
        PasswordEncoder passwordEncoder
    ) {
        this.condominiums = condominiums;
        this.units = units;
        this.users = users;
        this.residents = residents;
        this.visitors = visitors;
        this.commonAreas = commonAreas;
        this.reservations = reservations;
        this.workOrders = workOrders;
        this.workOrderCategories = workOrderCategories;
        this.workOrderSubcategories = workOrderSubcategories;
        this.workOrderUpdates = workOrderUpdates;
        this.parkingSpots = parkingSpots;
        this.parkingDraws = parkingDraws;
        this.parkingRegistrations = parkingRegistrations;
        this.parkingAssignments = parkingAssignments;
        this.assemblies = assemblies;
        this.assemblyAgendaItems = assemblyAgendaItems;
        this.assemblyVotes = assemblyVotes;
        this.financialConfigs = financialConfigs;
        this.invoices = invoices;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Condominium condoDemo = getCondominium("Condo Demo");
        Condominium bossaNova = getCondominium("Bossa Nova");
        Condominium parqueCentral = getCondominium("Parque Central");

        seedSupportUsers(condoDemo, bossaNova, parqueCentral);
        seedVisitors(condoDemo);
        seedReservations(condoDemo);
        seedWorkOrders(condoDemo);
        seedParking(condoDemo, bossaNova, parqueCentral);
        seedAssemblies(condoDemo);
        seedFinancial(condoDemo, bossaNova, parqueCentral);
    }

    private Condominium getCondominium(String name) {
        return condominiums.findByTenantIdAndName(TENANT, name).orElseGet(() -> {
            Condominium condominium = new Condominium();
            condominium.setTenantId(TENANT);
            condominium.setName(name);
            condominium.setCnpj("00000000000000");
            condominium.setParkingPolicyMode("Bossa Nova".equals(name)
                ? Condominium.ParkingPolicyMode.FIXED
                : Condominium.ParkingPolicyMode.DRAW);
            condominium.setParkingDrawFrequency("Parque Central".equals(name)
                ? Condominium.ParkingDrawFrequency.MONTHLY
                : Condominium.ParkingDrawFrequency.QUARTERLY);
            condominium.setAllowManualAssignments(true);
            condominium.setAllowResidentRegistration(!"Bossa Nova".equals(name));
            condominium.setMaxVehiclesPerUnit(1);
            return condominiums.save(condominium);
        });
    }

    private void seedSupportUsers(Condominium condoDemo, Condominium bossaNova, Condominium parqueCentral) {
        upsertUser("superadmin@condohub.com", "Super Admin", Role.SUPERUSER, null, null, "SuperAdmin@2026");
        upsertUser("admin@condodemo.com", "Admin Condo Demo", Role.ADMIN, condoDemo.getId(), null, "Admin@2026");
        upsertUser("sindico@condodemo.com", "Sindico Demo", Role.SINDICO, condoDemo.getId(), getUnitId(condoDemo.getId(), "101", "A"), "Sindico@2026");
        upsertUser("financeiro@condodemo.com", "Financeiro Demo", Role.FINANCEIRO, condoDemo.getId(), null, "Financeiro@2026");
        upsertUser("operador@condodemo.com", "Operador Demo", Role.OPERADOR, condoDemo.getId(), null, "Operador@2026");
        upsertUser("zelador@condodemo.com", "Zelador Demo", Role.ZELADOR, condoDemo.getId(), null, "Zelador@2026");
        upsertUser("portaria@condohub.com", "Portaria Central", Role.PORTARIA, condoDemo.getId(), null, "Portaria@2026");
        upsertUser("morador@condodemo.com", "Joao Morador", Role.MORADOR, condoDemo.getId(), getUnitId(condoDemo.getId(), "101", "B"), "Morador@2026");
        upsertUser("admin@bossanova.com", "Admin Bossa Nova", Role.ADMIN, bossaNova.getId(), null, "Admin@2026");
        upsertUser("sindico@parquecentral.com", "Sindico Parque Central", Role.SINDICO, parqueCentral.getId(), getUnitId(parqueCentral.getId(), "301", "C"), "Sindico@2026");
    }

    private void upsertUser(String email, String name, Role role, Long condominiumId, Long unitId, String password) {
        users.findByTenantAndEmail(TENANT, email).ifPresentOrElse(existing -> {
            existing.setName(name);
            existing.setRole(role);
            existing.setCondominiumId(condominiumId);
            existing.setUnitId(unitId);
            users.save(existing);
        }, () -> {
            User user = new User();
            user.setTenantId(TENANT);
            user.setEmail(email);
            user.setName(name);
            user.setRole(role);
            user.setCondominiumId(condominiumId);
            user.setUnitId(unitId);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setMustChangePassword(false);
            users.save(user);
        });
    }

    private void seedVisitors(Condominium condominium) {
        ensureVisitor(condominium.getId(), "Carlos Entrega", Visitor.Type.DELIVERY, Visitor.Status.CHECKED_IN, "Entrega de farmacia");
        ensureVisitor(condominium.getId(), "Fernanda Silva", Visitor.Type.VISITOR, Visitor.Status.APPROVED, "Visita liberada para o fim de semana");
    }

    private void ensureVisitor(Long condoId, String name, Visitor.Type type, Visitor.Status status, String note) {
        boolean exists = visitors.findAll().stream()
            .anyMatch(v -> TENANT.equals(v.getTenantId()) && condoId.equals(v.getCondominiumId()) && name.equalsIgnoreCase(v.getName()));
        if (exists) {
            return;
        }

        Visitor visitor = new Visitor();
        visitor.setTenantId(TENANT);
        visitor.setCondominiumId(condoId);
        visitor.setUnitId(getUnitId(condoId, "101", "B"));
        visitor.setName(name);
        visitor.setType(type);
        visitor.setStatus(status);
        visitor.setNote(note);
        visitor.setPhone("11999990000");
        visitor.setDocument("DOC-" + slug(name));
        visitor.setExpectedInAt(Instant.now().minusSeconds(7200));
        visitor.setExpectedOutAt(Instant.now().plusSeconds(7200));
        visitor.setCheckInAt(Instant.now().minusSeconds(1800));
        if (status == Visitor.Status.CHECKED_OUT) {
            visitor.setCheckOutAt(Instant.now().minusSeconds(900));
        }
        if (status == Visitor.Status.APPROVED || status == Visitor.Status.CHECKED_IN || status == Visitor.Status.CHECKED_OUT) {
            visitor.setApprovedAt(Instant.now().minusSeconds(3600));
            visitor.setApprovedBy("seed");
        }
        visitors.save(visitor);
    }

    private void seedReservations(Condominium condominium) {
        CommonArea churrasqueira = ensureCommonArea(condominium.getId(), "Churrasqueira Gourmet", 25, false);
        CommonArea salao = ensureCommonArea(condominium.getId(), "Salao de Festas", 60, true);

        Long residentId = getResidentId(condominium.getId(), "mahoab@gmail.com");
        Long residentUnitId = getUnitId(condominium.getId(), "101", "B");
        Long approverId = getUserId("admin@condodemo.com");

        ensureReservation(condominium.getId(), churrasqueira.getId(), residentUnitId, residentId,
            "Aniversario infantil", Reservation.Status.APPROVED,
            Instant.now().plusSeconds(86400), Instant.now().plusSeconds(93600), approverId);

        ensureReservation(condominium.getId(), salao.getId(), residentUnitId, residentId,
            "Reuniao de familia", Reservation.Status.PENDING,
            Instant.now().plusSeconds(172800), Instant.now().plusSeconds(180000), null);
    }

    private CommonArea ensureCommonArea(Long condoId, String name, int capacity, boolean requiresApproval) {
        Optional<CommonArea> existing = commonAreas.findAll().stream()
            .filter(a -> TENANT.equals(a.getTenantId()) && condoId.equals(a.getCondominiumId()) && name.equalsIgnoreCase(a.getName()))
            .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        CommonArea area = new CommonArea();
        area.setTenantId(TENANT);
        area.setCondominiumId(condoId);
        area.setName(name);
        area.setCapacity(capacity);
        area.setRequiresApproval(requiresApproval);
        area.setRules("Seed dev/test");
        area.setCreatedAt(Instant.now());
        return commonAreas.save(area);
    }

    private void ensureReservation(Long condoId, Long areaId, Long unitId, Long residentId,
                                   String title, Reservation.Status status,
                                   Instant start, Instant end, Long approvedBy) {
        boolean exists = reservations.findAll().stream()
            .anyMatch(r -> TENANT.equals(r.getTenantId()) && condoId.equals(r.getCondominiumId()) && title.equalsIgnoreCase(r.getTitle()));
        if (exists) {
            return;
        }

        Reservation reservation = new Reservation();
        reservation.setTenantId(TENANT);
        reservation.setCondominiumId(condoId);
        reservation.setCommonAreaId(areaId);
        reservation.setUnitId(unitId);
        reservation.setResidentId(residentId);
        reservation.setTitle(title);
        reservation.setNotes("Reserva criada automaticamente para validacao de tela.");
        reservation.setStartDatetime(start);
        reservation.setEndDatetime(end);
        reservation.setStatus(status);
        reservation.setCreatedBy(getUserId("morador@condodemo.com"));
        reservation.setCreatedAt(Instant.now());
        if (approvedBy != null) {
            reservation.setApprovedBy(approvedBy);
            reservation.setApprovedAt(Instant.now());
        }
        reservations.save(reservation);
    }

    private void seedWorkOrders(Condominium condominium) {
        if (workOrderCategories.findAllByOrderBySortOrderAsc().isEmpty()) {
            return;
        }

        WorkOrderCategory category = workOrderCategories.findAllByOrderBySortOrderAsc().get(0);
        WorkOrderSubcategory subcategory = workOrderSubcategories.findAll().stream()
            .filter(s -> category.getId().equals(s.getCategoryId()))
            .findFirst()
            .orElse(null);
        if (subcategory == null) {
            return;
        }

        WorkOrder order = workOrders.findAll().stream()
            .filter(w -> TENANT.equals(w.getTenantId()) && condominium.getId().equals(w.getCondominiumId()) && "Lampada queimada no corredor".equalsIgnoreCase(w.getTitle()))
            .findFirst()
            .orElseGet(() -> {
                WorkOrder created = new WorkOrder();
                created.setTenantId(TENANT);
                created.setCondominiumId(condominium.getId());
                created.setUnitId(getUnitId(condominium.getId(), "101", "A"));
                created.setCategoryId(category.getId());
                created.setSubcategoryId(subcategory.getId());
                created.setTitle("Lampada queimada no corredor");
                created.setDescription("Corredor do bloco A sem iluminacao no 1o andar.");
                created.setPriority(WorkOrder.Priority.HIGH);
                created.setStatus(WorkOrder.Status.IN_PROGRESS);
                created.setAssignedTo(getUserId("zelador@condodemo.com"));
                created.setCreatedBy(getUserId("operador@condodemo.com"));
                created.setCreatedAt(Instant.now().minusSeconds(7200));
                created.setUpdatedAt(Instant.now().minusSeconds(1800));
                created.setSlaDeadline(Instant.now().plusSeconds(subcategory.getSlaHours() * 3600L));
                return workOrders.save(created);
            });

        ensureWorkOrderUpdate(order.getId(), "Chamado aberto pelo operador", null);
        ensureWorkOrderUpdate(order.getId(), "Equipe de manutencao acionada", WorkOrder.Status.IN_PROGRESS.name());
    }

    private void ensureWorkOrderUpdate(Long workOrderId, String content, String newStatus) {
        boolean exists = workOrderUpdates.findAll().stream()
            .anyMatch(u -> workOrderId.equals(u.getWorkOrderId()) && content.equalsIgnoreCase(u.getContent()));
        if (exists) {
            return;
        }

        WorkOrderUpdate update = new WorkOrderUpdate();
        update.setWorkOrderId(workOrderId);
        update.setAuthorId(getUserId("operador@condodemo.com"));
        update.setAuthorName("Operador Demo");
        update.setContent(content);
        update.setNewStatus(newStatus);
        update.setCreatedAt(Instant.now().minusSeconds(1200));
        workOrderUpdates.save(update);
    }

    private void seedParking(Condominium condoDemo, Condominium bossaNova, Condominium parqueCentral) {
        ensureParkingSpot(condoDemo.getId(), "A-01", "Subsolo A");
        ensureParkingSpot(condoDemo.getId(), "A-02", "Subsolo A");
        ensureParkingSpot(bossaNova.getId(), "F-01", "Vaga fixa torre 1");
        ensureParkingSpot(bossaNova.getId(), "F-02", "Vaga fixa torre 1");
        ensureParkingSpot(parqueCentral.getId(), "M-01", "Modulo central");
        ensureParkingSpot(parqueCentral.getId(), "M-02", "Modulo central");

        Long bossaUnitId = getUnitId(bossaNova.getId(), "11", "T1");
        Long fixedSpotId = getParkingSpotId(bossaNova.getId(), "F-01");
        ensureAssignment(bossaNova.getId(), fixedSpotId, bossaUnitId, null,
            LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(10));

        ParkingDraw draw = ensureDraw(parqueCentral.getId(), "Sorteio Mensal Abril",
            Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
            LocalDate.now(), LocalDate.now().plusMonths(1), ParkingDraw.Status.OPEN);
        ensureRegistration(draw.getId(), getUnitId(parqueCentral.getId(), "301", "C"));
        ensureRegistration(draw.getId(), getUnitId(parqueCentral.getId(), "302", "C"));
    }

    private ParkingSpot ensureParkingSpot(Long condoId, String code, String description) {
        return parkingSpots.findAll().stream()
            .filter(s -> TENANT.equals(s.getTenantId()) && condoId.equals(s.getCondominiumId()) && code.equalsIgnoreCase(s.getCode()))
            .findFirst()
            .orElseGet(() -> {
                ParkingSpot spot = new ParkingSpot();
                spot.setTenantId(TENANT);
                spot.setCondominiumId(condoId);
                spot.setCode(code);
                spot.setDescription(description);
                spot.setActive(true);
                spot.setCreatedAt(Instant.now());
                return parkingSpots.save(spot);
            });
    }

    private ParkingDraw ensureDraw(Long condoId, String name, Instant openAt, Instant closeAt,
                                   LocalDate validFrom, LocalDate validUntil, ParkingDraw.Status status) {
        return parkingDraws.findAll().stream()
            .filter(d -> TENANT.equals(d.getTenantId()) && condoId.equals(d.getCondominiumId()) && name.equalsIgnoreCase(d.getName()))
            .findFirst()
            .orElseGet(() -> {
                ParkingDraw draw = new ParkingDraw();
                draw.setTenantId(TENANT);
                draw.setCondominiumId(condoId);
                draw.setName(name);
                draw.setRegistrationOpenAt(openAt);
                draw.setRegistrationCloseAt(closeAt);
                draw.setValidFrom(validFrom);
                draw.setValidUntil(validUntil);
                draw.setStatus(status);
                draw.setCreatedBy(getUserId("admin@condodemo.com"));
                draw.setCreatedAt(Instant.now());
                return parkingDraws.save(draw);
            });
    }

    private void ensureRegistration(Long drawId, Long unitId) {
        boolean exists = parkingRegistrations.findAll().stream()
            .anyMatch(r -> drawId.equals(r.getDrawId()) && unitId.equals(r.getUnitId()));
        if (exists) {
            return;
        }
        ParkingDrawRegistration registration = new ParkingDrawRegistration();
        registration.setDrawId(drawId);
        registration.setUnitId(unitId);
        registration.setRegisteredAt(Instant.now().minusSeconds(1800));
        parkingRegistrations.save(registration);
    }

    private void ensureAssignment(Long condoId, Long spotId, Long unitId, Long drawId, LocalDate from, LocalDate until) {
        boolean exists = parkingAssignments.findAll().stream()
            .anyMatch(a -> TENANT.equals(a.getTenantId()) && condoId.equals(a.getCondominiumId()) && spotId.equals(a.getSpotId()) && unitId.equals(a.getUnitId()) && a.getStatus() == ParkingSpotAssignment.Status.ACTIVE);
        if (exists) {
            return;
        }
        ParkingSpotAssignment assignment = new ParkingSpotAssignment();
        assignment.setTenantId(TENANT);
        assignment.setCondominiumId(condoId);
        assignment.setSpotId(spotId);
        assignment.setUnitId(unitId);
        assignment.setDrawId(drawId);
        assignment.setValidFrom(from);
        assignment.setValidUntil(until);
        assignment.setStatus(ParkingSpotAssignment.Status.ACTIVE);
        assignment.setCreatedAt(Instant.now().minusSeconds(86400));
        parkingAssignments.save(assignment);
    }

    private void seedAssemblies(Condominium condominium) {
        Assembly assembly = assemblies.findAll().stream()
            .filter(a -> TENANT.equals(a.getTenantId()) && condominium.getId().equals(a.getCondominiumId()) && "AGO Ordinaria 2026".equalsIgnoreCase(a.getTitle()))
            .findFirst()
            .orElseGet(() -> {
                Assembly created = new Assembly();
                created.setTenantId(TENANT);
                created.setCondominiumId(condominium.getId());
                created.setTitle("AGO Ordinaria 2026");
                created.setDescription("Prestacao de contas e aprovacao de melhorias.");
                created.setScheduledAt(Instant.now().plusSeconds(604800));
                created.setLocation("Salao de festas");
                created.setStatus(Assembly.Status.OPEN);
                created.setOpenedAt(Instant.now().minusSeconds(7200));
                created.setCreatedBy(getUserId("sindico@condodemo.com"));
                created.setCreatedAt(Instant.now().minusSeconds(172800));
                return assemblies.save(created);
            });

        AssemblyAgendaItem item = assemblyAgendaItems.findAll().stream()
            .filter(i -> assembly.getId().equals(i.getAssemblyId()) && "Aprovacao do orcamento anual".equalsIgnoreCase(i.getTitle()))
            .findFirst()
            .orElseGet(() -> {
                AssemblyAgendaItem agendaItem = new AssemblyAgendaItem();
                agendaItem.setAssemblyId(assembly.getId());
                agendaItem.setTitle("Aprovacao do orcamento anual");
                agendaItem.setDescription("Reajuste de 8% nas despesas ordinarias.");
                agendaItem.setRequiresVote(true);
                agendaItem.setSortOrder(1);
                return assemblyAgendaItems.save(agendaItem);
            });

        ensureVote(item.getId(), getUnitId(condominium.getId(), "101", "A"), AssemblyVote.VoteValue.YES);
        ensureVote(item.getId(), getUnitId(condominium.getId(), "101", "B"), AssemblyVote.VoteValue.NO);
    }

    private void ensureVote(Long agendaItemId, Long unitId, AssemblyVote.VoteValue voteValue) {
        boolean exists = assemblyVotes.findAll().stream()
            .anyMatch(v -> agendaItemId.equals(v.getAgendaItemId()) && unitId.equals(v.getUnitId()));
        if (exists) {
            return;
        }
        AssemblyVote vote = new AssemblyVote();
        vote.setAgendaItemId(agendaItemId);
        vote.setUnitId(unitId);
        vote.setVoteValue(voteValue);
        vote.setVotedBy(getUserId("morador@condodemo.com"));
        vote.setVotedAt(Instant.now().minusSeconds(900));
        assemblyVotes.save(vote);
    }

    private void seedFinancial(Condominium condoDemo, Condominium bossaNova, Condominium parqueCentral) {
        ensureFinancialConfig(condoDemo.getId(), new BigDecimal("450.00"), 10, "financeiro@condodemo.com");
        ensureFinancialConfig(bossaNova.getId(), new BigDecimal("620.00"), 12, "admin@bossanova.com");
        ensureFinancialConfig(parqueCentral.getId(), new BigDecimal("510.00"), 8, "sindico@parquecentral.com");

        ensureInvoice(condoDemo.getId(), getUnitId(condoDemo.getId(), "101", "A"), "2026-04", new BigDecimal("450.00"), LocalDate.now().plusDays(10), Invoice.Status.PENDING, null);
        ensureInvoice(condoDemo.getId(), getUnitId(condoDemo.getId(), "101", "B"), "2026-03", new BigDecimal("450.00"), LocalDate.now().minusDays(5), Invoice.Status.PAID, Invoice.PaymentMethod.PIX);
        ensureInvoice(bossaNova.getId(), getUnitId(bossaNova.getId(), "11", "T1"), "2026-04", new BigDecimal("620.00"), LocalDate.now().plusDays(12), Invoice.Status.PENDING, null);
        ensureInvoice(parqueCentral.getId(), getUnitId(parqueCentral.getId(), "301", "C"), "2026-03", new BigDecimal("510.00"), LocalDate.now().minusDays(15), Invoice.Status.OVERDUE, null);
    }

    private void ensureFinancialConfig(Long condoId, BigDecimal monthlyFee, int dueDay, String actorEmail) {
        Optional<FinancialConfig> existing = financialConfigs.findAll().stream()
            .filter(c -> TENANT.equals(c.getTenantId()) && condoId.equals(c.getCondominiumId()))
            .findFirst();
        FinancialConfig config = existing.orElseGet(FinancialConfig::new);
        config.setTenantId(TENANT);
        config.setCondominiumId(condoId);
        config.setMonthlyFee(monthlyFee);
        config.setDueDay(dueDay);
        config.setLateFeePct(new BigDecimal("2.00"));
        config.setInterestPct(new BigDecimal("1.00"));
        config.setPixKey(actorEmail);
        config.setPixKeyType("EMAIL");
        config.setUpdatedAt(Instant.now());
        financialConfigs.save(config);
    }

    private void ensureInvoice(Long condoId, Long unitId, String referenceMonth, BigDecimal amount,
                               LocalDate dueDate, Invoice.Status status, Invoice.PaymentMethod paymentMethod) {
        boolean exists = invoices.findAll().stream()
            .anyMatch(i -> TENANT.equals(i.getTenantId()) && condoId.equals(i.getCondominiumId())
                && unitId.equals(i.getUnitId()) && referenceMonth.equals(i.getReferenceMonth()));
        if (exists) {
            return;
        }
        Invoice invoice = new Invoice();
        invoice.setTenantId(TENANT);
        invoice.setCondominiumId(condoId);
        invoice.setUnitId(unitId);
        invoice.setReferenceMonth(referenceMonth);
        invoice.setAmount(amount);
        invoice.setDueDate(dueDate);
        invoice.setStatus(status);
        invoice.setCreatedAt(Instant.now().minusSeconds(86400));
        if (status == Invoice.Status.PAID) {
            invoice.setPaidAt(Instant.now().minusSeconds(3600));
            invoice.setPaidAmount(amount);
            invoice.setPaymentMethod(paymentMethod == null ? Invoice.PaymentMethod.PIX : paymentMethod);
            invoice.setPaymentNotes("Pagamento registrado automaticamente no seed.");
            invoice.setRegisteredBy(getUserId("financeiro@condodemo.com"));
        }
        invoices.save(invoice);
    }

    private Long getUserId(String email) {
        return users.findByTenantAndEmail(TENANT, email).map(User::getId).orElse(null);
    }

    private Long getResidentId(Long condoId, String email) {
        return residents.findAll().stream()
            .filter(r -> TENANT.equals(r.getTenantId()) && condoId.equals(r.getCondominiumId()) && email.equalsIgnoreCase(r.getEmail()))
            .map(Resident::getId)
            .findFirst()
            .orElse(null);
    }

    private Long getUnitId(Long condoId, String number, String block) {
        return units.findAll().stream()
            .filter(u -> TENANT.equals(u.getTenantId())
                && condoId.equals(u.getCondominiumId())
                && number.equalsIgnoreCase(u.getNumber())
                && block.equalsIgnoreCase(u.getBlock() == null ? "" : u.getBlock()))
            .map(Unit::getId)
            .findFirst()
            .orElseThrow();
    }

    private Long getParkingSpotId(Long condoId, String code) {
        return parkingSpots.findAll().stream()
            .filter(s -> TENANT.equals(s.getTenantId()) && condoId.equals(s.getCondominiumId()) && code.equalsIgnoreCase(s.getCode()))
            .map(ParkingSpot::getId)
            .findFirst()
            .orElseThrow();
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
