package com.example.NewsPublishingSystem.service.impl;

import com.example.NewsPublishingSystem.dto.NewsDTOs;
import com.example.NewsPublishingSystem.dto.NewsDTOs.NewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.CreateNewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.UpdateNewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.SearchNewsCriteriaDTO;
import com.example.NewsPublishingSystem.entity.News;
import com.example.NewsPublishingSystem.entity.Topic;
import com.example.NewsPublishingSystem.entity.Administrator;
import com.example.NewsPublishingSystem.repository.NewsRepository;
import com.example.NewsPublishingSystem.repository.TopicRepository;
import com.example.NewsPublishingSystem.repository.AdministratorRepository;
import com.example.NewsPublishingSystem.service.NewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;
    private final TopicRepository topicRepository;
    private final AdministratorRepository administratorRepository;

    @Autowired
    public NewsServiceImpl(
            NewsRepository newsRepository,
            TopicRepository topicRepository,
            AdministratorRepository administratorRepository) {
        this.newsRepository = newsRepository;

        this.topicRepository = topicRepository;
        this.administratorRepository = administratorRepository;
    }

    @Override
    @Transactional
    public NewsDTO createNews(CreateNewsDTO createNewsDTO, Long authorId) {
        Topic topic = topicRepository.findById(createNewsDTO.getTopicId())
                .orElseThrow(() -> new NoSuchElementException("Topic not found"));

        Administrator author = administratorRepository.findById(authorId)
                .orElseThrow(() -> new NoSuchElementException("Author not found"));

        LocalDateTime now = LocalDateTime.now();

        News news = News.builder()
                .title(createNewsDTO.getTitle())
                .summary(createNewsDTO.getSummary())
                .contentText(createNewsDTO.getContent())
                .contentHtml(createNewsDTO.getContent()) // 可能需要转换为HTML格式
                .topic(topic)
                .author(author)
                .publishDate(now)
                .status(createNewsDTO.getStatus())
                .viewCount(0L)
                .likeCount(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        News savedNews = newsRepository.save(news);
        return NewsDTO.fromEntity(savedNews);
    }

    @Override
    public NewsDTO getNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("News not found"));
        return NewsDTO.fromEntity(news);
    }

    @Override
    @Transactional
    public NewsDTO updateNews(Long id, UpdateNewsDTO updateNewsDTO) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("News not found"));

        if (updateNewsDTO.getTitle() != null) {
            news.setTitle(updateNewsDTO.getTitle());
        }

        if (updateNewsDTO.getContent() != null) {
            news.setContentText(updateNewsDTO.getContent());
            news.setContentHtml(updateNewsDTO.getContent()); // 可能需要转换为HTML格式
        }

        if (updateNewsDTO.getSummary() != null) {
            news.setSummary(updateNewsDTO.getSummary());
        }

        if (updateNewsDTO.getStatus() != null) {
            news.setStatus(updateNewsDTO.getStatus());
        }

        if (updateNewsDTO.getTopicId() != null && !updateNewsDTO.getTopicId().equals(news.getTopic().getId())) {
            Topic topic = topicRepository.findById(updateNewsDTO.getTopicId())
                    .orElseThrow(() -> new NoSuchElementException("Topic not found"));
            news.setTopic(topic);
        }

        news.setUpdatedAt(LocalDateTime.now());
        News updatedNews = newsRepository.save(news);
        return NewsDTO.fromEntity(updatedNews);
    }

    @Override
    @Transactional
    public void deleteNews(Long id) {
        if (!newsRepository.existsById(id)) {
            throw new NoSuchElementException("News not found");
        }
        newsRepository.deleteById(id);
    }

    @Override
    public Page<NewsDTO> getAllNews(Pageable pageable) {
        return newsRepository.findAll(pageable)
                .map(NewsDTO::fromEntity);
    }

    @Override
    public Page<NewsDTO> getNewsByStatus(String status, Pageable pageable) {
        return newsRepository.findAllByStatus(status, pageable)
                .map(NewsDTO::fromEntity);
    }

    @Override
    public Page<NewsDTO> getNewsByTopicId(Long topicId, Pageable pageable) {
        return newsRepository.findAllByTopicId(topicId, pageable)
                .map(NewsDTO::fromEntity);
    }

    @Override
    public Page<NewsDTO> searchNewsByTitle(String keyword, Pageable pageable) {
        return newsRepository.findByTitleContaining(keyword, pageable)
                .map(NewsDTO::fromEntity);
    }

    @Override
    public List<NewsDTO> searchNewsByAuthorName(String authorName) {
        return newsRepository.findByAuthor_AccountContaining(authorName)
                .stream()
                .map(NewsDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NewsDTO> advancedSearch(SearchNewsCriteriaDTO criteria, Pageable pageable) {
        Specification<News> spec = Specification.allOf();

        // 这里实现根据搜索条件构建Specification
        // 由于实现较复杂，这里只提供示例实现
        // 实际项目中可以根据需求进行完善

        return newsRepository.findAll(spec, pageable)
                .map(NewsDTO::fromEntity);
    }
}
