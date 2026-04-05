package com.example.condo.repo;

import com.example.condo.entity.FinancialConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialConfigRepository extends JpaRepository<FinancialConfig, Long> {
    Optional<FinancialConfig> findByTenantIdAndCondominiumId(String tenantId, Long condominiumId);
}
