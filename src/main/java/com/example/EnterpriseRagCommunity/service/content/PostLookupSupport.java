package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PostLookupSupport {

    private PostLookupSupport() {
    }

    public static Map<Long, PostsEntity> loadPublishedPostsById(Collection<Long> postIds, PostsRepository postsRepository) {
        if (postIds == null || postIds.isEmpty() || postsRepository == null) return Map.of();
        List<Long> ids = postIds.stream().filter(Objects::nonNull).filter(id -> id > 0).toList();
        if (ids.isEmpty()) return Map.of();

        Map<Long, PostsEntity> byId = new HashMap<>();
        for (PostsEntity p : postsRepository.findByIdInAndIsDeletedFalseAndStatus(ids, PostStatus.PUBLISHED)) {
            if (p == null || p.getId() == null) continue;
            byId.put(p.getId(), p);
        }
        return byId;
    }
}
