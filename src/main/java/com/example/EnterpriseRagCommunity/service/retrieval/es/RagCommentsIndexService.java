package com.example.EnterpriseRagCommunity.service.retrieval.es;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RagCommentsIndexService {

    private static final Logger log = LoggerFactory.getLogger(RagCommentsIndexService.class);

    private final ElasticsearchTemplate template;
    private final RetrievalRagProperties props;
    private final ElasticsearchIkAnalyzerProbe ikProbe;
    private final AtomicBoolean ikDisabledWarned = new AtomicBoolean(false);

    public String defaultIndexName() {
        String base = props.getEs().getIndex();
        if (base == null || base.isBlank()) return "rag_comment_chunks_v1";
        return base.trim() + "_comments";
    }

    public void recreateIndex(String indexName, int embeddingDims) {
        String idx = (indexName == null || indexName.isBlank()) ? defaultIndexName() : indexName.trim();
        IndexOperations ops = template.indexOps(IndexCoordinates.of(idx));
        if (ops.exists()) {
            ops.delete();
        }
        tryCreate(ops, resolveIkEnabled(), embeddingDims);
    }

    public void ensureIndex(String indexName, int embeddingDims) {
        String idx = (indexName == null || indexName.isBlank()) ? defaultIndexName() : indexName.trim();
        IndexOperations ops = template.indexOps(IndexCoordinates.of(idx));
        if (!ops.exists()) {
            tryCreate(ops, resolveIkEnabled(), embeddingDims);
            return;
        }
        if (embeddingDims > 0) {
            Integer existingDims = readEmbeddingDims(ops);
            if (existingDims == null) {
                throw new IllegalStateException("ES index mapping mismatch: index='" + idx + "' has no embedding.dims but expected " + embeddingDims + ". Rebuild with clear=true (delete index) to recreate mapping.");
            }
            if (existingDims != embeddingDims) {
                throw new IllegalStateException("ES index mapping mismatch: index='" + idx + "' embedding.dims=" + existingDims + " but expected " + embeddingDims + ". Rebuild with clear=true (delete index) to recreate mapping.");
            }
        }
    }

    private boolean resolveIkEnabled() {
        boolean configured = props.getEs().isIkEnabled();
        if (!configured) return false;
        boolean supported = ikProbe.isIkSupported();
        if (!supported && ikDisabledWarned.compareAndSet(false, true)) {
            log.warn("IK analyzer is enabled in config but not available on this Elasticsearch cluster. Falling back to standard analyzer. index={}", defaultIndexName());
        }
        return supported;
    }

    private Integer readEmbeddingDims(IndexOperations ops) {
        try {
            Map<String, Object> mapping = ops.getMapping();
            return extractEmbeddingDims(mapping);
        } catch (Exception e) {
            log.warn("Read ES mapping failed. err={}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Integer extractEmbeddingDims(Map<String, Object> mapping) {
        if (mapping == null) return null;
        Object props0 = mapping.get("properties");
        Integer dims = extractEmbeddingDimsFromProperties(props0);
        if (dims != null) return dims;

        Object mappings0 = mapping.get("mappings");
        if (mappings0 instanceof Map<?, ?> mm) {
            Object props1 = ((Map<String, Object>) mm).get("properties");
            dims = extractEmbeddingDimsFromProperties(props1);
            if (dims != null) return dims;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Integer extractEmbeddingDimsFromProperties(Object props0) {
        if (!(props0 instanceof Map<?, ?>)) return null;
        Map<String, Object> props = (Map<String, Object>) props0;
        Object emb0 = props.get("embedding");
        if (!(emb0 instanceof Map<?, ?>)) return null;
        Map<String, Object> emb = (Map<String, Object>) emb0;
        Object dims0 = emb.get("dims");
        if (dims0 instanceof Number n) return n.intValue();
        if (dims0 instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled, int embeddingDims) {
        Document settings = Document.from(buildSettings(ikEnabled));
        Document mapping = Document.from(buildMapping(ikEnabled, embeddingDims));

        try {
            ops.create(settings);
            ops.putMapping(mapping);
        } catch (Exception first) {
            if (ikEnabled) {
                log.warn("Create ES index failed with IK enabled. Retrying with IK disabled. err={}", first.getMessage());
                try {
                    if (ops.exists()) ops.delete();
                } catch (Exception ignore) {
                }
                Document settings2 = Document.from(buildSettings(false));
                Document mapping2 = Document.from(buildMapping(false, embeddingDims));
                ops.create(settings2);
                ops.putMapping(mapping2);
                return;
            }
            throw first;
        }
    }

    private Map<String, Object> buildSettings(boolean ikEnabled) {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("number_of_shards", 1);
        index.put("number_of_replicas", 0);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("index", index);

        if (ikEnabled) {
            Map<String, Object> analysis = new LinkedHashMap<>();
            Map<String, Object> analyzer = new LinkedHashMap<>();
            analyzer.put("ik_max_word", Map.of("type", "ik_max_word"));
            analyzer.put("ik_smart", Map.of("type", "ik_smart"));
            analysis.put("analyzer", analyzer);
            settings.put("analysis", analysis);
        }

        return settings;
    }

    private Map<String, Object> buildMapping(boolean ikEnabled, int embeddingDims) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> propsMap = new LinkedHashMap<>();

        propsMap.put("id", Map.of("type", "keyword"));
        propsMap.put("comment_id", Map.of("type", "long"));
        propsMap.put("post_id", Map.of("type", "long"));
        propsMap.put("parent_id", Map.of("type", "long"));
        propsMap.put("author_id", Map.of("type", "long"));
        propsMap.put("comment_floor", Map.of("type", "integer"));
        propsMap.put("comment_level", Map.of("type", "integer"));
        propsMap.put("source_type", Map.of("type", "keyword"));
        propsMap.put("chunk_index", Map.of("type", "integer"));
        propsMap.put("content_hash", Map.of("type", "keyword"));
        propsMap.put("created_at", Map.of("type", "date"));
        propsMap.put("updated_at", Map.of("type", "date"));

        Map<String, Object> contentExcerpt = new LinkedHashMap<>();
        contentExcerpt.put("type", "text");
        if (ikEnabled) {
            contentExcerpt.put("analyzer", "ik_max_word");
            contentExcerpt.put("search_analyzer", "ik_smart");
        }
        propsMap.put("content_excerpt", contentExcerpt);

        Map<String, Object> contentText = new LinkedHashMap<>();
        contentText.put("type", "text");
        if (ikEnabled) {
            contentText.put("analyzer", "ik_max_word");
            contentText.put("search_analyzer", "ik_smart");
        }
        propsMap.put("content_text", contentText);

        if (embeddingDims > 0) {
            Map<String, Object> emb = new LinkedHashMap<>();
            emb.put("type", "dense_vector");
            emb.put("dims", embeddingDims);
            emb.put("index", true);
            emb.put("similarity", "cosine");
            propsMap.put("embedding", emb);
        }

        root.put("properties", propsMap);
        return root;
    }
}
