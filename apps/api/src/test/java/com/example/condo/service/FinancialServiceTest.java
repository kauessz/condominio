package com.example.condo.service;

import com.example.condo.dto.financial.FinancialSummaryResponse;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.FinancialConfigRepository;
import com.example.condo.repo.InvoiceRepository;
import com.example.condo.repo.UnitRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock
    private FinancialConfigRepository configRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private CondominiumRepository condominiumRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private FinancialService financialService;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    @Test
    void superuserSummaryWithoutCondominiumShouldAggregateTenantInsteadOfReturningEmptyShape() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(invoiceRepository.summary("tenant-a", null, null, null))
            .thenReturn(new Object[]{2L, 300.0, 100.0, 120.0, 80.0});

        FinancialSummaryResponse summary = financialService.summary(null);

        assertEquals(2L, summary.totalInvoices());
        assertEquals(300.0, summary.totalAmount().doubleValue());
        assertEquals(100.0, summary.paidAmount().doubleValue());
        assertEquals(120.0, summary.pendingAmount().doubleValue());
        assertEquals(80.0, summary.overdueAmount().doubleValue());
        verify(invoiceRepository).summary("tenant-a", null, null, null);
    }
}
