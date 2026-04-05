package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.assembly.AssemblyListItemResponse;
import com.example.condo.entity.Assembly;
import com.example.condo.entity.AssemblyAgendaItem;
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
    public List<AssemblyAgendaItem> getAgenda(@PathVariable Long id) {
        return service.getAgenda(id);
    }

    @GetMapping("/{id}/agenda/{itemId}/votes")
    public Map<String, Object> getVoteResults(@PathVariable Long id, @PathVariable Long itemId) {
        return service.getVoteResults(id, itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public Assembly create(@RequestBody Map<String, Object> body) {
        Long condoId = body.get("condominiumId") != null ? ((Number) body.get("condominiumId")).longValue() : null;
        Instant scheduledAt = Instant.parse((String) body.get("scheduledAt"));
        return service.create(condoId, (String) body.get("title"), (String) body.get("description"),
            scheduledAt, (String) body.get("location"));
    }

    @PostMapping("/{id}/agenda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERUSER','ADMIN','SINDICO')")
    public AssemblyAgendaItem addAgendaItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        boolean requiresVote = !Boolean.FALSE.equals(body.get("requiresVote"));
        int sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        return service.addAgendaItem(id, title, description, requiresVote, sortOrder);
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

    @PostMapping("/{id}/agenda/{itemId}/vote")
    @PreAuthorize("hasAnyRole('MORADOR','SINDICO','ZELADOR')")
    public AssemblyVote vote(@PathVariable Long id, @PathVariable Long itemId,
                              @RequestBody Map<String, String> body) {
        return service.vote(id, itemId, body.get("vote"));
    }
}
