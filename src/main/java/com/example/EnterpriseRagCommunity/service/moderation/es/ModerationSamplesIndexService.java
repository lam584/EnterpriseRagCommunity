package com.example.EnterpriseRagCommunity.service.moderation.es;

import com.example.EnterpriseRagCommunity.config.ModerationSimilarityProperties;
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

@Service
@RequiredArgsConstructor
public class ModerationSamplesIndexService {

    private static final Logger log = LoggerFactory.getLogger(ModerationSamplesIndexService.class);

    private final ElasticsearchTemplate template;
    private final ModerationSimilarityProperties props;

    public boolean indexExists() {
        IndexOperations ops = template.indexOps(IndexCoordinates.of(props.getEs().getIndex()));
        return ops.exists();
    }

    /**
     * Create index if missing.
     *
     * Note: if embeddingDims is 0, we'll create the index without the vector field.
     */
    public void ensureIndex() {
        ensureIndex(props.getEs().getEmbeddingDims());
    }

    /**
     * Create index if missing, using provided dims for mapping creation.
     *
     * This is useful when app.moderation.similarity.es.embedding-dims isn't configured yet;
     * we can infer dims from the embedding result and still bootstrap ES mapping correctly.
     */
    public void ensureIndex(int embeddingDims) {
        IndexOperations ops = template.indexOps(IndexCoordinates.of(props.getEs().getIndex()));
        if (ops.exists()) return;

        // 1) try create with current config
        tryCreate(ops, props.getEs().isIkEnabled(), embeddingDims);
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled) {
        tryCreate(ops, ikEnabled, props.getEs().getEmbeddingDims());
    }

    private void tryCreate(IndexOperations ops, boolean ikEnabled, int embeddingDims) {
        Document settings = Document.from(buildSettings(ikEnabled));
        Document mapping = Document.from(buildMapping(ikEnabled, embeddingDims));

        try {
            // create index + mapping in one go
            ops.create(settings);
            ops.putMapping(mapping);
        } catch (Exception first) {
            // If IK analyzer is missing, index creation will fail.
            if (ikEnabled) {
                log.warn("Create ES index failed with IK enabled (maybe IK plugin missing). Retrying with IK disabled. err={}", first.getMessage());
                // Best-effort cleanup if partially created
                try {
                    if (ops.exists()) {
                        ops.delete();
                    }
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
        // IMPORTANT:
        // IndexOperations.create(Document) expects *index settings* (equivalent to the body of PUT /{index}/_settings),
        // NOT a full create-index request body. If we wrap with {"settings":{...}}, Spring Data will wrap again
        // and Elasticsearch will see unknown settings like "index.settings.index.number_of_shards".

        Map<String, Object> index = new LinkedHashMap<>();
        index.put("number_of_shards", 1);
        index.put("number_of_replicas", 0);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("index", index);

        if (ikEnabled) {
            // Requires IK plugin installed on ES node.
            Map<String, Object> analysis = new LinkedHashMap<>();
            Map<String, Object> analyzer = new LinkedHashMap<>();

            analyzer.put("ik_max_word", Map.of("type", "ik_max_word"));
            analyzer.put("ik_smart", Map.of("type", "ik_smart"));

            analysis.put("analyzer", analyzer);
            settings.put("analysis", analysis);
        }

        return settings;
    }

    private Map<String, Object> buildMapping(boolean ikEnabled) {
        return buildMapping(ikEnabled, props.getEs().getEmbeddingDims());
    }

    private Map<String, Object> buildMapping(boolean ikEnabled, int embeddingDims) {
        int dims = embeddingDims;

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> propsMap = new LinkedHashMap<>();

        propsMap.put("id", Map.of("type", "long"));
        propsMap.put("category", Map.of("type", "keyword"));
        propsMap.put("source", Map.of("type", "keyword"));
        propsMap.put("risk_level", Map.of("type", "integer"));
        propsMap.put("enabled", Map.of("type", "boolean"));
        propsMap.put("text_hash", Map.of("type", "keyword"));
        propsMap.put("created_at", Map.of("type", "date"));
        propsMap.put("updated_at", Map.of("type", "date"));
        propsMap.put("labels", Map.of("type", "keyword"));

        propsMap.put("raw_text", Map.of("type", "text"));

        Map<String, Object> normText = new LinkedHashMap<>();
        normText.put("type", "text");
        if (ikEnabled) {
            normText.put("analyzer", "ik_max_word");
            normText.put("search_analyzer", "ik_smart");
        }
        propsMap.put("normalized_text", normText);

        if (dims > 0) {
            Map<String, Object> emb = new LinkedHashMap<>();
            emb.put("type", "dense_vector");
            emb.put("dims", dims);
            emb.put("index", true);
            emb.put("similarity", "cosine");
            propsMap.put("embedding", emb);
        }

        root.put("properties", propsMap);
        return root;
    }
}
