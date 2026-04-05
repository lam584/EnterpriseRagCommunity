package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostLookupSupportTest {

    @Test
    void loadPublishedPostsById_shouldFilterInvalidIdsAndCollectById() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PostsEntity p1 = new PostsEntity();
        p1.setId(1L);
        PostsEntity p2 = new PostsEntity();
        p2.setId(2L);
        p2.setStatus(PostStatus.PUBLISHED);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(eq(List.of(1L, 2L)), eq(PostStatus.PUBLISHED)))
            .thenReturn(Arrays.asList(null, p1, p2));

        Map<Long, PostsEntity> out = PostLookupSupport.loadPublishedPostsById(Arrays.asList(null, 0L, 1L, 2L), postsRepository);

        assertEquals(2, out.size());
        assertTrue(out.containsKey(1L));
        assertTrue(out.containsKey(2L));
        verify(postsRepository).findByIdInAndIsDeletedFalseAndStatus(eq(List.of(1L, 2L)), eq(PostStatus.PUBLISHED));
    }
}
