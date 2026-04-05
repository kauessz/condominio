package com.example.condo.repo;

import com.example.condo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
  @Query("select u from User u where u.tenantId = :t and lower(u.email) = lower(:email)")
  Optional<User> findByTenantAndEmail(@Param("t") String tenantId, @Param("email") String email);
}