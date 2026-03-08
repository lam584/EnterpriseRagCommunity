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
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", saved.getProvider() == null ? null : saved.getProvider().name());
        details.put("collectionName", saved.getCollectionName());
        details.put("metric", saved.getMetric());
        details.put("dim", saved.getDim());
        auditLogWriter.write(null, principal == null ? null : principal.getName(),
                "RETRIEVAL_VECTOR_INDEX_CREATE", "VECTOR_INDEX", saved.getId(), AuditResult.SUCCESS, null, null,
                details);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<VectorIndicesEntity> update(@PathVariable("id") Long id,
                                                      @RequestBody(required = false) @Valid VectorIndicesUpdateDTO dto,
                                                      Principal principal) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (dto == null) throw new IllegalArgumentException("payload is required");
        if (dto.getId() == null || !id.equals(dto.getId())) throw new IllegalArgumentException("id mismatch");

        VectorIndicesEntity e = vectorIndicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + id));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("provider", e.getProvider() == null ? null : e.getProvider().name());
        before.put("collectionName", e.getCollectionName());
        before.put("metric", e.getMetric());
        before.put("dim", e.getDim());
        before.put("status", e.getStatus() == null ? null : e.getStatus().name());

        if (dto.getProvider() != null && dto.getProvider().isPresent()) e.setProvider(dto.getProvider().get());
        if (dto.getCollectionName() != null && dto.getCollectionName().isPresent()) e.setCollectionName(dto.getCollectionName().get());
        if (dto.getMetric() != null && dto.getMetric().isPresent()) e.setMetric(dto.getMetric().get());
        if (dto.getDim() != null && dto.getDim().isPresent()) e.setDim(dto.getDim().get());
        if (dto.getStatus() != null && dto.getStatus().isPresent()) e.setStatus(dto.getStatus().get());
        if (dto.getMetadata() != null && dto.getMetadata().isPresent()) e.setMetadata(dto.getMetadata().get());

        VectorIndicesEntity saved = vectorIndicesRepository.save(e);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("provider", saved.getProvider() == null ? null : saved.getProvider().name());
        after.put("collectionName", saved.getCollectionName());
        after.put("metric", saved.getMetric());
        after.put("dim", saved.getDim());
        after.put("status", saved.getStatus() == null ? null : saved.getStatus().name());

        auditLogWriter.write(null, principal == null ? null : principal.getName(),
                "RETRIEVAL_VECTOR_INDEX_UPDATE", "VECTOR_INDEX", saved.getId(), AuditResult.SUCCESS, null, null, auditDiffBuilder.build(before, after));

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
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
            @PathVariable("id") Long id,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "fromPostId", required = false) Long fromPostId,
            @RequestParam(value = "postBatchSize", required = false) Integer postBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "clear", required = false) Boolean clear,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagPostsBuildResponse resp = buildService.buildPosts(id, boardId, fromPostId, postBatchSize, chunkMaxChars, chunkOverlapChars, clear, embeddingModel, embeddingProviderId, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("boardId", boardId);
            details.put("fromPostId", fromPostId);
            details.put("postBatchSize", postBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("clear", clear);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingProviderId", embeddingProviderId);
            details.put("embeddingDims", embeddingDims);
            details.put("totalPosts", resp.getTotalPosts());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "postBatchSize", required = false) Integer postBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagPostsBuildResponse resp = buildService.rebuildPosts(id, boardId, postBatchSize, chunkMaxChars, chunkOverlapChars, embeddingModel, embeddingProviderId, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("boardId", boardId);
            details.put("postBatchSize", postBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingProviderId", embeddingProviderId);
            details.put("embeddingDims", embeddingDims);
            details.put("totalPosts", resp.getTotalPosts());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "postBatchSize", required = false) Integer postBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagPostsBuildResponse resp = buildService.syncPostsIncremental(id, boardId, postBatchSize, chunkMaxChars, chunkOverlapChars, embeddingModel, embeddingProviderId, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("boardId", boardId);
            details.put("postBatchSize", postBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingProviderId", embeddingProviderId);
            details.put("embeddingDims", embeddingDims);
            details.put("totalPosts", resp.getTotalPosts());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "fromCommentId", required = false) Long fromCommentId,
            @RequestParam(value = "commentBatchSize", required = false) Integer commentBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "clear", required = false) Boolean clear,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagCommentsBuildResponse resp = commentBuildService.buildComments(id, fromCommentId, commentBatchSize, chunkMaxChars, chunkOverlapChars, clear, embeddingModel, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fromCommentId", fromCommentId);
            details.put("commentBatchSize", commentBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("clear", clear);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingDims", embeddingDims);
            details.put("totalComments", resp.getTotalComments());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "commentBatchSize", required = false) Integer commentBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagCommentsBuildResponse resp = commentBuildService.rebuildComments(id, commentBatchSize, chunkMaxChars, chunkOverlapChars, embeddingModel, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("commentBatchSize", commentBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingDims", embeddingDims);
            details.put("totalComments", resp.getTotalComments());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "commentBatchSize", required = false) Integer commentBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagCommentsBuildResponse resp = commentBuildService.syncCommentsIncremental(id, commentBatchSize, chunkMaxChars, chunkOverlapChars, embeddingModel, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("commentBatchSize", commentBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingDims", embeddingDims);
            details.put("totalComments", resp.getTotalComments());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestBody(required = false) RagCommentsTestQueryRequest req,
            Principal principal
    ) {
        try {
            RagCommentsTestQueryResponse resp = commentTestQueryService.testQuery(id, req);
            String qt = req == null ? null : req.getQueryText();
            if (qt != null && qt.length() > 200) qt = qt.substring(0, 200) + "...";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("topK", req == null ? null : req.getTopK());
            details.put("numCandidates", req == null ? null : req.getNumCandidates());
            details.put("embeddingModel", req == null ? null : req.getEmbeddingModel());
            details.put("queryText", qt);
            details.put("hitCount", resp.getHits() == null ? 0 : resp.getHits().size());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "fromFileAssetId", required = false) Long fromFileAssetId,
            @RequestParam(value = "fileBatchSize", required = false) Integer fileBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "clear", required = false) Boolean clear,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagFilesBuildResponse resp = fileBuildService.buildFiles(id, fromFileAssetId, fileBatchSize, chunkMaxChars, chunkOverlapChars, clear, embeddingModel, embeddingProviderId, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fromFileAssetId", fromFileAssetId);
            details.put("fileBatchSize", fileBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("clear", clear);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingProviderId", embeddingProviderId);
            details.put("embeddingDims", embeddingDims);
            details.put("totalFiles", resp.getTotalFiles());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "fileBatchSize", required = false) Integer fileBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagFilesBuildResponse resp = fileBuildService.rebuildFiles(id, fileBatchSize, chunkMaxChars, chunkOverlapChars, embeddingModel, embeddingProviderId, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fileBatchSize", fileBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingProviderId", embeddingProviderId);
            details.put("embeddingDims", embeddingDims);
            details.put("totalFiles", resp.getTotalFiles());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestParam(value = "fileBatchSize", required = false) Integer fileBatchSize,
            @RequestParam(value = "chunkMaxChars", required = false) Integer chunkMaxChars,
            @RequestParam(value = "chunkOverlapChars", required = false) Integer chunkOverlapChars,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel,
            @RequestParam(value = "embeddingProviderId", required = false) String embeddingProviderId,
            @RequestParam(value = "embeddingDims", required = false) Integer embeddingDims,
            Principal principal
    ) {
        try {
            RagFilesBuildResponse resp = fileBuildService.syncFilesIncremental(id, fileBatchSize, chunkMaxChars, chunkOverlapChars, embeddingModel, embeddingProviderId, embeddingDims);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fileBatchSize", fileBatchSize);
            details.put("chunkMaxChars", chunkMaxChars);
            details.put("chunkOverlapChars", chunkOverlapChars);
            details.put("embeddingModel", embeddingModel);
            details.put("embeddingProviderId", embeddingProviderId);
            details.put("embeddingDims", embeddingDims);
            details.put("totalFiles", resp.getTotalFiles());
            details.put("totalChunks", resp.getTotalChunks());
            details.put("failedChunks", resp.getFailedChunks());
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
            @PathVariable("id") Long id,
            @RequestBody(required = false) RagFilesTestQueryRequest req,
            Principal principal
    ) {
        try {
            RagFilesTestQueryResponse resp = fileTestQueryService.testQuery(id, req);
            String qt = req == null ? null : req.getQueryText();
            if (qt != null && qt.length() > 200) qt = qt.substring(0, 200) + "...";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("fileAssetId", req == null ? null : req.getFileAssetId());
            details.put("postId", req == null ? null : req.getPostId());
            details.put("topK", req == null ? null : req.getTopK());
            details.put("numCandidates", req == null ? null : req.getNumCandidates());
            details.put("embeddingModel", req == null ? null : req.getEmbeddingModel());
            details.put("embeddingProviderId", req == null ? null : req.getEmbeddingProviderId());
            details.put("queryText", qt);
            details.put("hitCount", resp.getHits() == null ? 0 : resp.getHits().size());
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
            @PathVariable("id") Long id,
            @RequestBody(required = false) RagPostsTestQueryRequest req,
            Principal principal
    ) {
        try {
            RagPostsTestQueryResponse resp = testQueryService.testQuery(id, req);
            String qt = req == null ? null : req.getQueryText();
            if (qt != null && qt.length() > 200) qt = qt.substring(0, 200) + "...";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("boardId", req == null ? null : req.getBoardId());
            details.put("topK", req == null ? null : req.getTopK());
            details.put("numCandidates", req == null ? null : req.getNumCandidates());
            details.put("embeddingModel", req == null ? null : req.getEmbeddingModel());
            details.put("embeddingProviderId", req == null ? null : req.getEmbeddingProviderId());
            details.put("queryText", qt);
            details.put("hitCount", resp.getHits() == null ? 0 : resp.getHits().size());
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
}
