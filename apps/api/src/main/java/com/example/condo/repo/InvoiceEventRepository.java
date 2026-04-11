package com.example.condo.repo;

import com.example.condo.entity.InvoiceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceEventRepository extends JpaRepository<InvoiceEvent, Long> {

    List<InvoiceEvent> findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(String tenantId, Long invoiceId);
}
