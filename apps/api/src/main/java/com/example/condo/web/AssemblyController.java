package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.assembly.AssemblyCreateRequest;
import com.example.condo.dto.assembly.AssemblyAgendaItemResponse;
import com.example.condo.dto.assembly.AssemblyListItemResponse;
import com.example.condo.entity.Assembly;
import com.example.condo.entity.AssemblyVote;
import com.example.condo.service.AssemblyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assemblies")
public class AssemblyController {

    private final AssemblyService service;

    public AssemblyController(AssemblyService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AssemblyListItemResponse> list(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Page<AssemblyListItemResponse> p = service.list(condominiumId, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @GetMapping("/{id}")
    public Assembly get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/agenda")
    public List<AssemblyAgendaItemResponse> getAgenda(@PathVariable Long id) {
        return service.getAgenda(id);
    }

    @GetMapping("/{id}/agenda/{itemId}/votes")
    public Map<String, Object> getVoteResults(@PathVariable Long id, @PathVariable Long itemId) {
        return service.getVoteResults(id, itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Assembly create(@RequestBody AssemblyCreateRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/agenda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public AssemblyAgendaItemResponse addAgendaItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        boolean requiresVote = !Boolean.FALSE.equals(body.get("requiresVote"));
        int sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        String itemType = (String) body.get("itemType");
        String officeName = (String) body.get("officeName");
        List<String> options = body.get("options") instanceof List<?> rawOptions
            ? rawOptions.stream().map(String::valueOf).toList()
            : List.of();
        List<Long> candidateUserIds = body.get("candidateUserIds") instanceof List<?> rawCandidateIds
            ? rawCandidateIds.stream().map(value -> Long.valueOf(String.valueOf(value))).toList()
            : List.of();
        return service.addAgendaItem(id, title, description, requiresVote, sortOrder, itemType, officeName, options, candidateUserIds);
    }

    @PatchMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Assembly open(@PathVariable Long id) {
        return service.open(id);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Assembly close(@PathVariable Long id) {
        return service.close(id);
    }

    @PatchMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Assembly validate(@PathVariable Long id) {
        return service.validate(id);
    }

    @PostMapping("/{id}/agenda/{itemId}/vote")
    @PreAuthorize("hasAnyRole('MORADOR','SINDICO')")
    public AssemblyVote vote(@PathVariable Long id, @PathVariable Long itemId,
                              @RequestBody Map<String, String> body) {
        Long optionId = body.get("optionId") != null && !body.get("optionId").isBlank()
            ? Long.valueOf(body.get("optionId"))
            : null;
        return service.vote(id, itemId, body.get("vote"), optionId);
    }
}
