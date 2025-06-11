package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 该接口用于定义公告实体的数据库操作
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    // 根据管理员 ID 查找相关公告
    List<Announcement> findByAdministratorId(Long administratorId);

    // 查找在指定时间范围内创建的公告
    List<Announcement> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    // 查找标题中包含指定关键字的公告
    List<Announcement> findByTitleContaining(String keyword);
}
