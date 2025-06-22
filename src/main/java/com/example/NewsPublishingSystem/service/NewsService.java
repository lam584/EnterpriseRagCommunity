package com.example.NewsPublishingSystem.service;

import com.example.NewsPublishingSystem.dto.NewsDTOs;
import com.example.NewsPublishingSystem.dto.NewsDTOs.NewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.CreateNewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.UpdateNewsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NewsService {
    /**
     * 创建新闻
     *
     * @param createNewsDTO 创建新闻DTO
     * @param authorId 作者ID
     * @return 创建的新闻DTO
     */
    NewsDTO createNews(CreateNewsDTO createNewsDTO, Long authorId);

    /**
     * 根据ID获取新闻
     *
     * @param id 新闻ID
     * @return 新闻DTO
     */
    NewsDTO getNewsById(Long id);

    /**
     * 更新新闻
     *
     * @param id 新闻ID
     * @param updateNewsDTO 更新新闻DTO
     * @return 更新后的新闻DTO
     */
    NewsDTO updateNews(Long id, UpdateNewsDTO updateNewsDTO);

    /**
     * 删除新闻
     *
     * @param id 新闻ID
     */
    void deleteNews(Long id);

    /**
     * 获取所有新闻（分页）
     *
     * @param pageable 分页参数
     * @return 分页新闻列表
     */
    Page<NewsDTO> getAllNews(Pageable pageable);

    /**
     * 根据状态获取新闻（分页）
     *
     * @param status 状态
     * @param pageable 分页参数
     * @return 分页新闻列表
     */
    Page<NewsDTO> getNewsByStatus(String status, Pageable pageable);

    /**
     * 根据主题ID获取新闻（分页）
     *
     * @param topicId 主题ID
     * @param pageable 分页参数
     * @return 分页新闻列表
     */
    Page<NewsDTO> getNewsByTopicId(Long topicId, Pageable pageable);

    /**
     * 搜索新闻（按标题）
     *
     * @param keyword 关键字
     * @param pageable 分页参数
     * @return 分页新闻列表
     */
    Page<NewsDTO> searchNewsByTitle(String keyword, Pageable pageable);

    List<NewsDTO> searchNewsByAuthorName(String authorName);
    Page<NewsDTO> advancedSearch(NewsDTOs.SearchNewsCriteriaDTO criteria, Pageable pageable);

}
