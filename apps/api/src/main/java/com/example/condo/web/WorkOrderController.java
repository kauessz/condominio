package com.example.condo.web;

import com.example.condo.dto.common.PageResponse;
import com.example.condo.dto.workorder.WorkOrderListItemResponse;
import com.example.condo.entity.WorkOrder;
import com.example.condo.entity.WorkOrderCategory;
import com.example.condo.entity.WorkOrderSubcategory;
import com.example.condo.entity.WorkOrderUpdate;
import com.example.condo.exception.BusinessException;
import com.example.condo.service.WorkOrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping("/work-order-categories")
    public List<WorkOrderCategory> listCategories() {
        return service.listCategories();
    }

    @GetMapping("/work-order-categories/{categoryId}/subcategories")
    public List<WorkOrderSubcategory> listSubcategories(@PathVariable Long categoryId) {
        return service.listSubcategories(categoryId);
    }

    @GetMapping("/work-orders")
    public PageResponse<WorkOrderListItemResponse> list(
        @RequestParam(required = false) Long condominiumId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<WorkOrderListItemResponse> p = service.listOrderCards(condominiumId, status, unitId, categoryId, PageRequest.of(page, size));
        return PageResponse.of(p);
    }

    @GetMapping("/work-orders/{id}")
    public WorkOrder get(@PathVariable Long id) {
        return service.getOrder(id);
    }

    @GetMapping("/work-orders/{id}/updates")
    public List<WorkOrderUpdate> getUpdates(@PathVariable Long id) {
        return service.getUpdates(id);
    }

    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrder create(@RequestBody Map<String, Object> body) {
        Long condoId = body.get("condominiumId") != null ? ((Number) body.get("condominiumId")).longValue() : null;
        Long categoryId = requiredLong(body, "categoryId");
        Long subcategoryId = body.get("subcategoryId") != null ? ((Number) body.get("subcategoryId")).longValue() : null;
        String title = requiredString(body, "title");
        String description = requiredString(body, "description");
        String priority = (String) body.get("priority");
        Long unitId = body.get("unitId") != null ? ((Number) body.get("unitId")).longValue() : null;
        return service.createOrder(condoId, categoryId, subcategoryId, title, description, priority, unitId);
    }

    @PatchMapping("/work-orders/{id}/status")
    public WorkOrder updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        String comment = (String) body.get("comment");
        Long assignedTo = body.get("assignedTo") != null ? ((Number) body.get("assignedTo")).longValue() : null;
        return service.updateStatus(id, status, comment, assignedTo);
    }

    private Long requiredLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Number number)) {
            throw new BusinessException(field + " é obrigatório");
        }
        return number.longValue();
    }

    private String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessException(field + " é obrigatório");
        }
        return text;
    }
}
