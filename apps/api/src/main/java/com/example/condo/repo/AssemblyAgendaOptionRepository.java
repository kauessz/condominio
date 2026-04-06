package com.example.condo.repo;

import com.example.condo.entity.AssemblyAgendaOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssemblyAgendaOptionRepository extends JpaRepository<AssemblyAgendaOption, Long> {
    List<AssemblyAgendaOption> findByAgendaItemIdOrderBySortOrderAscIdAsc(Long agendaItemId);

    boolean existsByAgendaItemIdAndCandidateUserId(Long agendaItemId, Long candidateUserId);
}
