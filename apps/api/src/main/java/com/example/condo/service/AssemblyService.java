package com.example.condo.service;

import com.example.condo.dto.assembly.AssemblyListItemResponse;
import com.example.condo.entity.Assembly;
import com.example.condo.entity.AssemblyAgendaItem;
import com.example.condo.entity.AssemblyVote;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.AssemblyAgendaItemRepository;
import com.example.condo.repo.AssemblyRepository;
import com.example.condo.repo.AssemblyVoteRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AssemblyService {

    private final AssemblyRepository assemblyRepo;
    private final AssemblyAgendaItemRepository agendaRepo;
    private final AssemblyVoteRepository voteRepo;
    private final UnitRepository unitRepo;
    private final AuditService auditService;

    public AssemblyService(AssemblyRepository assemblyRepo,
                            AssemblyAgendaItemRepository agendaRepo,
                            AssemblyVoteRepository voteRepo,
                            UnitRepository unitRepo,
                            AuditService auditService) {
        this.assemblyRepo = assemblyRepo;
        this.agendaRepo = agendaRepo;
        this.voteRepo = voteRepo;
        this.unitRepo = unitRepo;
        this.auditService = auditService;
    }

    public Page<AssemblyListItemResponse> list(Long condoIdParam, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        Page<AssemblyListItemResponse> page;
        if (UserContext.isSuperuser() && condoId == null) {
            page = assemblyRepo.findAllCardsByTenant(tenant, pageable);
        } else {
            if (condoId == null) return Page.empty(pageable);
            page = assemblyRepo.findAllCards(tenant, condoId, pageable);
        }
        if (!isMorador()) {
            return page;
        }
        Long unitId = UserContext.unitId();
        return page.map(item -> enrichForMorador(item, unitId));
    }

    public Assembly get(Long id) {
        String tenant = TenantContext.get();
        Assembly a = assemblyRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Assembleia", "id", id));
        enforceSameCondominium(a.getCondominiumId());
        return a;
    }

    public List<AssemblyAgendaItem> getAgenda(Long assemblyId) {
        get(assemblyId); // valida acesso
        return agendaRepo.findByAssemblyIdOrderBySortOrderAsc(assemblyId);
    }

    /**
     * Retorna resultado da votação sem expor votos individuais.
     * Estrutura: { itemId, itemTitle, total, yes, no, abstain }
     */
    public Map<String, Object> getVoteResults(Long assemblyId, Long itemId) {
        Assembly assembly = get(assemblyId);
        AssemblyAgendaItem item = agendaRepo.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Pauta", "id", itemId));
        if (!item.getAssemblyId().equals(assemblyId)) {
            throw new BusinessException("Item não pertence à assembleia");
        }

        long yes = voteRepo.countByItemAndValue(itemId, AssemblyVote.VoteValue.YES);
        long no = voteRepo.countByItemAndValue(itemId, AssemblyVote.VoteValue.NO);
        long abstain = voteRepo.countByItemAndValue(itemId, AssemblyVote.VoteValue.ABSTAIN);
        long total = yes + no + abstain;

        // Total de unidades do condomínio (quórum)
        long totalUnits = unitRepo.countByTenantIdAndCondominiumId(
            TenantContext.get(), assembly.getCondominiumId());

        return Map.of(
            "itemId", itemId,
            "itemTitle", item.getTitle(),
            "totalVotes", total,
            "totalUnits", totalUnits,
            "yes", yes,
            "no", no,
            "abstain", abstain,
            "quorumPct", totalUnits > 0 ? (total * 100.0 / totalUnits) : 0.0
        );
    }

    @Transactional
    public Assembly create(Long condoIdParam, String title, String description,
                            Instant scheduledAt, String location) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");

        Assembly a = new Assembly();
        a.setTenantId(tenant);
        a.setCondominiumId(condoId);
        a.setTitle(title.trim());
        a.setDescription(description);
        a.setScheduledAt(scheduledAt);
        a.setLocation(location);
        a.setStatus(Assembly.Status.SCHEDULED);
        a.setCreatedBy(UserContext.userId());
        a.setCreatedAt(Instant.now());
        a = assemblyRepo.save(a);
        auditService.log("CREATE", "Assembly", a.getId(), a.getCondominiumId(), null, a);
        return a;
    }

    @Transactional
    public AssemblyAgendaItem addAgendaItem(Long assemblyId, String title, String description,
                                             boolean requiresVote, int sortOrder) {
        Assembly assembly = get(assemblyId);
        if (assembly.getStatus() == Assembly.Status.CLOSED || assembly.getStatus() == Assembly.Status.CANCELLED) {
            throw new BusinessException("Não é possível adicionar pauta a assembleia encerrada/cancelada");
        }

        AssemblyAgendaItem item = new AssemblyAgendaItem();
        item.setAssemblyId(assemblyId);
        item.setTitle(title.trim());
        item.setDescription(description);
        item.setRequiresVote(requiresVote);
        item.setSortOrder(sortOrder);
        item = agendaRepo.save(item);
        auditService.log("CREATE", "AssemblyAgendaItem", item.getId(), assembly.getCondominiumId(), null, item);
        return item;
    }

    @Transactional
    public Assembly open(Long id) {
        Assembly a = get(id);
        Assembly before = copyAssembly(a);
        if (a.getStatus() != Assembly.Status.SCHEDULED) {
            throw new BusinessException("Apenas assembleias agendadas podem ser abertas");
        }
        a.setStatus(Assembly.Status.OPEN);
        a.setOpenedAt(Instant.now());
        a = assemblyRepo.save(a);
        auditService.log("OPEN", "Assembly", a.getId(), a.getCondominiumId(), before, a);
        return a;
    }

    @Transactional
    public Assembly close(Long id) {
        Assembly a = get(id);
        Assembly before = copyAssembly(a);
        if (a.getStatus() != Assembly.Status.OPEN) {
            throw new BusinessException("Apenas assembleias abertas podem ser encerradas");
        }
        a.setStatus(Assembly.Status.CLOSED);
        a.setClosedAt(Instant.now());
        a = assemblyRepo.save(a);
        auditService.log("CLOSE", "Assembly", a.getId(), a.getCondominiumId(), before, a);
        return a;
    }

    @Transactional
    public AssemblyVote vote(Long assemblyId, Long itemId, String voteValueStr) {
        Assembly assembly = get(assemblyId);
        if (assembly.getStatus() != Assembly.Status.OPEN) {
            throw new BusinessException("Votação só é permitida em assembleias abertas");
        }

        Long unitId = UserContext.unitId();
        if (unitId == null) throw new BusinessException("Usuário sem unidade vinculada para votar");

        AssemblyAgendaItem item = agendaRepo.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Pauta", "id", itemId));
        if (!item.getAssemblyId().equals(assemblyId)) {
            throw new BusinessException("Item não pertence à assembleia");
        }
        if (!item.isRequiresVote()) {
            throw new BusinessException("Este item de pauta não requer votação");
        }

        if (voteRepo.existsByAgendaItemIdAndUnitId(itemId, unitId)) {
            throw new BusinessException("Sua unidade já votou neste item");
        }

        AssemblyVote.VoteValue value;
        try {
            value = AssemblyVote.VoteValue.valueOf(voteValueStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Voto inválido. Valores: YES, NO, ABSTAIN");
        }

        AssemblyVote vote = new AssemblyVote();
        vote.setAgendaItemId(itemId);
        vote.setUnitId(unitId);
        vote.setVoteValue(value);
        vote.setVotedBy(UserContext.userId());
        vote.setVotedAt(Instant.now());
        vote = voteRepo.save(vote);
        auditService.log("VOTE_CAST", "AssemblyVote", vote.getId(), assembly.getCondominiumId(), null, vote);
        return vote;
    }

    private void enforceSameCondominium(Long condoId) {
        Long effective = UserContext.resolveCondominiumId(condoId);
        if (effective != null && !effective.equals(condoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private AssemblyListItemResponse enrichForMorador(AssemblyListItemResponse item, Long unitId) {
        List<AssemblyAgendaItem> agendaItems = agendaRepo.findByAssemblyIdOrderBySortOrderAsc(item.id());
        long votableItems = agendaItems.stream().filter(AssemblyAgendaItem::isRequiresVote).count();
        if (unitId == null) {
            return copyItemWithVoteState(item, false, false, "BLOCKED_NO_UNIT");
        }
        if ("CLOSED".equalsIgnoreCase(item.status())) {
            return copyItemWithVoteState(item, false, hasAnyVote(agendaItems, unitId), "CLOSED");
        }
        if (!"OPEN".equalsIgnoreCase(item.status())) {
            return copyItemWithVoteState(item, false, false, "NOT_OPEN");
        }
        if (votableItems == 0) {
            return copyItemWithVoteState(item, false, false, "NO_VOTABLE_ITEMS");
        }
        long votedItems = agendaItems.stream()
            .filter(AssemblyAgendaItem::isRequiresVote)
            .filter(agendaItem -> voteRepo.existsByAgendaItemIdAndUnitId(agendaItem.getId(), unitId))
            .count();
        if (votedItems >= votableItems) {
            return copyItemWithVoteState(item, false, true, "ALREADY_VOTED");
        }
        return copyItemWithVoteState(item, true, votedItems > 0, "CAN_VOTE");
    }

    private boolean hasAnyVote(List<AssemblyAgendaItem> agendaItems, Long unitId) {
        return agendaItems.stream()
            .filter(AssemblyAgendaItem::isRequiresVote)
            .anyMatch(agendaItem -> voteRepo.existsByAgendaItemIdAndUnitId(agendaItem.getId(), unitId));
    }

    private AssemblyListItemResponse copyItemWithVoteState(
        AssemblyListItemResponse item,
        boolean canVote,
        boolean alreadyVoted,
        String voteStatus
    ) {
        return new AssemblyListItemResponse(
            item.id(),
            item.condominiumId(),
            item.condominiumName(),
            item.title(),
            item.description(),
            item.status(),
            item.scheduledAt(),
            item.location(),
            item.agendaItemCount(),
            canVote,
            alreadyVoted,
            voteStatus
        );
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private Assembly copyAssembly(Assembly source) {
        Assembly copy = new Assembly();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setScheduledAt(source.getScheduledAt());
        copy.setLocation(source.getLocation());
        copy.setStatus(source.getStatus());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setOpenedAt(source.getOpenedAt());
        copy.setClosedAt(source.getClosedAt());
        return copy;
    }
}
