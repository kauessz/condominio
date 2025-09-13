package com.example.condo.repo;

import com.example.condo.entity.Condominium;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CondominiumRepository extends JpaRepository<Condominium, Long> {

    // Paginação por tenant (usado no Dashboard)
    Page<Condominium> findByTenantId(String tenantId, Pageable pageable);

    // Busca 1 registro por tenant + id
    Optional<Condominium> findByTenantIdAndId(String tenantId, Long id);

    // Método legado que retorna lista completa por tenant (mantido para compatibilidade)
    @Query("select c from Condominium c where c.tenantId = :t")
    List<Condominium> findAllByTenant(@Param("t") String tenantId);
}