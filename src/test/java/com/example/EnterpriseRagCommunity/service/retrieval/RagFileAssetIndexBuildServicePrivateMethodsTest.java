package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagFileAssetIndexBuildServicePrivateMethodsTest {

    @Test
    void httpHelpers_shouldCaptureRequests_andHandleStatusCodes() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"ok\":true}");
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");
        MockHttpUrl.enqueue(404, "{\"error\":\"missing\"}");
        MockHttpUrl.enqueue(500, "{\"error\":\"oops\"}");

        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/,mockhttp://backup");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");

        RagFileAssetIndexBuildService svc = new RagFileAssetIndexBuildService(
                mock(VectorIndicesRepository.class),
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                mock(PostAttachmentsRepository.class),
                mock(AiEmbeddingService.class),
                mock(LlmRoutingService.class),
                mock(RagFileAssetsIndexService.class),
                mock(ElasticsearchTemplate.class),
                systemConfigurationService
        );

        assertDoesNotThrow(() -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, "idx", "{\"query\":{\"match_all\":{}}}"));
        IllegalStateException ex1 = assertThrows(IllegalStateException.class,
                () -> invokePrivate(svc, "deleteByQuery", new Class[]{String.class, String.class}, "idx", "{\"query\":{\"match_all\":{}}}"));
        assertTrue(ex1.getMessage().contains("delete-by-query"));

        assertDoesNotThrow(() -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, "idx"));
        IllegalStateException ex2 = assertThrows(IllegalStateException.class,
                () -> invokePrivate(svc, "deleteIndexViaHttp", new Class[]{String.class}, "idx"));
        assertTrue(ex2.getMessage().contains("delete index"));

        MockHttpUrl.RequestCapture r1 = MockHttpUrl.pollRequest();
        MockHttpUrl.RequestCapture r2 = MockHttpUrl.pollRequest();
        MockHttpUrl.RequestCapture r3 = MockHttpUrl.pollRequest();
        MockHttpUrl.RequestCapture r4 = MockHttpUrl.pollRequest();
        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);
        assertNotNull(r4);
        assertEquals("POST", r1.method());
        assertEquals("POST", r2.method());
        assertEquals("DELETE", r3.method());
        assertEquals("DELETE", r4.method());
        assertNotNull(r1.headers().get("Content-Type"));
        assertEquals("ApiKey k1", r1.headers().get("Authorization"));
        assertTrue(new String(r1.body()).contains("match_all"));
    }

    @Test
    void helperMethods_shouldCoverBranches() throws Exception {
        Method toLong = RagFileAssetIndexBuildService.class.getDeclaredMethod("toLong", Object.class);
        toLong.setAccessible(true);
        assertEquals(12L, toLong.invoke(null, 12));
        assertEquals(34L, toLong.invoke(null, " 34 "));
        assertNull(toLong.invoke(null, " "));
        assertNull(toLong.invoke(null, "x1"));
        assertNull(toLong.invoke(null, new Object()));

        Method toNonBlankString = RagFileAssetIndexBuildService.class.getDeclaredMethod("toNonBlankString", Object.class);
        toNonBlankString.setAccessible(true);
        assertNull(toNonBlankString.invoke(null, (Object) null));
        assertNull(toNonBlankString.invoke(null, " "));
        assertEquals("x", toNonBlankString.invoke(null, " x "));
        assertEquals("123", toNonBlankString.invoke(null, 123));

        Method validateEmbeddingDims = RagFileAssetIndexBuildService.class.getDeclaredMethod("validateEmbeddingDims", Integer.class, Integer.class);
        validateEmbeddingDims.setAccessible(true);
        assertDoesNotThrow(() -> validateEmbeddingDims.invoke(null, null, 768));
        assertDoesNotThrow(() -> validateEmbeddingDims.invoke(null, 0, 768));
        assertDoesNotThrow(() -> validateEmbeddingDims.invoke(null, 768, null));
        assertDoesNotThrow(() -> validateEmbeddingDims.invoke(null, 768, 0));
        assertDoesNotThrow(() -> validateEmbeddingDims.invoke(null, 768, 768));
        try {
            validateEmbeddingDims.invoke(null, 768, 1024);
            fail();
        } catch (InvocationTargetException ite) {
            assertNotNull(ite.getCause());
            assertTrue(ite.getCause() instanceof IllegalStateException);
            assertTrue(ite.getCause().getMessage().contains("embedding dims mismatch"));
        }

        Method splitWithOverlap = RagFileAssetIndexBuildService.class.getDeclaredMethod("splitWithOverlap", String.class, int.class, int.class);
        splitWithOverlap.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> a = (List<String>) splitWithOverlap.invoke(null, null, 10, 2);
        assertEquals(0, a.size());
        @SuppressWarnings("unchecked")
        List<String> b = (List<String>) splitWithOverlap.invoke(null, "abcdef", 3, -1);
        assertEquals(List.of("abc", "def"), b);
        @SuppressWarnings("unchecked")
        List<String> c = (List<String>) splitWithOverlap.invoke(null, "abcdef", 3, 99);
        assertEquals(List.of("abc", "bcd", "cde", "def"), c);

        FileAssetsEntity fileAsset = new FileAssetsEntity();
        fileAsset.setId(1L);
        fileAsset.setOriginalName(" report.pdf ");
        fileAsset.setMimeType(" application/pdf ");
        fileAsset.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        UsersEntity owner = new UsersEntity();
        owner.setId(8L);
        fileAsset.setOwner(owner);

        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setUpdatedAt(LocalDateTime.of(2024, 2, 3, 4, 5, 6));

        Method buildFileAssetDocument = RagFileAssetIndexBuildService.class.getDeclaredMethod(
                "buildFileAssetDocument",
                String.class,
                Long.class,
                FileAssetsEntity.class,
                FileAssetExtractionsEntity.class,
                List.class,
                int.class,
                String.class,
                String.class,
                float[].class
        );
        buildFileAssetDocument.setAccessible(true);
        Document d = (Document) buildFileAssetDocument.invoke(
                null,
                "doc-1",
                9L,
                fileAsset,
                extraction,
                List.of(10L, 11L),
                2,
                "hash-x",
                "content",
                new float[]{0.1f, 0.2f}
        );
        assertEquals("doc-1", d.get("id"));
        assertEquals(9L, d.get("file_asset_id"));
        assertEquals(8L, d.get("owner_user_id"));
        assertEquals(List.of(10L, 11L), d.get("post_ids"));
        assertEquals(2, d.get("chunk_index"));
        assertEquals("hash-x", d.get("content_hash"));
        assertEquals("report.pdf", d.get("file_name"));
        assertEquals("application/pdf", d.get("mime_type"));
        assertEquals("content", d.get("content_text"));
        assertNotNull(d.get("created_at"));
        assertNotNull(d.get("updated_at"));
        assertNotNull(d.get("embedding"));

        Method summarizeException = RagFileAssetIndexBuildService.class.getDeclaredMethod("summarizeException", Throwable.class);
        summarizeException.setAccessible(true);
        assertEquals("error", summarizeException.invoke(null, (Object) null));
        assertEquals("RuntimeException", summarizeException.invoke(null, new RuntimeException((String) null)));
        assertEquals("IllegalArgumentException", summarizeException.invoke(null, new IllegalArgumentException(" ")));
        assertEquals("x", summarizeException.invoke(null, new IllegalStateException(" x ")));
        assertEquals(500, ((String) summarizeException.invoke(null, new Exception("x".repeat(501)))).length());
    }

    private static Object invokePrivate(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception ex) throw ex;
            if (c instanceof Error err) throw err;
            throw e;
        }
    }
}
