package com.example.condo.repo;

import com.example.condo.entity.ParkingDrawRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingDrawRegistrationRepository extends JpaRepository<ParkingDrawRegistration, Long> {

    List<ParkingDrawRegistration> findByDrawId(Long drawId);

    Optional<ParkingDrawRegistration> findByDrawIdAndUnitId(Long drawId, Long unitId);

    boolean existsByDrawIdAndUnitId(Long drawId, Long unitId);
}
