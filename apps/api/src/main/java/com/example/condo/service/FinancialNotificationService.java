package com.example.condo.service;

import com.example.condo.entity.FinancialNotification;
import com.example.condo.entity.Invoice;
import com.example.condo.entity.Resident;
import com.example.condo.repo.FinancialNotificationRepository;
import com.example.condo.repo.ResidentRepository;
import com.example.condo.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancialNotificationService {

    private final FinancialNotificationRepository notificationRepository;
    private final ResidentRepository residentRepository;

    public FinancialNotificationService(
        FinancialNotificationRepository notificationRepository,
        ResidentRepository residentRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.residentRepository = residentRepository;
    }

    @Transactional
    public FinancialNotification logInvoiceNotification(Invoice invoice, FinancialNotification.Type type, String message) {
        return logInvoiceNotification(invoice, type, message, Map.of());
    }

    @Transactional
    public FinancialNotification logInvoiceNotification(Invoice invoice, FinancialNotification.Type type, String message, Map<String, Object> metadata) {
        Resident resident = findPrimaryResident(invoice);
        FinancialNotification notification = new FinancialNotification();
        notification.setTenantId(invoice.getTenantId());
        notification.setCondominiumId(invoice.getCondominiumId());
        notification.setInvoiceId(invoice.getId());
        notification.setUnitId(invoice.getUnitId());
        notification.setRecipientName(resident != null ? resident.getName() : null);
        notification.setRecipientEmail(resident != null ? resident.getEmail() : null);
        notification.setType(type);
        notification.setChannel(resident != null && resident.getEmail() != null && !resident.getEmail().isBlank()
            ? FinancialNotification.Channel.EMAIL
            : FinancialNotification.Channel.SYSTEM);
        notification.setStatus(FinancialNotification.Status.LOGGED);
        notification.setMessage(message);
        notification.setMetadata(new LinkedHashMap<>(metadata));
        notification.setCreatedAt(Instant.now());
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<FinancialNotification> listInvoiceNotifications(Long invoiceId) {
        return notificationRepository.findTop20ByTenantIdAndInvoiceIdOrderByCreatedAtDesc(TenantContext.get(), invoiceId);
    }

    @Transactional(readOnly = true)
    public boolean hasNotification(Long invoiceId, FinancialNotification.Type type) {
        return notificationRepository.existsByTenantIdAndInvoiceIdAndType(TenantContext.get(), invoiceId, type);
    }

    private Resident findPrimaryResident(Invoice invoice) {
        List<Resident> residents = residentRepository.findByTenantIdAndCondominiumIdAndUnitIdIn(
            invoice.getTenantId(),
            invoice.getCondominiumId(),
            List.of(invoice.getUnitId())
        );
        return residents.isEmpty() ? null : residents.get(0);
    }
}
