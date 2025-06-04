package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByAdministratorId(Long administratorId);
    List<Announcement> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<Announcement> findByTitleContaining(String keyword);
}
