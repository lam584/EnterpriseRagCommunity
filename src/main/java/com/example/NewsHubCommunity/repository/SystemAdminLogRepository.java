package com.example.NewsHubCommunity.repository;

import com.example.NewsHubCommunity.entity.SystemAdminLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemAdminLogRepository extends JpaRepository<SystemAdminLog, Long> {
    Page<SystemAdminLog> findAllByAdminId(Long adminId, Pageable pageable);
    Page<SystemAdminLog> findAllByLevel(String level, Pageable pageable);
}
