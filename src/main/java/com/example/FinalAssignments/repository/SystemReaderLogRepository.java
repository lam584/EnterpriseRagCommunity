package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.SystemReaderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemReaderLogRepository extends JpaRepository<SystemReaderLog, Long> {
    List<SystemReaderLog> findByLevel(String level);
    List<SystemReaderLog> findByReaderId(Long readerId);
    List<SystemReaderLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<SystemReaderLog> findByIp(String ip);
    List<SystemReaderLog> findByMessageContaining(String keyword);
}
