package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryResponse;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentTestQueryService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetTestQueryService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostTestQueryService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminRetrievalVectorIndexController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminRetrievalVectorIndexControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository vectorIndicesRepository;

    @MockitoBean
    RagPostIndexBuildService buildService;

    @MockitoBean
    RagPostTestQueryService testQueryService;

    @MockitoBean
    RagCommentIndexBuildService commentBuildService;

    @MockitoBean
    RagCommentTestQueryService commentTestQueryService;

    @MockitoBean
    RagFileAssetIndexBuildService fileBuildService;

    @MockitoBean
    RagFileAssetTestQueryService fileTestQueryService;

    @MockitoBean
    AuditLogWriter auditLogWriter;

    @MockitoBean
    AuditDiffBuilder auditDiffBuilder;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());

        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        when(contentSafetyCircuitBreakerService.getConfig()).thenReturn(cfg);
    }

    @Test
    void update_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:access")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_shouldReturn400_whenIdMismatch() throws Exception {
        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("id mismatch"));
    }

    @Test
    void update_shouldReturn400_whenPayloadNull() throws Exception {
        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("payload is required"));
    }

    @Test
    void update_shouldReturn400_whenBeanValidationFails() throws Exception {
        String tooLong = "x".repeat(129);
        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"collectionName\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.collectionName").exists());
    }

    @Test
    void update_shouldReturn200_whenOk() throws Exception {
        VectorIndicesEntity e = new VectorIndicesEntity();
        e.setId(1L);
        e.setCollectionName("old");

        when(vectorIndicesRepository.findById(1L)).thenReturn(Optional.of(e));
        when(vectorIndicesRepository.save(any(VectorIndicesEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(Map.class), any(Map.class))).thenReturn(Map.of("changed", true));

        mockMvc.perform(put("/api/admin/retrieval/vector-indices/{id}", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"collectionName\":\"new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.collectionName").value("new"));

        verify(auditLogWriter).write(
                Mockito.isNull(),
                eq("u@example.com"),
                eq("RETRIEVAL_VECTOR_INDEX_UPDATE"),
                eq("VECTOR_INDEX"),
                eq(1L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                Mockito.isNull(),
                Mockito.isNull(),
                any()
        );
    }

    @Test
    void testQuery_shouldReturn200_whenOk() throws Exception {
        RagPostsTestQueryResponse resp = new RagPostsTestQueryResponse();
        RagPostsTestQueryResponse.Hit hit = new RagPostsTestQueryResponse.Hit();
        hit.setDocId("d1");
        resp.setHits(List.of(hit));
        when(testQueryService.testQuery(eq(1L), any())).thenReturn(resp);

        mockMvc.perform(post("/api/admin/retrieval/vector-indices/{id}/test-query", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(1))
                .andExpect(jsonPath("$.hits[0].docId").value("d1"));

        verify(auditLogWriter).write(
                Mockito.isNull(),
                eq("u@example.com"),
                eq("RETRIEVAL_RAG_TEST_QUERY"),
                eq("VECTOR_INDEX"),
                eq(1L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                Mockito.isNull(),
                Mockito.isNull(),
                any()
        );
    }

    @Test
    void testQueryComments_shouldReturn500_andAuditFail_whenServiceThrows() throws Exception {
        when(commentTestQueryService.testQuery(eq(1L), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/admin/retrieval/vector-indices/{id}/test-query/comments", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("系统处理请求时发生错误"));

        verify(auditLogWriter).write(
                Mockito.isNull(),
                eq("u@example.com"),
                eq("RETRIEVAL_RAG_TEST_QUERY_COMMENTS"),
                eq("VECTOR_INDEX"),
                eq(1L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.FAIL),
                eq("boom"),
                Mockito.isNull(),
                Mockito.isNull()
        );
    }

    @Test
    void testQueryFiles_shouldReturn400_whenServiceThrowsIllegalArgument() throws Exception {
        when(fileTestQueryService.testQuery(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("bad req"));

        mockMvc.perform(post("/api/admin/retrieval/vector-indices/{id}/test-query/files", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad req"));

        verify(auditLogWriter).write(
                Mockito.isNull(),
                eq("u@example.com"),
                eq("RETRIEVAL_RAG_TEST_QUERY_FILES"),
                eq("VECTOR_INDEX"),
                eq(1L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.FAIL),
                eq("bad req"),
                Mockito.isNull(),
                Mockito.isNull()
        );
    }

    @Test
    void testQueryFiles_shouldReturn200_whenOk() throws Exception {
        RagFilesTestQueryResponse resp = new RagFilesTestQueryResponse();
        RagFilesTestQueryResponse.Hit hit = new RagFilesTestQueryResponse.Hit();
        hit.setDocId("f1");
        resp.setHits(List.of(hit));
        when(fileTestQueryService.testQuery(eq(1L), any())).thenReturn(resp);

        mockMvc.perform(post("/api/admin/retrieval/vector-indices/{id}/test-query/files", 1)
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(1))
                .andExpect(jsonPath("$.hits[0].docId").value("f1"));

        verify(auditLogWriter).write(
                Mockito.isNull(),
                eq("u@example.com"),
                eq("RETRIEVAL_RAG_TEST_QUERY_FILES"),
                eq("VECTOR_INDEX"),
                eq(1L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                Mockito.isNull(),
                Mockito.isNull(),
                any()
        );
    }
}

