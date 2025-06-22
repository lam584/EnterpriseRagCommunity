package com.example.NewsPublishingSystem.controller;

import com.example.NewsPublishingSystem.dto.NewsDTOs;
import com.example.NewsPublishingSystem.dto.NewsDTOs.NewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.CreateNewsDTO;
import com.example.NewsPublishingSystem.dto.NewsDTOs.UpdateNewsDTO;
import com.example.NewsPublishingSystem.service.NewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    @Autowired
    public NewsController(NewsService service) {
        this.newsService = service;
    }

    @PostMapping
    public ResponseEntity<NewsDTO> createNews(@RequestBody CreateNewsDTO dto) {
        // 简化：从 SecurityContext 获取 authorId，可改为真实逻辑
        Long authorId = 1L;
        NewsDTO created = newsService.createNews(dto, authorId);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNewsList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long topicId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<NewsDTO> content;
        long totalElements;
        int totalPages;

        var pageResult = (status != null && !status.isEmpty())
                ? newsService.getNewsByStatus(status, pageable)
                : topicId != null
                ? newsService.getNewsByTopicId(topicId, pageable)
                : newsService.getAllNews(pageable);

        content = pageResult.getContent();
        totalElements = pageResult.getTotalElements();
        totalPages = pageResult.getTotalPages();

        Map<String, Object> resp = new HashMap<>();
        resp.put("content", content);
        resp.put("totalElements", totalElements);
        resp.put("totalPages", totalPages);
        resp.put("size", pageResult.getSize());
        resp.put("number", pageResult.getNumber());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewsDTO> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(newsService.getNewsById(id));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewsDTO> update(@PathVariable Long id,
                                          @RequestBody UpdateNewsDTO dto) {
        try {
            return ResponseEntity.ok(newsService.updateNews(id, dto));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            newsService.deleteNews(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 通用搜索：searchField=title|author|id, keyword=…
     * 返回 List<NewsDTO>
     */
    @GetMapping("/search")
    public ResponseEntity<List<NewsDTO>> search(
            @RequestParam String searchField,
            @RequestParam String keyword) {

        List<NewsDTO> result;
        switch (searchField) {
            case "id":
                try {
                    Long id = Long.parseLong(keyword);
                    result = Collections.singletonList(newsService.getNewsById(id));
                } catch (Exception e) {
                    result = Collections.emptyList();
                }
                break;
            case "author":
                result = newsService.searchNewsByAuthorName(keyword);
                break;
            case "title":
            default:
                var pg = newsService.searchNewsByTitle(
                        keyword,
                        PageRequest.of(0, 100, Sort.by("updatedAt").descending()));
                result = pg.getContent();
                break;
        }
        return ResponseEntity.ok(result);
    }
    /**
     * 基本搜索：title, author, topicId, status
     */
    @GetMapping("/search/basic")
    public ResponseEntity<Map<String,Object>> searchBasic(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        NewsDTOs.SearchNewsCriteriaDTO c = new NewsDTOs.SearchNewsCriteriaDTO();
        c.setTitle(title);
        c.setAuthor(author);
        if (topicId != null) c.setCategoryId(topicId.toString());
        c.setStatus(status);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<NewsDTO> pr = newsService.advancedSearch(c, pageable);

        Map<String,Object> resp = new HashMap<>();
        resp.put("content", pr.getContent());
        resp.put("totalElements", pr.getTotalElements());
        resp.put("totalPages", pr.getTotalPages());
        resp.put("size", pr.getSize());
        resp.put("number", pr.getNumber());
        return ResponseEntity.ok(resp);
    }

    /**
     * 高级搜索：支持所有字段
     */
    @GetMapping("/search/advanced")
    public ResponseEntity<Map<String,Object>> searchAdvanced(
            @ModelAttribute NewsDTOs.SearchNewsCriteriaDTO criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<NewsDTO> pr = newsService.advancedSearch(criteria, pageable);

        Map<String,Object> resp = new HashMap<>();
        resp.put("content", pr.getContent());
        resp.put("totalElements", pr.getTotalElements());
        resp.put("totalPages", pr.getTotalPages());
        resp.put("size", pr.getSize());
        resp.put("number", pr.getNumber());
        return ResponseEntity.ok(resp);
    }
}