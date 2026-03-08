package com.example.EnterpriseRagCommunity.service.init;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexBuildService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexBuildService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InitialAdminIndexBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminIndexBootstrapService.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RetrievalRagProperties ragProps;
    private final RagPostIndexBuildService ragPostIndexBuildService;
    private final RagCommentIndexBuildService ragCommentIndexBuildService;
    private final RagFileAssetIndexBuildService ragFileAssetIndexBuildService;

    private final ModerationSamplesSyncService moderationSamplesSyncService;
    private final ModerationSimilarityConfigRepository moderationSimilarityConfigRepository;
    private final ModerationSamplesIndexConfigService moderationSamplesIndexConfigService;

    public void bootstrap(Long initialAdminUserId) {
        ensureDefaultRagVectorIndexRecordsIfEmpty();
        moderationSamplesIndexConfigService.getOrSeedDefault(initialAdminUserId);
        ensureModerationConfigRecord(initialAdminUserId);
        rebuildElasticsearchIndicesFromSqlConfig();
    }

    private void ensureDefaultRagVectorIndexRecordsIfEmpty() {
        String indexName = toNonBlank(ragProps.getEs().getIndex());
        if (indexName == null) return;

        int dims = ragProps.getEs().getEmbeddingDims();
        if (dims <= 0) dims = 1024;

        ensureDefaultVectorIndex(indexName, dims, "POST", 800, 80, "Seeded default post vector_indices record");
        ensureDefaultVectorIndex(indexName + "_comments", dims, "COMMENT", 800, 80, "Seeded default comment vector_indices record");
        ensureDefaultVectorIndex("rag_file_assets_v1", dims, "FILE_ASSET", 1200, 120, "Seeded default file vector_indices record");
    }

    private void ensureDefaultVectorIndex(String collectionName, int dims, String sourceType, int chunkMaxChars, int chunkOverlapChars, String logPrefix) {
        String cname = toNonBlank(collectionName);
        if (cname == null) return;
        if (vectorIndicesRepository.existsByProviderAndCollectionName(VectorIndexProvider.OTHER, cname)) return;

        VectorIndicesEntity e = new VectorIndicesEntity();
        e.setProvider(VectorIndexProvider.OTHER);
        e.setCollectionName(cname);
        e.setMetric("cosine");
        e.setDim(dims);
        e.setStatus(VectorIndexStatus.READY);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sourceType", sourceType);
        meta.put("defaultChunkMaxChars", chunkMaxChars);
        meta.put("defaultChunkOverlapChars", chunkOverlapChars);
        e.setMetadata(meta);

        vectorIndicesRepository.save(e);
        log.info("{}. provider={}, collectionName={}, dim={}", logPrefix, e.getProvider(), e.getCollectionName(), e.getDim());
    }

    private void ensureModerationConfigRecord(Long initialAdminUserId) {
        ModerationSimilarityConfigEntity cfg = moderationSimilarityConfigRepository.findAll()
                .stream().findFirst().orElse(null);

        if (cfg == null) {
            ModerationSimilarityConfigEntity e = new ModerationSimilarityConfigEntity();
            e.setEnabled(true);
            e.setEmbeddingModel(toNonBlank(moderationSamplesIndexConfigService.getEmbeddingModelOrDefault()));
            e.setEmbeddingDims(Math.max(0, moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault()));
            e.setMaxInputChars(0);
            e.setDefaultTopK(Math.max(1, moderationSamplesIndexConfigService.getDefaultTopKOrDefault()));
            e.setDefaultThreshold(Math.max(0, moderationSamplesIndexConfigService.getDefaultThresholdOrDefault()));
            e.setDefaultNumCandidates(0);
            e.setUpdatedAt(LocalDateTime.now());
            e.setUpdatedBy(initialAdminUserId);
            moderationSimilarityConfigRepository.save(e);
            log.info("Seeded moderation_similarity_config record. embeddingModel={}, embeddingDims={}",
                    e.getEmbeddingModel(), e.getEmbeddingDims());
            return;
        }

        boolean changed = false;
        if (toNonBlank(cfg.getEmbeddingModel()) == null && toNonBlank(moderationSamplesIndexConfigService.getEmbeddingModelOrDefault()) != null) {
            cfg.setEmbeddingModel(toNonBlank(moderationSamplesIndexConfigService.getEmbeddingModelOrDefault()));
            changed = true;
        }
        if (cfg.getEmbeddingDims() == null || cfg.getEmbeddingDims() <= 0) {
            int dims = moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault();
            if (dims > 0) {
                cfg.setEmbeddingDims(dims);
                changed = true;
            }
        }
        if (cfg.getDefaultTopK() == null || cfg.getDefaultTopK() <= 0) {
            cfg.setDefaultTopK(Math.max(1, moderationSamplesIndexConfigService.getDefaultTopKOrDefault()));
            changed = true;
        }
        if (cfg.getDefaultThreshold() == null || cfg.getDefaultThreshold() < 0) {
            cfg.setDefaultThreshold(Math.max(0, moderationSamplesIndexConfigService.getDefaultThresholdOrDefault()));
            changed = true;
        }
        if (cfg.getMaxInputChars() == null || cfg.getMaxInputChars() < 0) {
            cfg.setMaxInputChars(0);
            changed = true;
        }
        if (cfg.getDefaultNumCandidates() == null || cfg.getDefaultNumCandidates() < 0) {
            cfg.setDefaultNumCandidates(0);
            changed = true;
        }

        if (changed) {
            cfg.setUpdatedAt(LocalDateTime.now());
            cfg.setUpdatedBy(initialAdminUserId);
            moderationSimilarityConfigRepository.save(cfg);
            log.info("Normalized moderation_similarity_config record. embeddingModel={}, embeddingDims={}",
                    cfg.getEmbeddingModel(), cfg.getEmbeddingDims());
        }
    }

    private void rebuildElasticsearchIndicesFromSqlConfig() {
        rebuildModerationSamplesIndexAndData();
        rebuildRagIndicesAndData();
    }

    private void rebuildModerationSamplesIndexAndData() {
        try {
            moderationSamplesSyncService.reindexAll(true, 200, null);
            log.info("Reindexed moderation samples ES index. index={}", moderationSamplesIndexConfigService.getIndexNameOrDefault());
        } catch (Exception ex) {
            log.warn("Reindex moderation samples ES index failed. index={}, err={}", moderationSamplesIndexConfigService.getIndexNameOrDefault(), ex.getMessage());
        }
    }

    private void rebuildRagIndicesAndData() {
        List<VectorIndicesEntity> list = vectorIndicesRepository.findByProvider(VectorIndexProvider.OTHER);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (VectorIndicesEntity vi : list) {
            if (vi == null) continue;
            String indexName = toNonBlank(vi.getCollectionName());
            if (indexName == null) indexName = toNonBlank(ragProps.getEs().getIndex());
            if (indexName == null) continue;

            String sourceType = vi.getMetadata() == null || vi.getMetadata().get("sourceType") == null
                    ? null
                    : String.valueOf(vi.getMetadata().get("sourceType")).trim();
            if (sourceType == null || sourceType.isBlank()) sourceType = "POST";
            if ("COMMENT".equalsIgnoreCase(sourceType)) {
                try {
                    ragCommentIndexBuildService.rebuildComments(vi.getId(), null, null, null, null, vi.getDim());
                    log.info("Rebuilt RAG comment index. index={}, vectorIndexId={}", indexName, vi.getId());
                } catch (Exception ex) {
                    log.warn("Rebuild RAG comment index failed. index={}, vectorIndexId={}, err={}", indexName, vi.getId(), ex.getMessage());
                }
            } else if ("FILE_ASSET".equalsIgnoreCase(sourceType)) {
                try {
                    ragFileAssetIndexBuildService.rebuildFiles(vi.getId(), null, null, null, null, null, vi.getDim());
                    log.info("Rebuilt RAG file index. index={}, vectorIndexId={}", indexName, vi.getId());
                } catch (Exception ex) {
                    log.warn("Rebuild RAG file index failed. index={}, vectorIndexId={}, err={}", indexName, vi.getId(), ex.getMessage());
                }
            } else {
                try {
                    ragPostIndexBuildService.rebuildPosts(vi.getId(), null, null, null, null, null, null, vi.getDim());
                    log.info("Rebuilt RAG posts index. index={}, vectorIndexId={}", indexName, vi.getId());
                } catch (Exception ex) {
                    log.warn("Rebuild RAG posts index failed. index={}, vectorIndexId={}, err={}", indexName, vi.getId(), ex.getMessage());
                }
            }
        }
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
