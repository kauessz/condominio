package com.example.condo.repo;

import com.example.condo.dto.assembly.AssemblyListItemResponse;
import com.example.condo.entity.Assembly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssemblyRepository extends JpaRepository<Assembly, Long> {

    Optional<Assembly> findByTenantIdAndId(String tenantId, Long id);

    @Query("select a from Assembly a where a.tenantId = :tenantId and a.condominiumId = :condoId order by a.scheduledAt desc")
    Page<Assembly> findAll(@Param("tenantId") String tenantId,
                            @Param("condoId") Long condoId,
                            Pageable pageable);

    @Query("""
        select new com.example.condo.dto.assembly.AssemblyListItemResponse(
            a.id,
            a.condominiumId,
            c.name,
            a.title,
            a.description,
            cast(a.status as string),
            a.scheduledAt,
            a.location,
            (select count(i) from AssemblyAgendaItem i where i.assemblyId = a.id)
        )
        from Assembly a
        join Condominium c on c.id = a.condominiumId
        where a.tenantId = :tenantId
        order by a.scheduledAt desc
    """)
    Page<AssemblyListItemResponse> findAllCardsByTenant(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("""
        select new com.example.condo.dto.assembly.AssemblyListItemResponse(
            a.id,
            a.condominiumId,
            c.name,
            a.title,
            a.description,
            cast(a.status as string),
            a.scheduledAt,
            a.location,
            (select count(i) from AssemblyAgendaItem i where i.assemblyId = a.id)
        )
        from Assembly a
        join Condominium c on c.id = a.condominiumId
        where a.tenantId = :tenantId
          and a.condominiumId = :condoId
        order by a.scheduledAt desc
    """)
    Page<AssemblyListItemResponse> findAllCards(@Param("tenantId") String tenantId,
                                                @Param("condoId") Long condoId,
                                                Pageable pageable);
}
