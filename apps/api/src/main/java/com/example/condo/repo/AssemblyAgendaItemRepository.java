package com.example.condo.repo;

import com.example.condo.entity.AssemblyAgendaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssemblyAgendaItemRepository extends JpaRepository<AssemblyAgendaItem, Long> {
    List<AssemblyAgendaItem> findByAssemblyIdOrderBySortOrderAsc(Long assemblyId);
}
