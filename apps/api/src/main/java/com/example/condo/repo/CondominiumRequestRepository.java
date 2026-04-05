package com.example.condo.repo;

import com.example.condo.entity.CondominiumRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondominiumRequestRepository extends JpaRepository<CondominiumRequest, Long> {

    Page<CondominiumRequest> findByStatusOrderByCreatedAtDesc(
        CondominiumRequest.Status status,
        Pageable pageable
    );

    long countByStatus(CondominiumRequest.Status status);
}
