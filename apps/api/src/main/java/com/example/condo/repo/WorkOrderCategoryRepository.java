package com.example.condo.repo;

import com.example.condo.entity.WorkOrderCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderCategoryRepository extends JpaRepository<WorkOrderCategory, Long> {
    List<WorkOrderCategory> findAllByOrderBySortOrderAsc();
}
