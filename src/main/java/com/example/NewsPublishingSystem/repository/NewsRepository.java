package com.example.NewsPublishingSystem.repository;

import com.example.NewsPublishingSystem.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsRepository
        extends JpaRepository<News, Long>, JpaSpecificationExecutor<News> {
    Page<News> findAllByTopicId(Long topicId, Pageable pageable);
    Page<News> findByTitleContaining(String keyword, Pageable pageable);
    Page<News> findAllByStatus(String status, Pageable pageable);
    List<News> findByAuthor_AccountContaining(String account);
}