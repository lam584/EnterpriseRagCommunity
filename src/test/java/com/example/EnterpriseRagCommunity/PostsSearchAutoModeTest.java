package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.content.impl.PostsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostsSearchAutoModeTest {

    private PostsServiceImpl postsService;
    private PostsRepository postsRepository;

    @BeforeEach
    void setUp() throws Exception {
        postsService = new PostsServiceImpl();
        postsRepository = mock(PostsRepository.class);

        Field f = PostsServiceImpl.class.getDeclaredField("postsRepository");
        f.setAccessible(true);
        f.set(postsService, postsRepository);
    }

    @Test
    void autoMode_shouldFallbackToLike_forMidSubstringNumericKeyword() {
        when(postsRepository.searchLikeOrderByCreatedAtDesc(eq("23456"), any(Pageable.class)))
                .thenReturn(Page.empty());

        postsService.query(
                "23456",
                null,
                null, // AUTO
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        );

        verify(postsRepository, times(1)).searchLikeOrderByCreatedAtDesc(eq("23456"), any(Pageable.class));
        verify(postsRepository, never()).searchFullTextOrderByCreatedAtDesc(anyString(), any(Pageable.class));
    }

    @Test
    void fullTextMode_shouldUseFullText_forKeyword() {
        when(postsRepository.searchFullTextOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());

        postsService.query(
                "1234",
                null,
                "FULLTEXT",
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        );

        verify(postsRepository, times(1)).searchFullTextOrderByCreatedAtDesc(eq("1234*"), any(Pageable.class));
        verify(postsRepository, never()).searchLikeOrderByCreatedAtDesc(anyString(), any(Pageable.class));
    }

    @Test
    void autoMode_shouldFallbackToLike_forLongSingleTokenKeyword() {
        when(postsRepository.searchLikeOrderByCreatedAtDesc(eq("abcdef"), any(Pageable.class)))
                .thenReturn(Page.empty());

        postsService.query(
                "abcdef",
                null,
                "AUTO",
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        );

        verify(postsRepository, times(1)).searchLikeOrderByCreatedAtDesc(eq("abcdef"), any(Pageable.class));
        verify(postsRepository, never()).searchFullTextOrderByCreatedAtDesc(anyString(), any(Pageable.class));
    }

    @Test
    void autoMode_shouldUseFullText_forMultiTokenKeyword() {
        when(postsRepository.searchFullTextOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());

        postsService.query(
                "hello world",
                null,
                "AUTO",
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        );

        verify(postsRepository, times(1)).searchFullTextOrderByCreatedAtDesc(eq("hello world*"), any(Pageable.class));
        verify(postsRepository, never()).searchLikeOrderByCreatedAtDesc(anyString(), any(Pageable.class));
    }

    @Test
    void autoMode_shouldUseFullText_forShortLetterToken_likeWre() {
        when(postsRepository.searchFullTextOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());

        postsService.query(
                "wre",
                null,
                "AUTO",
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        );

        verify(postsRepository, times(1)).searchFullTextOrderByCreatedAtDesc(eq("wre*"), any(Pageable.class));
        verify(postsRepository, never()).searchLikeOrderByCreatedAtDesc(anyString(), any(Pageable.class));
    }

    @Test
    void autoMode_shouldFallbackToLike_forMixedAlphaNumericToken() {
        when(postsRepository.searchLikeOrderByCreatedAtDesc(eq("tyujyswreb2025"), any(Pageable.class)))
                .thenReturn(Page.empty());

        postsService.query(
                "tyujyswreb2025",
                null,
                "AUTO",
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        );

        verify(postsRepository, times(1)).searchLikeOrderByCreatedAtDesc(eq("tyujyswreb2025"), any(Pageable.class));
        verify(postsRepository, never()).searchFullTextOrderByCreatedAtDesc(anyString(), any(Pageable.class));
    }
}
