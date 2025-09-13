package com.example.condo.repo;

import com.example.condo.entity.Resident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

  @Query("""
      select r from Resident r
      where r.tenantId = :t and r.condominiumId = :c and
            ( :q is null or :q = '' or
              lower(r.name)  like lower(concat('%', :q, '%')) or
              lower(r.email) like lower(concat('%', :q, '%')) or
              lower(r.phone) like lower(concat('%', :q, '%')) )
      """)
  Page<Resident> search(@Param("t") String tenantId,
                        @Param("c") Long condoId,
                        @Param("q") String q,
                        Pageable pageable);

  Optional<Resident> findByTenantIdAndId(String tenantId, Long id);
}