package com.example.condo.repo;

import com.example.condo.entity.FinancialNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialNotificationRepository extends JpaRepository<FinancialNotification, Long> {

    List<FinancialNotification> findTop20ByTenantIdAndInvoiceIdOrderByCreatedAtDesc(String tenantId, Long invoiceId);

    boolean existsByTenantIdAndInvoiceIdAndType(String tenantId, Long invoiceId, FinancialNotification.Type type);
}
