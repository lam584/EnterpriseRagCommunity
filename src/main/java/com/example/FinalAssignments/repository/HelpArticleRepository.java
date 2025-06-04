package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.HelpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HelpArticleRepository extends JpaRepository<HelpArticle, Long> {
    List<HelpArticle> findByAdministratorId(Long administratorId);
    List<HelpArticle> findByTitleContaining(String keyword);
    List<HelpArticle> findByContentTextContaining(String keyword);
}
