package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.HelpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// 该接口用于定义帮助文章实体的数据库操作
@Repository
public interface HelpArticleRepository extends JpaRepository<HelpArticle, Long> {
    // 根据管理员 ID 查找帮助文章
    List<HelpArticle> findByAdministratorId(Long administratorId);

    // 根据标题中包含的关键字查找帮助文章
    List<HelpArticle> findByTitleContaining(String keyword);

    // 根据内容文本中包���的关键字查找帮助文章
    List<HelpArticle> findByContentTextContaining(String keyword);
}
