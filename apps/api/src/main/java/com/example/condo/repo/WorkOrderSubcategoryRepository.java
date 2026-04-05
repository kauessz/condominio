package com.example.condo.repo;

import com.example.condo.entity.WorkOrderSubcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderSubcategoryRepository extends JpaRepository<WorkOrderSubcategory, Long> {
    List<WorkOrderSubcategory> findByCategoryIdOrderBySortOrderAsc(Long categoryId);
}
