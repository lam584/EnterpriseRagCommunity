package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ElasticsearchIndexStartupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexStartupInitializer.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService ragPostsIndexService;
    private final ModerationSamplesIndexService moderationSamplesIndexService;
    private final ModerationSamplesIndexConfigService moderationSamplesIndexConfigService;
    private final ModerationSimilarityConfigRepository moderationSimilarityConfigRepository;

    @Value("${app.es.init.enabled:true}")
    private boolean enabled;

    @Value("${app.es.init.force-recreate:false}")
    private boolean forceRecreate;

    @Value("${app.es.init.fail-on-error:false}")
    private boolean failOnError;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        tryInitModerationSamplesIndex();
        tryInitRagVectorIndices();
    }

    private void tryInitModerationSamplesIndex() {
        Integer dims0 = moderationSimilarityConfigRepository.findAll().stream().findFirst().map(c -> c.getEmbeddingDims()).orElse(null);
        int dims = dims0 == null ? moderationSamplesIndexConfigService.getEmbeddingDimsOrDefault() : dims0;
        if (dims <= 0) {
            log.info("ES init skip moderation samples index recreate: embeddingDims not configured (<=0)");
            return;
        }
        try {
            if (forceRecreate) {
                moderationSamplesIndexService.recreateIndex(dims);
            } else {
                moderationSamplesIndexService.ensureIndex(dims);
            }
            log.info("ES init moderation samples index ok. index={}, dims={}", moderationSamplesIndexService.getIndexName(), dims);
        } catch (Exception ex) {
            if (failOnError) throw ex;
            log.warn("ES init moderation samples index failed. index={}, err={}", moderationSamplesIndexService.getIndexName(), ex.getMessage());
        }
    }

    private void tryInitRagVectorIndices() {
        List<VectorIndicesEntity> list = vectorIndicesRepository.findByProvider(VectorIndexProvider.OTHER);
        if (list == null || list.isEmpty()) {
            int dims = ragProps.getEs().getEmbeddingDims();
            if (dims <= 0) {
                log.info("ES init skip default RAG index: embeddingDims not configured (app.retrieval.rag.es.embedding-dims=0)");
                return;
            }
            String indexName = ragProps.getEs().getIndex();
            try {
                ensureOrRecreateRagIndex(indexName, dims);
                log.info("ES init default RAG index ok. index={}, dims={}", indexName, dims);
            } catch (Exception ex) {
                if (failOnError) throw ex;
                log.warn("ES init default RAG index failed. index={}, err={}", indexName, ex.getMessage());
            }
            return;
        }

        Map<String, Integer> byIndexName = new LinkedHashMap<>();
        for (VectorIndicesEntity vi : list) {
            if (vi == null) continue;
            String indexName = toNonBlank(vi.getCollectionName());
            if (indexName == null) indexName = toNonBlank(ragProps.getEs().getIndex());
            if (indexName == null) continue;

            Integer dims = bestDimsFor(vi);
            if (dims == null || dims <= 0) continue;

            Integer prev = byIndexName.get(indexName);
            if (prev != null && !prev.equals(dims)) {
                log.warn("ES init skip index due to conflicting dims in vector_indices. index={}, dims1={}, dims2={}", indexName, prev, dims);
                byIndexName.remove(indexName);
                byIndexName.put(indexName, -1);
                continue;
            }
            if (prev == null) byIndexName.put(indexName, dims);
        }

        for (Map.Entry<String, Integer> e : byIndexName.entrySet()) {
            String indexName = e.getKey();
            Integer dims = e.getValue();
            if (dims == null || dims <= 0) continue;
            if (dims == -1) continue;

            try {
                ensureOrRecreateRagIndex(indexName, dims);
                log.info("ES init RAG vector index ok. index={}, dims={}", indexName, dims);
            } catch (Exception ex) {
                if (failOnError) throw ex;
                log.warn("ES init RAG vector index failed. index={}, err={}", indexName, ex.getMessage());
            }
        }
    }

    private void ensureOrRecreateRagIndex(String indexName, int dims) {
        if (forceRecreate) {
            ragPostsIndexService.recreateIndex(indexName, dims);
            return;
        }
        try {
            ragPostsIndexService.ensureIndex(indexName, dims);
        } catch (IllegalStateException mismatch) {
            ragPostsIndexService.recreateIndex(indexName, dims);
        }
    }

    private Integer bestDimsFor(VectorIndicesEntity vi) {
        if (vi == null) return null;
        Integer d = vi.getDim();
        if (d != null && d > 0) return d;
        Object meta = vi.getMetadata() == null ? null : vi.getMetadata().get("lastBuildEmbeddingDims");
        Integer dm = toInt(meta);
        if (dm != null && dm > 0) return dm;
        int d0 = ragProps.getEs().getEmbeddingDims();
        return d0 > 0 ? d0 : null;
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Integer.parseInt(t);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
