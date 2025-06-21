package com.example.NewsPublishingSystem.repository;

import com.example.NewsPublishingSystem.entity.SystemUserLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemUserLogRepository extends JpaRepository<SystemUserLog, Long> {
    Page<SystemUserLog> findAllByUserId(Long userId, Pageable pageable);
    Page<SystemUserLog> findAllByLevel(String level, Pageable pageable);
}