package com.example.condo.repo;

import com.example.condo.entity.FinancialWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialWebhookEventRepository extends JpaRepository<FinancialWebhookEvent, Long> {

    boolean existsByDedupKey(String dedupKey);

    Optional<FinancialWebhookEvent> findByDedupKey(String dedupKey);
}
