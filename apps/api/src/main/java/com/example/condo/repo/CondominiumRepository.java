package com.example.condo.repo;

import com.example.condo.entity.Condominium;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CondominiumRepository extends JpaRepository<Condominium, Long> {

  Optional<Condominium> findByTenantIdAndId(String tenantId, Long id);

  // >>> Adicionado para o DevData.java compilar
  Optional<Condominium> findByTenantIdAndName(String tenantId, String name);

  // Contagem robusta via subqueries (evita problemas de join)
  @Query(
      value = """
              select
                c.id,
                c.name,
                c.cnpj,
                c.created_at,
                (select count(*) from unit u
                   where u.tenant_id = c.tenant_id
                     and u.condominium_id = c.id) as units,
                (select count(*) from resident r
                   where r.tenant_id = c.tenant_id
                     and r.condominium_id = c.id) as residents
              from condominium c
              where c.tenant_id = :t
              order by c.created_at desc
              """,
      countQuery = """
              select count(*)
              from condominium c
              where c.tenant_id = :t
              """,
      nativeQuery = true
  )
  Page<Object[]> pageWithCounts(@Param("t") String tenantId, Pageable pageable);
}