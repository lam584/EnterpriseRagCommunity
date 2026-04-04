package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsBuildResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsTestQueryResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesBuildResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsBuildResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryResponse;
import com.example.EnterpriseRagCommunity.dto.semantic.VectorIndicesCreateDTO;
import com.example.EnterpriseRagCommunity.dto.semantic.VectorIndicesUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentTestQueryService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetTestQueryService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostTestQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/retrieval/vector-indices")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRetrievalVectorIndexController {

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagPostIndexBuildService buildService;
    private final RagPostTestQueryService testQueryService;
    private final RagCommentIndexBuildService commentBuildService;
    private final RagCommentTestQueryService commentTestQueryService;
    private final RagFileAssetIndexBuildService fileBuildService;
    private final RagFileAssetTestQueryService fileTestQueryService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','access'))")
    public ResponseEntity<Page<VectorIndicesEntity>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int p = Math.max(0, page);
        int ps = Math.max(1, Math.min(200, size));
        return ResponseEntity.ok(vectorIndicesRepository.findAll(PageRequest.of(p, ps, Sort.by(Sort.Direction.DESC, "id"))));
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<VectorIndicesEntity> create(@RequestBody @Valid VectorIndicesCreateDTO dto, Principal principal) {
        VectorIndicesEntity e = new VectorIndicesEntity();
        e.setProvider(dto.getProvider());
        e.setCollectionName(dto.getCollectionName());
        e.setMetric(dto.getMetric());
        e.setDim(dto.getDim());
        e.setStatus(dto.getStatus() == null ? VectorIndexStatus.READY : dto.getStatus());
        e.setMetadata(dto.getMetadata());
        VectorIndicesEntity saved = vectorIndicesRepository.save(e);
        Map<String, Object> details = buildVectorIndexDetails(saved);
        auditLogWriter.write(null, principal == null ? null : principal.getName(),
                "RETRIEVAL_VECTOR_INDEX_CREATE", "VECTOR_INDEX", saved.getId(), AuditResult.SUCCESS, null, null,
                details);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        if (id == null || !vectorIndicesRepository.existsById(id)) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_VECTOR_INDEX_DELETE", "VECTOR_INDEX", id, AuditResult.FAIL, "not found", null, null);
            return ResponseEntity.notFound().build();
        }
        vectorIndicesRepository.deleteById(id);
        auditLogWriter.write(null, principal == null ? null : principal.getName(),
                "RETRIEVAL_VECTOR_INDEX_DELETE", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/build/posts")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagPostsBuildResponse> buildPosts(
            @PathVariable Long id,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "fromPostId", required = false) Long fromPostId,
            @RequestParam(value = "postBatchSize", required = false) Integer postBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "clear", required = false) Boolean clear,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagPostsBuildResponse resp = buildService.buildPosts(id, boardId, fromPostId, postBatchSize, chunkMaxChars, chunkOverlapChars, clear, null, embeddingProviderId, embeddingDims);
            Map<String, Object> details = buildPostsDetails(boardId, fromPostId, postBatchSize, chunkMaxChars,
                    chunkOverlapChars, clear, embeddingProviderId, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_BUILD_POSTS", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_BUILD_POSTS", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/rebuild/posts")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagPostsBuildResponse> rebuildPosts(
            @PathVariable Long id,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "postBatchSize", required = false) Integer postBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagPostsBuildResponse resp = buildService.rebuildPosts(id, boardId, postBatchSize, chunkMaxChars, chunkOverlapChars, null, embeddingProviderId, embeddingDims);
            Map<String, Object> details = buildPostsRebuildOrSyncDetails(boardId, postBatchSize, chunkMaxChars,
                    chunkOverlapChars, embeddingProviderId, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_REBUILD", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_REBUILD", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/sync/posts")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagPostsBuildResponse> syncPosts(
            @PathVariable Long id,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "postBatchSize", required = false) Integer postBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagPostsBuildResponse resp = buildService.syncPostsIncremental(id, boardId, postBatchSize, chunkMaxChars, chunkOverlapChars, null, embeddingProviderId, embeddingDims);
            Map<String, Object> details = buildPostsRebuildOrSyncDetails(boardId, postBatchSize, chunkMaxChars,
                    chunkOverlapChars, embeddingProviderId, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_INCREMENTAL_SYNC", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_INCREMENTAL_SYNC", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/build/comments")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagCommentsBuildResponse> buildComments(
            @PathVariable Long id,
            @RequestParam(value = "fromCommentId", required = false) Long fromCommentId,
            @RequestParam(value = "commentBatchSize", required = false) Integer commentBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "clear", required = false) Boolean clear,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagCommentsBuildResponse resp = commentBuildService.buildComments(id, fromCommentId, commentBatchSize, chunkMaxChars, chunkOverlapChars, clear, null, embeddingDims);
            Map<String, Object> details = buildCommentsDetails(fromCommentId, commentBatchSize, chunkMaxChars,
                    chunkOverlapChars, clear, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_BUILD_COMMENTS", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_BUILD_COMMENTS", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/rebuild/comments")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagCommentsBuildResponse> rebuildComments(
            @PathVariable Long id,
            @RequestParam(value = "commentBatchSize", required = false) Integer commentBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagCommentsBuildResponse resp = commentBuildService.rebuildComments(id, commentBatchSize, chunkMaxChars, chunkOverlapChars, null, embeddingDims);
            Map<String, Object> details = buildCommentsRebuildOrSyncDetails(commentBatchSize, chunkMaxChars,
                    chunkOverlapChars, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_REBUILD_COMMENTS", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_REBUILD_COMMENTS", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/sync/comments")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagCommentsBuildResponse> syncComments(
            @PathVariable Long id,
            @RequestParam(value = "commentBatchSize", required = false) Integer commentBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagCommentsBuildResponse resp = commentBuildService.syncCommentsIncremental(id, commentBatchSize, chunkMaxChars, chunkOverlapChars, null, embeddingDims);
            Map<String, Object> details = buildCommentsRebuildOrSyncDetails(commentBatchSize, chunkMaxChars,
                    chunkOverlapChars, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_INCREMENTAL_SYNC_COMMENTS", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_INCREMENTAL_SYNC_COMMENTS", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/test-query/comments")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagCommentsTestQueryResponse> testQueryComments(
            @PathVariable Long id,
            @RequestBody(required = false) RagCommentsTestQueryRequest req,
            Principal principal
    ) {
        try {
            RagCommentsTestQueryResponse resp = commentTestQueryService.testQuery(id, req);
            Map<String, Object> details = buildCommentsTestQueryDetails(req, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_TEST_QUERY_COMMENTS", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_TEST_QUERY_COMMENTS", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/build/files")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagFilesBuildResponse> buildFiles(
            @PathVariable Long id,
            @RequestParam(value = "fromFileAssetId", required = false) Long fromFileAssetId,
            @RequestParam(value = "fileBatchSize", required = false) Integer fileBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "clear", required = false) Boolean clear,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagFilesBuildResponse resp = fileBuildService.buildFiles(id, fromFileAssetId, fileBatchSize, chunkMaxChars, chunkOverlapChars, clear, null, embeddingProviderId, embeddingDims);
            Map<String, Object> details = buildFilesDetails(fromFileAssetId, fileBatchSize, chunkMaxChars,
                    chunkOverlapChars, clear, embeddingProviderId, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_BUILD_FILES", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null, details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_BUILD_FILES", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/rebuild/files")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagFilesBuildResponse> rebuildFiles(
            @PathVariable Long id,
            @RequestParam(value = "fileBatchSize", required = false) Integer fileBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagFilesBuildResponse resp = fileBuildService.rebuildFiles(id, fileBatchSize, chunkMaxChars, chunkOverlapChars, null, embeddingProviderId, embeddingDims);
            Map<String, Object> details = buildFilesRebuildOrSyncDetails(fileBatchSize, chunkMaxChars,
                    chunkOverlapChars, embeddingProviderId, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_REBUILD_FILES", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null, details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_REBUILD_FILES", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/sync/files")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagFilesBuildResponse> syncFiles(
            @PathVariable Long id,
            @RequestParam(value = "fileBatchSize", required = false) Integer fileBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagFilesBuildResponse resp = fileBuildService.syncFilesIncremental(id, fileBatchSize, chunkMaxChars, chunkOverlapChars, null, embeddingProviderId, embeddingDims);
            Map<String, Object> details = buildFilesRebuildOrSyncDetails(fileBatchSize, chunkMaxChars,
                    chunkOverlapChars, embeddingProviderId, embeddingDims, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_INCREMENTAL_SYNC_FILES", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null, details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_INCREMENTAL_SYNC_FILES", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/test-query/files")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagFilesTestQueryResponse> testQueryFiles(
            @PathVariable Long id,
            @RequestBody(required = false) RagFilesTestQueryRequest req,
            Principal principal
    ) {
        try {
            RagFilesTestQueryResponse resp = fileTestQueryService.testQuery(id, req);
            Map<String, Object> details = buildFilesTestQueryDetails(req, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_TEST_QUERY_FILES", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null, details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_TEST_QUERY_FILES", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PostMapping("/{id}/test-query")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagPostsTestQueryResponse> testQuery(
            @PathVariable Long id,
            @RequestBody(required = false) RagPostsTestQueryRequest req,
            Principal principal
    ) {
        try {
            RagPostsTestQueryResponse resp = testQueryService.testQuery(id, req);
            Map<String, Object> details = buildPostsTestQueryDetails(req, resp);
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_TEST_QUERY", "VECTOR_INDEX", id, AuditResult.SUCCESS, null, null,
                    details);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            auditLogWriter.write(null, principal == null ? null : principal.getName(),
                    "RETRIEVAL_RAG_TEST_QUERY", "VECTOR_INDEX", id, AuditResult.FAIL, e.getMessage(), null, null);
            throw e;
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<VectorIndicesEntity> update(@PathVariable Long id,
                                                      @RequestBody(required = false) @Valid VectorIndicesUpdateDTO dto,
                                                      Principal principal) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (dto == null) throw new IllegalArgumentException("payload is required");
        if (dto.getId() == null || !id.equals(dto.getId())) throw new IllegalArgumentException("id mismatch");

        VectorIndicesEntity e = vectorIndicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + id));

        Map<String, Object> before = buildVectorIndexDetailsWithStatus(e);

        if (dto.isHasProvider()) e.setProvider(dto.getProvider());
        if (dto.isHasCollectionName()) e.setCollectionName(dto.getCollectionName());
        if (dto.isHasMetric()) e.setMetric(dto.getMetric());
        if (dto.isHasDim()) e.setDim(dto.getDim());
        if (dto.isHasStatus()) e.setStatus(dto.getStatus());
        if (dto.isHasMetadata()) e.setMetadata(dto.getMetadata());

        VectorIndicesEntity saved = vectorIndicesRepository.save(e);

        Map<String, Object> after = buildVectorIndexDetailsWithStatus(saved);

        auditLogWriter.write(null, principal == null ? null : principal.getName(),
                "RETRIEVAL_VECTOR_INDEX_UPDATE", "VECTOR_INDEX", saved.getId(), AuditResult.SUCCESS, null, null, auditDiffBuilder.build(before, after));

        return ResponseEntity.ok(saved);
    }

    private static Map<String, Object> buildVectorIndexDetails(VectorIndicesEntity e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", enumName(e.getProvider()));
        details.put("collectionName", e.getCollectionName());
        details.put("metric", e.getMetric());
        details.put("dim", e.getDim());
        return details;
    }

    private static Map<String, Object> buildVectorIndexDetailsWithStatus(VectorIndicesEntity e) {
        Map<String, Object> details = buildVectorIndexDetails(e);
        details.put("status", enumName(e.getStatus()));
        return details;
    }

    private static Map<String, Object> buildPostsDetails(Long boardId, Long fromPostId, Integer postBatchSize,
                                                          Integer chunkMaxChars, Integer chunkOverlapChars,
                                                          Boolean clear, String embeddingProviderId,
                                                          Integer embeddingDims, RagPostsBuildResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("boardId", boardId);
        details.put("fromPostId", fromPostId);
        details.put("postBatchSize", postBatchSize);
        details.put("chunkMaxChars", chunkMaxChars);
        details.put("chunkOverlapChars", chunkOverlapChars);
        details.put("clear", clear);
        details.put("embeddingProviderId", embeddingProviderId);
        details.put("embeddingDims", embeddingDims);
        details.put("totalPosts", resp.getTotalPosts());
        details.put("totalChunks", resp.getTotalChunks());
        details.put("failedChunks", resp.getFailedChunks());
        return details;
    }

    private static Map<String, Object> buildPostsRebuildOrSyncDetails(Long boardId, Integer postBatchSize,
                                                                       Integer chunkMaxChars,
                                                                       Integer chunkOverlapChars,
                                                                       String embeddingProviderId,
                                                                       Integer embeddingDims,
                                                                       RagPostsBuildResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("boardId", boardId);
        details.put("postBatchSize", postBatchSize);
        details.put("chunkMaxChars", chunkMaxChars);
        details.put("chunkOverlapChars", chunkOverlapChars);
        details.put("embeddingProviderId", embeddingProviderId);
        details.put("embeddingDims", embeddingDims);
        details.put("totalPosts", resp.getTotalPosts());
        details.put("totalChunks", resp.getTotalChunks());
        details.put("failedChunks", resp.getFailedChunks());
        return details;
    }

    private static Map<String, Object> buildCommentsDetails(Long fromCommentId, Integer commentBatchSize,
                                                             Integer chunkMaxChars, Integer chunkOverlapChars,
                                                             Boolean clear, Integer embeddingDims,
                                                             RagCommentsBuildResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromCommentId", fromCommentId);
        details.put("commentBatchSize", commentBatchSize);
        details.put("chunkMaxChars", chunkMaxChars);
        details.put("chunkOverlapChars", chunkOverlapChars);
        details.put("clear", clear);
        details.put("embeddingDims", embeddingDims);
        details.put("totalComments", resp.getTotalComments());
        details.put("totalChunks", resp.getTotalChunks());
        details.put("failedChunks", resp.getFailedChunks());
        return details;
    }

    private static Map<String, Object> buildCommentsRebuildOrSyncDetails(Integer commentBatchSize,
                                                                          Integer chunkMaxChars,
                                                                          Integer chunkOverlapChars,
                                                                          Integer embeddingDims,
                                                                          RagCommentsBuildResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("commentBatchSize", commentBatchSize);
        details.put("chunkMaxChars", chunkMaxChars);
        details.put("chunkOverlapChars", chunkOverlapChars);
        details.put("embeddingDims", embeddingDims);
        details.put("totalComments", resp.getTotalComments());
        details.put("totalChunks", resp.getTotalChunks());
        details.put("failedChunks", resp.getFailedChunks());
        return details;
    }

    private static Map<String, Object> buildFilesDetails(Long fromFileAssetId, Integer fileBatchSize,
                                                          Integer chunkMaxChars, Integer chunkOverlapChars,
                                                          Boolean clear, String embeddingProviderId,
                                                          Integer embeddingDims, RagFilesBuildResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromFileAssetId", fromFileAssetId);
        details.put("fileBatchSize", fileBatchSize);
        details.put("chunkMaxChars", chunkMaxChars);
        details.put("chunkOverlapChars", chunkOverlapChars);
        details.put("clear", clear);
        details.put("embeddingProviderId", embeddingProviderId);
        details.put("embeddingDims", embeddingDims);
        details.put("totalFiles", resp.getTotalFiles());
        details.put("totalChunks", resp.getTotalChunks());
        details.put("failedChunks", resp.getFailedChunks());
        return details;
    }

    private static Map<String, Object> buildFilesRebuildOrSyncDetails(Integer fileBatchSize,
                                                                       Integer chunkMaxChars,
                                                                       Integer chunkOverlapChars,
                                                                       String embeddingProviderId,
                                                                       Integer embeddingDims,
                                                                       RagFilesBuildResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fileBatchSize", fileBatchSize);
        details.put("chunkMaxChars", chunkMaxChars);
        details.put("chunkOverlapChars", chunkOverlapChars);
        details.put("embeddingProviderId", embeddingProviderId);
        details.put("embeddingDims", embeddingDims);
        details.put("totalFiles", resp.getTotalFiles());
        details.put("totalChunks", resp.getTotalChunks());
        details.put("failedChunks", resp.getFailedChunks());
        return details;
    }

    private static String truncateQueryText(String queryText) {
        if (queryText == null || queryText.length() <= 200) return queryText;
        return queryText.substring(0, 200) + "...";
    }

    private static Map<String, Object> buildCommentsTestQueryDetails(RagCommentsTestQueryRequest req,
                                                                      RagCommentsTestQueryResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("topK", req == null ? null : req.getTopK());
        details.put("numCandidates", req == null ? null : req.getNumCandidates());
        details.put("embeddingModel", req == null ? null : req.getEmbeddingModel());
        details.put("queryText", truncateQueryText(req == null ? null : req.getQueryText()));
        details.put("hitCount", resp.getHits() == null ? 0 : resp.getHits().size());
        return details;
    }

    private static Map<String, Object> buildFilesTestQueryDetails(RagFilesTestQueryRequest req,
                                                                   RagFilesTestQueryResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fileAssetId", req == null ? null : req.getFileAssetId());
        details.put("postId", req == null ? null : req.getPostId());
        details.put("topK", req == null ? null : req.getTopK());
        details.put("numCandidates", req == null ? null : req.getNumCandidates());
        details.put("embeddingModel", req == null ? null : req.getEmbeddingModel());
        details.put("embeddingProviderId", req == null ? null : req.getEmbeddingProviderId());
        details.put("queryText", truncateQueryText(req == null ? null : req.getQueryText()));
        details.put("hitCount", resp.getHits() == null ? 0 : resp.getHits().size());
        return details;
    }

    private static Map<String, Object> buildPostsTestQueryDetails(RagPostsTestQueryRequest req,
                                                                   RagPostsTestQueryResponse resp) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("boardId", req == null ? null : req.getBoardId());
        details.put("topK", req == null ? null : req.getTopK());
        details.put("numCandidates", req == null ? null : req.getNumCandidates());
        details.put("embeddingModel", req == null ? null : req.getEmbeddingModel());
        details.put("embeddingProviderId", req == null ? null : req.getEmbeddingProviderId());
        details.put("queryText", truncateQueryText(req == null ? null : req.getQueryText()));
        details.put("hitCount", resp.getHits() == null ? 0 : resp.getHits().size());
        return details;
    }
}
