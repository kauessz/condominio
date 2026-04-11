package com.example.condo.repo;

import com.example.condo.entity.InvoiceWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceWebhookEventRepository extends JpaRepository<InvoiceWebhookEvent, Long> {

    Optional<InvoiceWebhookEvent> findByProviderAndExternalEventId(String provider, String externalEventId);
}
