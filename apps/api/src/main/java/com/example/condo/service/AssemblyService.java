package com.example.condo.service;

import com.example.condo.dto.assembly.AssemblyCreateAgendaItemRequest;
import com.example.condo.dto.assembly.AssemblyCreateRequest;
import com.example.condo.dto.assembly.AssemblyListItemResponse;
import com.example.condo.dto.assembly.AssemblyAgendaItemResponse;
import com.example.condo.dto.assembly.AssemblyAgendaOptionResponse;
import com.example.condo.entity.Assembly;
import com.example.condo.entity.AssemblyAgendaItem;
import com.example.condo.entity.AssemblyAgendaOption;
import com.example.condo.entity.AssemblyVote;
import com.example.condo.entity.User;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.AssemblyAgendaItemRepository;
import com.example.condo.repo.AssemblyAgendaOptionRepository;
import com.example.condo.repo.AssemblyRepository;
import com.example.condo.repo.AssemblyVoteRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.repo.UserRepository;
import com.example.condo.security.Role;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AssemblyService {

    private final AssemblyRepository assemblyRepo;
    private final AssemblyAgendaItemRepository agendaRepo;
    private final AssemblyAgendaOptionRepository optionRepo;
    private final AssemblyVoteRepository voteRepo;
    private final UnitRepository unitRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;

    public AssemblyService(AssemblyRepository assemblyRepo,
                            AssemblyAgendaItemRepository agendaRepo,
                            AssemblyAgendaOptionRepository optionRepo,
                            AssemblyVoteRepository voteRepo,
                            UnitRepository unitRepo,
                            UserRepository userRepo,
                            AuditService auditService) {
        this.assemblyRepo = assemblyRepo;
        this.agendaRepo = agendaRepo;
        this.optionRepo = optionRepo;
        this.voteRepo = voteRepo;
        this.unitRepo = unitRepo;
        this.userRepo = userRepo;
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
        if (!canEvaluateVotingState()) {
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

    public List<AssemblyAgendaItemResponse> getAgenda(Long assemblyId) {
        get(assemblyId); // valida acesso
        return agendaRepo.findByAssemblyIdOrderBySortOrderAsc(assemblyId).stream()
            .map(this::toAgendaResponse)
            .toList();
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

        long totalUnits = unitRepo.countByTenantIdAndCondominiumId(
            TenantContext.get(), assembly.getCondominiumId());

        if (item.getItemType() == AssemblyAgendaItem.ItemType.OFFICE_ELECTION) {
            Map<Long, Long> counts = new HashMap<>();
            voteRepo.countByItemGroupedByOption(itemId).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
            List<Map<String, Object>> candidates = optionRepo.findByAgendaItemIdOrderBySortOrderAscIdAsc(itemId).stream()
                .map(option -> Map.<String, Object>of(
                    "optionId", option.getId(),
                    "candidateName", option.getCandidateName(),
                    "votes", counts.getOrDefault(option.getId(), 0L)
                ))
                .toList();
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            List<Long> winners = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes && maxVotes > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("itemType", item.getItemType().name());
            result.put("itemId", itemId);
            result.put("itemTitle", item.getTitle());
            result.put("officeName", item.getOfficeName());
            result.put("resolutionStatus", item.getResolutionStatus().name());
            result.put("winningOptionId", item.getWinningOptionId());
            result.put("appliedUserId", item.getAppliedUserId());
            result.put("totalVotes", total);
            result.put("totalUnits", totalUnits);
            result.put("quorumPct", totalUnits > 0 ? (total * 100.0 / totalUnits) : 0.0);
            result.put("candidates", candidates);
            result.put("winnerOptionIds", winners);
            return result;
        }

        long yes = voteRepo.countByItemAndValue(itemId, AssemblyVote.VoteValue.YES);
        long no = voteRepo.countByItemAndValue(itemId, AssemblyVote.VoteValue.NO);
        long abstain = voteRepo.countByItemAndValue(itemId, AssemblyVote.VoteValue.ABSTAIN);
        long total = yes + no + abstain;

        return Map.of(
            "itemType", item.getItemType().name(),
            "itemId", itemId,
            "itemTitle", item.getTitle(),
            "resolutionStatus", item.getResolutionStatus().name(),
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
        return create(new AssemblyCreateRequest(condoIdParam, title, description, scheduledAt, location, List.of()));
    }

    @Transactional
    public Assembly create(AssemblyCreateRequest request) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(request.condominiumId());
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");

        Assembly a = new Assembly();
        a.setTenantId(tenant);
        a.setCondominiumId(condoId);
        a.setTitle(request.title().trim());
        a.setDescription(request.description());
        a.setScheduledAt(request.scheduledAt());
        a.setLocation(request.location());
        a.setStatus(Assembly.Status.SCHEDULED);
        a.setCreatedBy(UserContext.userId());
        a.setCreatedAt(Instant.now());
        a = assemblyRepo.save(a);
        if (request.agendaItems() != null) {
            int index = 0;
            for (AssemblyCreateAgendaItemRequest itemRequest : request.agendaItems()) {
                addAgendaItem(
                    a.getId(),
                    itemRequest.title(),
                    itemRequest.description(),
                    !Boolean.FALSE.equals(itemRequest.requiresVote()),
                    itemRequest.sortOrder() != null ? itemRequest.sortOrder() : index,
                    itemRequest.itemType(),
                    itemRequest.officeName(),
                    null,
                    itemRequest.candidateUserIds()
                );
                index++;
            }
        }
        auditService.log("CREATE", "Assembly", a.getId(), a.getCondominiumId(), null, a);
        return a;
    }

    @Transactional
    public AssemblyAgendaItemResponse addAgendaItem(Long assemblyId, String title, String description,
                                             boolean requiresVote, int sortOrder, String itemType,
                                             String officeName, List<String> options) {
        return addAgendaItem(assemblyId, title, description, requiresVote, sortOrder, itemType, officeName, options, null);
    }

    @Transactional
    public AssemblyAgendaItemResponse addAgendaItem(Long assemblyId, String title, String description,
                                             boolean requiresVote, int sortOrder, String itemType,
                                             String officeName, List<String> options, List<Long> candidateUserIds) {
        Assembly assembly = get(assemblyId);
        if (assembly.getStatus() == Assembly.Status.CLOSED || assembly.getStatus() == Assembly.Status.CANCELLED) {
            throw new BusinessException("Não é possível adicionar pauta a assembleia encerrada/cancelada");
        }

        AssemblyAgendaItem.ItemType resolvedType = resolveItemType(itemType);
        List<String> normalizedOptions = options == null ? List.of() : options.stream()
            .map(value -> value == null ? "" : value.trim())
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        List<Long> normalizedCandidateUserIds = candidateUserIds == null ? List.of() : candidateUserIds.stream().distinct().toList();
        if (resolvedType == AssemblyAgendaItem.ItemType.OFFICE_ELECTION) {
            if (officeName == null || officeName.isBlank()) {
                throw new BusinessException("Informe o cargo da eleição.");
            }
            if ((normalizedCandidateUserIds.isEmpty() ? normalizedOptions.size() : normalizedCandidateUserIds.size()) < 2) {
                throw new BusinessException("Informe pelo menos dois candidatos para a eleição.");
            }
            requiresVote = true;
        }

        AssemblyAgendaItem item = new AssemblyAgendaItem();
        item.setAssemblyId(assemblyId);
        item.setTitle(title.trim());
        item.setDescription(description);
        item.setRequiresVote(requiresVote);
        item.setItemType(resolvedType);
        item.setOfficeName(resolvedType == AssemblyAgendaItem.ItemType.OFFICE_ELECTION ? officeName.trim() : null);
        item.setResolutionStatus(resolvedType == AssemblyAgendaItem.ItemType.OFFICE_ELECTION
            ? AssemblyAgendaItem.ResolutionStatus.PENDING
            : AssemblyAgendaItem.ResolutionStatus.NOT_APPLICABLE);
        item.setSortOrder(sortOrder);
        item = agendaRepo.save(item);
        if (resolvedType == AssemblyAgendaItem.ItemType.OFFICE_ELECTION) {
            createElectionOptions(assembly, item, normalizedOptions, normalizedCandidateUserIds);
        }
        auditService.log("CREATE", "AssemblyAgendaItem", item.getId(), assembly.getCondominiumId(), null, item);
        return toAgendaResponse(item);
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
    public Assembly validate(Long id) {
        Assembly assembly = get(id);
        Assembly before = copyAssembly(assembly);
        if (assembly.getStatus() != Assembly.Status.CLOSED) {
            throw new BusinessException("A validação oficial só pode ocorrer após o encerramento da assembleia.");
        }
        if (assembly.getValidatedAt() != null) {
            throw new BusinessException("Assembleia já validada.");
        }

        for (AssemblyAgendaItem item : agendaRepo.findByAssemblyIdOrderBySortOrderAsc(id)) {
            if (item.getItemType() != AssemblyAgendaItem.ItemType.OFFICE_ELECTION) {
                continue;
            }
            resolveElectionResult(assembly, item);
        }

        assembly.setValidatedAt(Instant.now());
        assembly.setValidatedBy(UserContext.userId());
        assembly = assemblyRepo.save(assembly);
        auditService.log("VALIDATE", "Assembly", assembly.getId(), assembly.getCondominiumId(), before, assembly);
        return assembly;
    }

    @Transactional
    public AssemblyVote vote(Long assemblyId, Long itemId, String voteValueStr, Long optionId) {
        Assembly assembly = get(assemblyId);
        if (assembly.getStatus() != Assembly.Status.OPEN) {
            throw new BusinessException("Votação só é permitida em assembleias abertas");
        }

        if (!canCastVote()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu perfil não pode votar nesta assembleia.");
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

        AssemblyVote vote = new AssemblyVote();
        vote.setAgendaItemId(itemId);
        vote.setUnitId(unitId);
        if (item.getItemType() == AssemblyAgendaItem.ItemType.OFFICE_ELECTION) {
            if (optionId == null) {
                throw new BusinessException("Selecione um candidato para registrar o voto.");
            }
            AssemblyAgendaOption option = optionRepo.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidato", "id", optionId));
            if (!option.getAgendaItemId().equals(itemId)) {
                throw new BusinessException("Candidato não pertence a este item de pauta.");
            }
            vote.setOptionId(optionId);
            vote.setVoteValue(null);
        } else {
            AssemblyVote.VoteValue value;
            try {
                value = AssemblyVote.VoteValue.valueOf(voteValueStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Voto inválido. Valores: YES, NO, ABSTAIN");
            }
            vote.setVoteValue(value);
            vote.setOptionId(null);
        }
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
        if (!canCastVote()) {
            return copyItemWithVoteState(item, false, false, "ROLE_CANNOT_VOTE");
        }
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

    private AssemblyAgendaItemResponse toAgendaResponse(AssemblyAgendaItem item) {
        List<AssemblyAgendaOptionResponse> options = optionRepo.findByAgendaItemIdOrderBySortOrderAscIdAsc(item.getId()).stream()
            .map(option -> new AssemblyAgendaOptionResponse(
                option.getId(),
                option.getCandidateUserId(),
                option.getCandidateName(),
                option.getCandidateUnitLabel(),
                option.getSortOrder()
            ))
            .toList();
        return new AssemblyAgendaItemResponse(
            item.getId(),
            item.getAssemblyId(),
            item.getTitle(),
            item.getDescription(),
            item.isRequiresVote(),
            item.getSortOrder(),
            item.getItemType().name(),
            item.getOfficeName(),
            item.getResolutionStatus().name(),
            item.getWinningOptionId(),
            item.getAppliedUserId(),
            options
        );
    }

    private AssemblyAgendaItem.ItemType resolveItemType(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            return AssemblyAgendaItem.ItemType.GENERAL_VOTE;
        }
        try {
            return AssemblyAgendaItem.ItemType.valueOf(itemType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Tipo de pauta inválido. Use GENERAL_VOTE ou OFFICE_ELECTION.");
        }
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

    private boolean canEvaluateVotingState() {
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) {
            return false;
        }
        String role = ctx.role().toUpperCase();
        return "MORADOR".equals(role) || "SINDICO".equals(role);
    }

    private boolean canCastVote() {
        UserContext.Data ctx = UserContext.get();
        if (ctx == null || ctx.role() == null) {
            return false;
        }
        String role = ctx.role().toUpperCase();
        return "MORADOR".equals(role) || "SINDICO".equals(role);
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
        copy.setValidatedAt(source.getValidatedAt());
        copy.setValidatedBy(source.getValidatedBy());
        return copy;
    }

    private void createElectionOptions(
        Assembly assembly,
        AssemblyAgendaItem item,
        List<String> normalizedOptions,
        List<Long> candidateUserIds
    ) {
        String tenant = TenantContext.get();
        if (!candidateUserIds.isEmpty()) {
            int index = 0;
            for (Long candidateUserId : candidateUserIds) {
                User candidate = userRepo.findByTenantIdAndId(tenant, candidateUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário candidato", "id", candidateUserId));
                validateCandidateScope(assembly.getCondominiumId(), candidate);
                if (optionRepo.existsByAgendaItemIdAndCandidateUserId(item.getId(), candidateUserId)) {
                    throw new BusinessException("Candidato duplicado na mesma eleição.");
                }
                AssemblyAgendaOption option = new AssemblyAgendaOption();
                option.setAgendaItemId(item.getId());
                option.setCandidateUserId(candidate.getId());
                option.setCandidateName(candidate.getName());
                option.setCandidateUnitLabel(buildUnitLabel(candidate));
                option.setSortOrder(index++);
                optionRepo.save(option);
            }
            return;
        }

        int index = 0;
        for (String optionName : normalizedOptions) {
            AssemblyAgendaOption option = new AssemblyAgendaOption();
            option.setAgendaItemId(item.getId());
            option.setCandidateName(optionName);
            option.setSortOrder(index++);
            optionRepo.save(option);
        }
    }

    private void validateCandidateScope(Long condominiumId, User candidate) {
        if (!condominiumId.equals(candidate.getCondominiumId())) {
            throw new BusinessException("Candidato não pertence ao condomínio da assembleia.");
        }
        if (candidate.getUnitId() == null) {
            throw new BusinessException("Candidato precisa estar vinculado a uma unidade.");
        }
        if (!(candidate.getRole() == Role.MORADOR || candidate.getRole() == Role.SINDICO)) {
            throw new BusinessException("Somente MORADOR ou SINDICO podem ser candidatos.");
        }
    }

    private String buildUnitLabel(User candidate) {
        if (candidate.getUnitId() == null) {
            return null;
        }
        return unitRepo.findByTenantIdAndId(TenantContext.get(), candidate.getUnitId())
            .map(unit -> unit.getBlock() != null && !unit.getBlock().isBlank()
                ? "Unidade " + unit.getNumber() + " • Bloco " + unit.getBlock()
                : "Unidade " + unit.getNumber())
            .orElse("Unidade #" + candidate.getUnitId());
    }

    private void resolveElectionResult(Assembly assembly, AssemblyAgendaItem item) {
        Map<Long, Long> counts = new HashMap<>();
        voteRepo.countByItemGroupedByOption(item.getId()).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        long maxVotes = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        List<Long> winners = counts.entrySet().stream()
            .filter(entry -> entry.getValue() == maxVotes && maxVotes > 0)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
        if (winners.size() != 1) {
            item.setResolutionStatus(AssemblyAgendaItem.ResolutionStatus.TIED);
            item.setWinningOptionId(null);
            item.setAppliedUserId(null);
            item.setResolvedAt(Instant.now());
            item.setResolvedBy(UserContext.userId());
            agendaRepo.save(item);
            return;
        }

        AssemblyAgendaOption winnerOption = optionRepo.findById(winners.getFirst())
            .orElseThrow(() -> new ResourceNotFoundException("Candidato vencedor", "id", winners.getFirst()));
        if (winnerOption.getCandidateUserId() == null) {
            throw new BusinessException("Eleição sem vínculo real do candidato vencedor.");
        }
        applySyndicRoleTransition(assembly, winnerOption.getCandidateUserId());
        item.setResolutionStatus(AssemblyAgendaItem.ResolutionStatus.APPLIED);
        item.setWinningOptionId(winnerOption.getId());
        item.setAppliedUserId(winnerOption.getCandidateUserId());
        item.setResolvedAt(Instant.now());
        item.setResolvedBy(UserContext.userId());
        agendaRepo.save(item);
    }

    private void applySyndicRoleTransition(Assembly assembly, Long winnerUserId) {
        String tenant = TenantContext.get();
        User winner = userRepo.findByTenantIdAndId(tenant, winnerUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário vencedor", "id", winnerUserId));
        validateCandidateScope(assembly.getCondominiumId(), winner);

        List<User> condominiumUsers = userRepo.findByTenantAndCondominiumAndRolesOrdered(
            tenant,
            assembly.getCondominiumId(),
            List.of(Role.SINDICO)
        );
        for (User currentSyndic : condominiumUsers) {
            if (currentSyndic.getId().equals(winner.getId())) {
                continue;
            }
            currentSyndic.setRole(Role.MORADOR);
            userRepo.save(currentSyndic);
            auditService.log("ROLE_CHANGE", "User", currentSyndic.getId(), currentSyndic.getCondominiumId(), null, "SINDICO->MORADOR");
        }

        winner.setRole(Role.SINDICO);
        userRepo.save(winner);
        auditService.log("ROLE_CHANGE", "User", winner.getId(), winner.getCondominiumId(), null, "WINNER->SINDICO");
    }
}
