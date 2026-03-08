package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HybridRagRetrievalServiceFilterVisibleHitsBranchTest {

    @Test
    void filterVisibleHits_nullOrEmpty_returnsEmptyList() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                null,
                null,
                null,
                mock(PostsRepository.class),
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("filterVisibleHits", List.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out1 = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, new Object[] { null });
        assertNotNull(out1);
        assertEquals(0, out1.size());

        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out2 = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, List.of());
        assertNotNull(out2);
        assertEquals(0, out2.size());
    }

    @Test
    void filterVisibleHits_noPostIds_returnsOriginalHits() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                null,
                null,
                null,
                mock(PostsRepository.class),
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("filterVisibleHits", List.class);
        m.setAccessible(true);

        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setDocId("d");
        h.setPostId(null);
        List<HybridRagRetrievalService.DocHit> hits = new ArrayList<>();
        hits.add(h);
        hits.add(null);

        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, hits);
        assertSame(hits, out);
    }

    @Test
    void filterVisibleHits_filtersByPublishedPostIds() throws Exception {
        PostsRepository postsRepository = mock(PostsRepository.class);

        PostsEntity p1 = new PostsEntity();
        p1.setId(1L);
        PostsEntity pNullId = new PostsEntity();

        List<PostsEntity> found = new ArrayList<>();
        found.add(p1);
        found.add(pNullId);
        found.add(null);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(found);

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                null,
                null,
                null,
                postsRepository,
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("filterVisibleHits", List.class);
        m.setAccessible(true);

        HybridRagRetrievalService.DocHit h1 = new HybridRagRetrievalService.DocHit();
        h1.setDocId("d1");
        h1.setPostId(1L);
        HybridRagRetrievalService.DocHit h2 = new HybridRagRetrievalService.DocHit();
        h2.setDocId("d2");
        h2.setPostId(2L);

        List<HybridRagRetrievalService.DocHit> hits = new ArrayList<>();
        hits.add(h1);
        hits.add(h2);
        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> out = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, hits);
        assertEquals(1, out.size());
        assertEquals("d1", out.get(0).getDocId());
    }
}
