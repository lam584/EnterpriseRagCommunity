package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface PostsService {
    PostsEntity publish(PostsPublishDTO dto);

    Page<PostsEntity> query(String keyword,
                           Long postId,
                           String searchMode,
                           Long boardId,
                           PostStatus status,
                           Long authorId,
                           LocalDate createdFrom,
                           LocalDate createdTo,
                           int page,
                           int pageSize,
                           String sortBy,
                           String sortOrderDirection);

    PostsEntity updateStatus(Long id, PostStatus status);

    PostsEntity update(Long id, com.example.EnterpriseRagCommunity.dto.content.PostsUpdateDTO dto);

    /**
     * 获取帖子详情（未删除）。
     *
     * @throws IllegalArgumentException 当帖子不存在或已删除
     */
    PostsEntity getById(Long id);
}
