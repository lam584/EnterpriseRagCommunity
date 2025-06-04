package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {
    Optional<AdminPermission> findByRoles(String roles);
    boolean existsByRoles(String roles);
}
