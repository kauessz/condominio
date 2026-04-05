package com.example.condo.service;

import com.example.condo.entity.WorkOrder;
import com.example.condo.repo.WorkOrderCategoryRepository;
import com.example.condo.repo.WorkOrderRepository;
import com.example.condo.repo.WorkOrderSubcategoryRepository;
import com.example.condo.repo.WorkOrderUpdateRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private WorkOrderCategoryRepository categoryRepository;

    @Mock
    private WorkOrderSubcategoryRepository subcategoryRepository;

    @Mock
    private WorkOrderUpdateRepository updateRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private WorkOrderService workOrderService;

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
    void superuserShouldListOrdersAcrossAllCondominiumsWhenNoCondoFilterIsProvided() {
        UserContext.set(new UserContext.Data("SUPERUSER", null, null, 1L));
        when(workOrderRepository.searchAllCondos(eq("tenant-a"), eq(null), eq(null), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of(new WorkOrder())));

        var result = workOrderService.listOrders(null, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        verify(workOrderRepository).searchAllCondos(eq("tenant-a"), eq(null), eq(null), eq(null), any());
        verify(workOrderRepository, never()).search(eq("tenant-a"), eq(1L), eq(null), eq(null), eq(null), any());
    }
}
