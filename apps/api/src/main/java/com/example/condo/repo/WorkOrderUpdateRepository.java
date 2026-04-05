package com.example.condo.repo;

import com.example.condo.entity.WorkOrderUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderUpdateRepository extends JpaRepository<WorkOrderUpdate, Long> {
    List<WorkOrderUpdate> findByWorkOrderIdOrderByCreatedAtAsc(Long workOrderId);
}
