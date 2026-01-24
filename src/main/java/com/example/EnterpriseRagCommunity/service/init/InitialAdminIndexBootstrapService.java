package com.example.EnterpriseRagCommunity.service.init;

import com.example.EnterpriseRagCommunity.config.ModerationSimilarityProperties;
import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
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
    private final RagPostsIndexService ragPostsIndexService;

    private final ModerationSimilarityProperties moderationProps;
    private final ModerationSamplesIndexService moderationSamplesIndexService;
    private final ModerationSimilarityConfigRepository moderationSimilarityConfigRepository;

    public void bootstrap(Long initialAdminUserId) {
        ensureDefaultVectorIndexRecord();
        ensureModerationConfigRecord(initialAdminUserId);
        ensureElasticsearchIndices();
    }

    private void ensureDefaultVectorIndexRecord() {
        String indexName = toNonBlank(ragProps.getEs().getIndex());
        if (indexName == null) return;

        if (vectorIndicesRepository.existsByProviderAndCollectionName(VectorIndexProvider.OTHER, indexName)) {
            return;
        }

        int dims = ragProps.getEs().getEmbeddingDims();
        if (dims <= 0) dims = 1024;

        VectorIndicesEntity e = new VectorIndicesEntity();
        e.setProvider(VectorIndexProvider.OTHER);
        e.setCollectionName(indexName);
        e.setMetric("cosine");
        e.setDim(dims);
        e.setStatus(VectorIndexStatus.READY);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("embeddingModel", toNonBlank(ragProps.getEs().getEmbeddingModel()));
        meta.put("defaultChunkMaxChars", 800);
        meta.put("defaultChunkOverlapChars", 80);
        e.setMetadata(meta);

        vectorIndicesRepository.save(e);
        log.info("Seeded default vector_indices record. provider={}, collectionName={}, dim={}",
                e.getProvider(), e.getCollectionName(), e.getDim());
    }

    private void ensureModerationConfigRecord(Long initialAdminUserId) {
        ModerationSimilarityConfigEntity cfg = moderationSimilarityConfigRepository.findAll()
                .stream().findFirst().orElse(null);

        if (cfg == null) {
            ModerationSimilarityConfigEntity e = new ModerationSimilarityConfigEntity();
            e.setEnabled(true);
            e.setEmbeddingModel(toNonBlank(moderationProps.getEs().getEmbeddingModel()));
            e.setEmbeddingDims(Math.max(0, moderationProps.getEs().getEmbeddingDims()));
            e.setMaxInputChars(0);
            e.setDefaultTopK(Math.max(1, moderationProps.getEs().getTopK()));
            e.setDefaultThreshold(Math.max(0, moderationProps.getEs().getThreshold()));
            e.setDefaultNumCandidates(0);
            e.setUpdatedAt(LocalDateTime.now());
            e.setUpdatedBy(initialAdminUserId);
            moderationSimilarityConfigRepository.save(e);
            log.info("Seeded moderation_similarity_config record. embeddingModel={}, embeddingDims={}",
                    e.getEmbeddingModel(), e.getEmbeddingDims());
            return;
        }

        boolean changed = false;
        if (toNonBlank(cfg.getEmbeddingModel()) == null && toNonBlank(moderationProps.getEs().getEmbeddingModel()) != null) {
            cfg.setEmbeddingModel(toNonBlank(moderationProps.getEs().getEmbeddingModel()));
            changed = true;
        }
        if (cfg.getEmbeddingDims() == null || cfg.getEmbeddingDims() <= 0) {
            int dims = moderationProps.getEs().getEmbeddingDims();
            if (dims > 0) {
                cfg.setEmbeddingDims(dims);
                changed = true;
            }
        }
        if (cfg.getDefaultTopK() == null || cfg.getDefaultTopK() <= 0) {
            cfg.setDefaultTopK(Math.max(1, moderationProps.getEs().getTopK()));
            changed = true;
        }
        if (cfg.getDefaultThreshold() == null || cfg.getDefaultThreshold() < 0) {
            cfg.setDefaultThreshold(Math.max(0, moderationProps.getEs().getThreshold()));
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

    private void ensureElasticsearchIndices() {
        ensureModerationSamplesIndex();
        ensureRagIndices();
    }

    private void ensureModerationSamplesIndex() {
        int dims = moderationProps.getEs().getEmbeddingDims();
        if (dims <= 0) dims = 1024;
        try {
            moderationSamplesIndexService.ensureIndex(dims);
            log.info("Ensured moderation samples ES index. index={}, dims={}", moderationProps.getEs().getIndex(), dims);
        } catch (Exception ex) {
            log.warn("Ensure moderation samples ES index failed. index={}, err={}", moderationProps.getEs().getIndex(), ex.getMessage());
        }
    }

    private void ensureRagIndices() {
        List<VectorIndicesEntity> list = vectorIndicesRepository.findByProvider(VectorIndexProvider.OTHER);
        if (list == null || list.isEmpty()) {
            String indexName = toNonBlank(ragProps.getEs().getIndex());
            if (indexName == null) return;
            int dims = ragProps.getEs().getEmbeddingDims();
            if (dims <= 0) dims = 1024;
            ensureOrRecreateRagIndex(indexName, dims);
            return;
        }

        for (VectorIndicesEntity vi : list) {
            if (vi == null) continue;
            String indexName = toNonBlank(vi.getCollectionName());
            if (indexName == null) indexName = toNonBlank(ragProps.getEs().getIndex());
            if (indexName == null) continue;

            Integer dims0 = vi.getDim();
            int dims = (dims0 != null && dims0 > 0) ? dims0 : ragProps.getEs().getEmbeddingDims();
            if (dims <= 0) dims = 1024;

            ensureOrRecreateRagIndex(indexName, dims);
        }
    }

    private void ensureOrRecreateRagIndex(String indexName, int dims) {
        try {
            ragPostsIndexService.ensureIndex(indexName, dims);
            log.info("Ensured RAG ES index. index={}, dims={}", indexName, dims);
        } catch (IllegalStateException mismatch) {
            try {
                ragPostsIndexService.recreateIndex(indexName, dims);
                log.info("Recreated RAG ES index due to dims mismatch. index={}, dims={}", indexName, dims);
            } catch (Exception ex) {
                log.warn("Recreate RAG ES index failed. index={}, err={}", indexName, ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Ensure RAG ES index failed. index={}, err={}", indexName, ex.getMessage());
        }
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}

