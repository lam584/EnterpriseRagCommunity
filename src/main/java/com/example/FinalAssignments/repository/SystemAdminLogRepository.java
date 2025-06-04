package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.SystemAdminLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemAdminLogRepository extends JpaRepository<SystemAdminLog, Long> {
    List<SystemAdminLog> findByLevel(String level);
    List<SystemAdminLog> findByAdministratorId(Long administratorId);
    List<SystemAdminLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<SystemAdminLog> findByIp(String ip);
    List<SystemAdminLog> findByMessageContaining(String keyword);
}
