package com.example.condo.repo;

import com.example.condo.entity.GovernanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GovernanceRequestRepository extends JpaRepository<GovernanceRequest, Long> {
    Page<GovernanceRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(
        String tenantId,
        GovernanceRequest.Status status,
        Pageable pageable
    );

    Optional<GovernanceRequest> findByTenantIdAndId(String tenantId, Long id);
}
