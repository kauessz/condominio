package com.example.condo.service;

import com.example.condo.dto.workorder.WorkOrderListItemResponse;
import com.example.condo.entity.Condominium;
import com.example.condo.entity.WorkOrder;
import com.example.condo.entity.WorkOrderCategory;
import com.example.condo.entity.WorkOrderSubcategory;
import com.example.condo.entity.WorkOrderUpdate;
import com.example.condo.exception.BusinessException;
import com.example.condo.exception.ResourceNotFoundException;
import com.example.condo.repo.WorkOrderCategoryRepository;
import com.example.condo.repo.CondominiumRepository;
import com.example.condo.repo.WorkOrderRepository;
import com.example.condo.repo.WorkOrderSubcategoryRepository;
import com.example.condo.repo.WorkOrderUpdateRepository;
import com.example.condo.tenant.TenantContext;
import com.example.condo.tenant.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepo;
    private final CondominiumRepository condominiumRepo;
    private final WorkOrderCategoryRepository categoryRepo;
    private final WorkOrderSubcategoryRepository subcategoryRepo;
    private final WorkOrderUpdateRepository updateRepo;
    private final AuditService auditService;

    public WorkOrderService(WorkOrderRepository workOrderRepo,
                             CondominiumRepository condominiumRepo,
                             WorkOrderCategoryRepository categoryRepo,
                             WorkOrderSubcategoryRepository subcategoryRepo,
                             WorkOrderUpdateRepository updateRepo,
                             AuditService auditService) {
        this.workOrderRepo = workOrderRepo;
        this.condominiumRepo = condominiumRepo;
        this.categoryRepo = categoryRepo;
        this.subcategoryRepo = subcategoryRepo;
        this.updateRepo = updateRepo;
        this.auditService = auditService;
    }

    public List<WorkOrderCategory> listCategories() {
        return categoryRepo.findAllByOrderBySortOrderAsc();
    }

    public List<WorkOrderSubcategory> listSubcategories(Long categoryId) {
        return subcategoryRepo.findByCategoryIdOrderBySortOrderAsc(categoryId);
    }

    public Page<WorkOrder> listOrders(Long condoIdParam, String statusStr, Long unitIdParam,
                                       Long categoryId, Pageable pageable) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);

        // MORADOR só vê suas OS
        Long effectiveUnitId = unitIdParam;
        if (isMorador()) effectiveUnitId = UserContext.unitId();

        WorkOrder.Status status = statusStr != null ? WorkOrder.Status.valueOf(statusStr.toUpperCase()) : null;
        if (UserContext.isSuperuser() && condoId == null) {
            return workOrderRepo.searchAllCondos(tenant, status, effectiveUnitId, categoryId, pageable);
        }
        if (condoId == null) return Page.empty(pageable);
        return workOrderRepo.search(tenant, condoId, status, effectiveUnitId, categoryId, pageable);
    }

    public Page<WorkOrderListItemResponse> listOrderCards(Long condoIdParam, String statusStr, Long unitIdParam,
                                                          Long categoryId, Pageable pageable) {
        Page<WorkOrder> orders = listOrders(condoIdParam, statusStr, unitIdParam, categoryId, pageable);
        if (orders.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<Long> condominiumIds = orders.getContent().stream()
            .map(WorkOrder::getCondominiumId)
            .collect(Collectors.toSet());
        Map<Long, String> condoNames = condominiumRepo.findAllById(condominiumIds).stream()
            .collect(Collectors.toMap(Condominium::getId, Condominium::getName));
        Map<Long, String> categoryNames = categoryRepo.findAllById(
                orders.getContent().stream().map(WorkOrder::getCategoryId).collect(Collectors.toSet())
            ).stream()
            .collect(Collectors.toMap(WorkOrderCategory::getId, WorkOrderCategory::getName));
        Map<Long, String> subcategoryNames = subcategoryRepo.findAllById(
                orders.getContent().stream()
                    .map(WorkOrder::getSubcategoryId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet())
            ).stream()
            .collect(Collectors.toMap(WorkOrderSubcategory::getId, WorkOrderSubcategory::getName));

        return orders.map(toListItem(condoNames, categoryNames, subcategoryNames));
    }

    public WorkOrder getOrder(Long id) {
        String tenant = TenantContext.get();
        WorkOrder wo = workOrderRepo.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new ResourceNotFoundException("Ordem de serviço", "id", id));
        enforceSameCondominium(wo.getCondominiumId());

        // MORADOR só pode ver as suas
        if (isMorador()) {
            Long myUnit = UserContext.unitId();
            if (!wo.getUnitId().equals(myUnit)) {
                throw new ResourceNotFoundException("Ordem de serviço", "id", id);
            }
        }
        return wo;
    }

    public List<WorkOrderUpdate> getUpdates(Long orderId) {
        getOrder(orderId); // valida acesso
        return updateRepo.findByWorkOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional
    public WorkOrder createOrder(Long condoIdParam, Long categoryId, Long subcategoryId,
                                  String title, String description, String priorityStr, Long unitIdParam) {
        String tenant = TenantContext.get();
        Long condoId = UserContext.resolveCondominiumId(condoIdParam);
        if (condoId == null) throw new BusinessException("condominiumId é obrigatório");

        // MORADOR: força sua unidade
        Long unitId = isMorador() ? UserContext.unitId() : unitIdParam;

        WorkOrderCategory cat = categoryRepo.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria", "id", categoryId));

        WorkOrderSubcategory sub = null;
        int slaHours = 48;
        if (subcategoryId != null) {
            sub = subcategoryRepo.findById(subcategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategoria", "id", subcategoryId));
            slaHours = sub.getSlaHours();
        }

        WorkOrder.Priority priority;
        try {
            priority = priorityStr != null ? WorkOrder.Priority.valueOf(priorityStr.toUpperCase()) : WorkOrder.Priority.MEDIUM;
        } catch (IllegalArgumentException e) {
            priority = WorkOrder.Priority.MEDIUM;
        }

        WorkOrder wo = new WorkOrder();
        wo.setTenantId(tenant);
        wo.setCondominiumId(condoId);
        wo.setUnitId(unitId);
        wo.setCategoryId(categoryId);
        wo.setSubcategoryId(subcategoryId);
        wo.setTitle(title.trim());
        wo.setDescription(description);
        wo.setStatus(WorkOrder.Status.OPEN);
        wo.setPriority(priority);
        wo.setSlaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS));
        wo.setCreatedBy(UserContext.userId());
        wo.setCreatedAt(Instant.now());
        wo.setUpdatedAt(Instant.now());
        wo = workOrderRepo.save(wo);

        // Update inicial
        addUpdate(wo.getId(), "Ordem de serviço aberta", null, null);
        auditService.log("CREATE", "WorkOrder", wo.getId(), wo.getCondominiumId(), null, wo);

        return wo;
    }

    @Transactional
    public WorkOrder updateStatus(Long id, String newStatusStr, String comment, Long assignedTo) {
        WorkOrder wo = getOrder(id);
        WorkOrder before = copyWorkOrder(wo);

        if (isMorador()) {
            // Morador só pode cancelar
            if (!"CANCELLED".equalsIgnoreCase(newStatusStr)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Moradores só podem cancelar OS");
            }
        }

        WorkOrder.Status newStatus;
        try {
            newStatus = WorkOrder.Status.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Status inválido: " + newStatusStr);
        }

        WorkOrder.Status old = wo.getStatus();
        wo.setStatus(newStatus);
        wo.setUpdatedAt(Instant.now());

        if (assignedTo != null) wo.setAssignedTo(assignedTo);

        if (newStatus == WorkOrder.Status.RESOLVED) wo.setResolvedAt(Instant.now());
        if (newStatus == WorkOrder.Status.CLOSED) wo.setClosedAt(Instant.now());

        wo = workOrderRepo.save(wo);

        String msg = comment != null ? comment : "Status alterado para " + newStatus.name();
        addUpdate(wo.getId(), msg, newStatus.name(), null);
        auditService.log("STATUS_CHANGE", "WorkOrder", wo.getId(), wo.getCondominiumId(), before, wo);

        return wo;
    }

    private void addUpdate(Long workOrderId, String content, String newStatus, Long authorId) {
        WorkOrderUpdate upd = new WorkOrderUpdate();
        upd.setWorkOrderId(workOrderId);
        upd.setAuthorId(authorId != null ? authorId : UserContext.userId());
        upd.setContent(content);
        upd.setNewStatus(newStatus);
        upd.setCreatedAt(Instant.now());
        updateRepo.save(upd);
    }

    private boolean isMorador() {
        UserContext.Data ctx = UserContext.get();
        return ctx != null && "MORADOR".equalsIgnoreCase(ctx.role());
    }

    private void enforceSameCondominium(Long condoId) {
        Long effective = UserContext.resolveCondominiumId(condoId);
        if (effective != null && !effective.equals(condoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
    }

    private WorkOrder copyWorkOrder(WorkOrder source) {
        WorkOrder copy = new WorkOrder();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setCondominiumId(source.getCondominiumId());
        copy.setUnitId(source.getUnitId());
        copy.setCategoryId(source.getCategoryId());
        copy.setSubcategoryId(source.getSubcategoryId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setStatus(source.getStatus());
        copy.setPriority(source.getPriority());
        copy.setAssignedTo(source.getAssignedTo());
        copy.setSlaDeadline(source.getSlaDeadline());
        copy.setResolvedAt(source.getResolvedAt());
        copy.setClosedAt(source.getClosedAt());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private Function<WorkOrder, WorkOrderListItemResponse> toListItem(
        Map<Long, String> condoNames,
        Map<Long, String> categoryNames,
        Map<Long, String> subcategoryNames
    ) {
        return workOrder -> new WorkOrderListItemResponse(
            workOrder.getId(),
            workOrder.getCondominiumId(),
            condoNames.getOrDefault(workOrder.getCondominiumId(), "Condomínio #" + workOrder.getCondominiumId()),
            workOrder.getUnitId(),
            workOrder.getCategoryId(),
            categoryNames.getOrDefault(workOrder.getCategoryId(), "#" + workOrder.getCategoryId()),
            workOrder.getSubcategoryId(),
            workOrder.getSubcategoryId() != null ? subcategoryNames.get(workOrder.getSubcategoryId()) : null,
            workOrder.getTitle(),
            workOrder.getDescription(),
            workOrder.getStatus().name(),
            workOrder.getPriority().name(),
            workOrder.getSlaDeadline(),
            workOrder.getCreatedAt()
        );
    }
}
