package com.example.EnterpriseRagCommunity.service.retrieval.es;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import org.slf4j.Logger;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class EsIndexSupport {

    private EsIndexSupport() {
    }

    static void tryCreateWithIkFallback(
            IndexOperations ops,
            boolean ikEnabled,
            int embeddingDims,
            Logger log,
            Function<Boolean, Map<String, Object>> settingsBuilder,
            Function<Boolean, Map<String, Object>> mappingBuilder
    ) {
        Document settings = Document.from(settingsBuilder.apply(ikEnabled));
        Document mapping = Document.from(mappingBuilder.apply(ikEnabled));

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
                Document settings2 = Document.from(settingsBuilder.apply(false));
                Document mapping2 = Document.from(mappingBuilder.apply(false));
                ops.create(settings2);
                ops.putMapping(mapping2);
                return;
            }
            throw first;
        }
    }

    static void recreateIndex(
            DependencyIsolationGuard dependencyIsolationGuard,
            DependencyCircuitBreakerService dependencyCircuitBreakerService,
            ElasticsearchTemplate template,
            String indexName,
            Supplier<String> defaultIndexNameSupplier,
            Consumer<IndexOperations> recreateAction
    ) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        dependencyCircuitBreakerService.run("ES", () -> {
            String idx = (indexName == null || indexName.isBlank()) ? defaultIndexNameSupplier.get() : indexName.trim();
            IndexOperations ops = template.indexOps(IndexCoordinates.of(idx));
            if (ops.exists()) {
                ops.delete();
            }
            recreateAction.accept(ops);
            return null;
        });
    }

    static void putTextField(Map<String, Object> propsMap, String fieldName, boolean ikEnabled) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "text");
        if (ikEnabled) {
            field.put("analyzer", "ik_max_word");
            field.put("search_analyzer", "ik_smart");
        }
        propsMap.put(fieldName, field);
    }

    static void putDenseVectorField(Map<String, Object> propsMap, String fieldName, int embeddingDims) {
        if (embeddingDims <= 0) return;
        Map<String, Object> emb = new LinkedHashMap<>();
        emb.put("type", "dense_vector");
        emb.put("dims", embeddingDims);
        emb.put("index", true);
        emb.put("similarity", "cosine");
        propsMap.put(fieldName, emb);
    }

    static boolean resolveIkEnabled(RetrievalRagProperties props,
                                    ElasticsearchIkAnalyzerProbe ikProbe,
                                    AtomicBoolean ikDisabledWarned,
                                    Logger log,
                                    String indexName) {
        boolean configured = props != null
                && props.getEs() != null
                && props.getEs().isIkEnabled();
        if (!configured) {
            return false;
        }
        boolean supported = ikProbe != null && ikProbe.isIkSupported();
        if (!supported && ikDisabledWarned != null && ikDisabledWarned.compareAndSet(false, true)) {
            log.warn("IK analyzer is enabled in config but not available on this Elasticsearch cluster. Falling back to standard analyzer. index={}", indexName);
        }
        return supported;
    }

    static void ensureIndex(
            DependencyIsolationGuard dependencyIsolationGuard,
            DependencyCircuitBreakerService dependencyCircuitBreakerService,
            ElasticsearchTemplate template,
            String indexName,
            Supplier<String> defaultIndexNameSupplier,
            int embeddingDims,
            Supplier<Boolean> ikEnabledSupplier,
            Function<IndexOperations, Integer> embeddingDimsReader,
            EnsureCreateAction createAction
    ) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        dependencyCircuitBreakerService.run("ES", () -> {
            String idx = (indexName == null || indexName.isBlank()) ? defaultIndexNameSupplier.get() : indexName.trim();
            IndexOperations ops = template.indexOps(IndexCoordinates.of(idx));
            if (!ops.exists()) {
                createAction.create(ops, ikEnabledSupplier.get(), embeddingDims);
                return null;
            }
            if (embeddingDims > 0) {
                Integer existingDims = embeddingDimsReader.apply(ops);
                if (existingDims == null) {
                    throw new IllegalStateException("ES index mapping mismatch: index='" + idx + "' has no embedding.dims but expected " + embeddingDims + ". Rebuild with clear=true (delete index) to recreate mapping.");
                }
                if (existingDims != embeddingDims) {
                    throw new IllegalStateException("ES index mapping mismatch: index='" + idx + "' embedding.dims=" + existingDims + " but expected " + embeddingDims + ". Rebuild with clear=true (delete index) to recreate mapping.");
                }
            }
            return null;
        });
    }

    @FunctionalInterface
    interface EnsureCreateAction {
        void create(IndexOperations ops, boolean ikEnabled, int embeddingDims);
    }
}
