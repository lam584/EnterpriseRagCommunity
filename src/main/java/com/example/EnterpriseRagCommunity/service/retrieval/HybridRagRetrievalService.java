package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.ai.AiResponseParsingUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.ApproxTokenSupport;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Service
public class HybridRagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRagRetrievalService.class);

    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    @Getter
    private final AiEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    @Getter
    private final AiRerankService aiRerankService;
    private final LlmGateway llmGateway;
    private final RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService;
    private final PostsRepository postsRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final DependencyIsolationGuard dependencyIsolationGuard;
    private final DependencyCircuitBreakerService dependencyCircuitBreakerService;

    @Autowired
    public HybridRagRetrievalService(
            RetrievalRagProperties ragProps,
            RagPostsIndexService indexService,
            AiEmbeddingService embeddingService,
            ObjectMapper objectMapper,
            AiRerankService aiRerankService,
            LlmGateway llmGateway,
            RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService,
            PostsRepository postsRepository,
            SystemConfigurationService systemConfigurationService,
            DependencyIsolationGuard dependencyIsolationGuard,
            DependencyCircuitBreakerService dependencyCircuitBreakerService
    ) {
        this.ragProps = ragProps;
        this.indexService = indexService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.aiRerankService = aiRerankService;
        this.llmGateway = llmGateway;
        this.ragFileAssetChatRetrievalService = ragFileAssetChatRetrievalService;
        this.postsRepository = postsRepository;
        this.systemConfigurationService = systemConfigurationService;
        this.dependencyIsolationGuard = dependencyIsolationGuard;
        this.dependencyCircuitBreakerService = dependencyCircuitBreakerService;
    }

    public HybridRagRetrievalService(
            RetrievalRagProperties ragProps,
            RagPostsIndexService indexService,
            AiEmbeddingService embeddingService,
            ObjectMapper objectMapper,
            AiRerankService aiRerankService,
            LlmGateway llmGateway,
            PostsRepository postsRepository,
            SystemConfigurationService systemConfigurationService,
            DependencyIsolationGuard dependencyIsolationGuard,
            DependencyCircuitBreakerService dependencyCircuitBreakerService
    ) {
        this(
                ragProps,
                indexService,
                embeddingService,
                objectMapper,
                aiRerankService,
                llmGateway,
                null,
                postsRepository,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );
    }

    public RetrieveResult retrieve(String queryText, Long boardId, HybridRetrievalConfigDTO cfg, boolean debug) {
        RetrieveResult out = new RetrieveResult();
        out.setQueryText(queryText == null ? "" : queryText);
        out.setBoardId(boardId);
        out.setConfig(cfg);

        if (queryText == null || queryText.isBlank()) {
            out.setFinalHits(List.of());
            return out;
        }

        int bm25K = cfg == null ? 0 : cfg.getBm25K() == null ? 0 : cfg.getBm25K();
        int vecK = cfg == null ? 0 : cfg.getVecK() == null ? 0 : cfg.getVecK();
        boolean fileVecEnabled = cfg == null || cfg.getFileVecEnabled() == null || cfg.getFileVecEnabled();
        int fileVecK = cfg == null ? 0 : cfg.getFileVecK() == null ? 0 : cfg.getFileVecK();
        int hybridK = cfg == null ? 6 : cfg.getHybridK() == null ? 6 : cfg.getHybridK();
        int maxDocs = cfg == null ? 500 : cfg.getMaxDocs() == null ? 500 : cfg.getMaxDocs();

        bm25K = clampInt(bm25K, 0, Math.max(0, maxDocs));
        vecK = clampInt(vecK, 0, Math.max(0, maxDocs));
        fileVecK = fileVecEnabled ? clampInt(fileVecK, 0, Math.max(0, maxDocs)) : 0;
        hybridK = clampInt(hybridK, 1, Math.max(1, maxDocs));

        long t0 = System.currentTimeMillis();
        List<DocHit> bm25Hits = List.of();
        try {
            if (bm25K > 0) bm25Hits = bm25Search(queryText, boardId, bm25K, cfg);
        } catch (Exception e) {
            out.setBm25Error(e.getMessage());
        }
        out.setBm25Hits(bm25Hits);
        out.setBm25LatencyMs((int) (System.currentTimeMillis() - t0));

        long t1 = System.currentTimeMillis();
        List<DocHit> vecHits = List.of();
        try {
            if (vecK > 0) vecHits = vecSearch(queryText, boardId, vecK);
        } catch (Exception e) {
            out.setVecError(e.getMessage());
        }
        out.setVecHits(vecHits);
        out.setVecLatencyMs((int) (System.currentTimeMillis() - t1));

        long t1f = System.currentTimeMillis();
        List<DocHit> fileVecHits = List.of();
        try {
            if (fileVecK > 0) fileVecHits = fileVecSearch(queryText, fileVecK);
        } catch (Exception e) {
            out.setFileVecError(e.getMessage());
        }
        out.setFileVecHits(fileVecHits);
        out.setFileVecLatencyMs((int) (System.currentTimeMillis() - t1f));

        long t2 = System.currentTimeMillis();
        List<DocHit> fused = fuse(bm25Hits, vecHits, fileVecHits, cfg, maxDocs);
        out.setFusedHits(fused);
        out.setFuseLatencyMs((int) (System.currentTimeMillis() - t2));

        List<DocHit> finalHits = fused;

        boolean rerankEnabled = cfg != null && Boolean.TRUE.equals(cfg.getRerankEnabled());
        int rerankK = cfg == null ? 0 : cfg.getRerankK() == null ? 0 : cfg.getRerankK();
        rerankK = clampInt(rerankK, 0, Math.min(maxDocs, fused.size()));

        if (rerankEnabled && rerankK > 0 && !fused.isEmpty()) {
            long t3 = System.currentTimeMillis();
            try {
                List<DocHit> reranked = rerank(queryText, fused, rerankK, cfg);
                out.setRerankHits(reranked);
                finalHits = reranked;
            } catch (Exception e) {
                out.setRerankError(e.getMessage());
                out.setRerankHits(List.of());
                out.setRerankDegraded(true);
                out.setRerankDegradeReason(e.getMessage());
                log.warn("HybridRag rerank degraded, fallback to fused ranking: reason={}", e.getMessage());
            } finally {
                out.setRerankLatencyMs((int) (System.currentTimeMillis() - t3));
            }
        }

        if (finalHits.size() > hybridK) finalHits = finalHits.subList(0, hybridK);
        out.setFinalHits(finalHits);
        if (debug) out.setDebugInfo(buildDebugInfo(out));
        return out;
    }

    private List<DocHit> bm25Search(String queryText, Long boardId, int size, HybridRetrievalConfigDTO cfg) {
        String indexName = ragProps.getEs().getIndex();
        double titleBoost = cfg == null || cfg.getBm25TitleBoost() == null ? 2.0 : cfg.getBm25TitleBoost();
        double contentBoost = cfg == null || cfg.getBm25ContentBoost() == null ? 1.0 : cfg.getBm25ContentBoost();

        String body = buildBm25Body(queryText, boardId, size, titleBoost, contentBoost);
        JsonNode root = postSearch(indexName, body);
        return filterVisibleHits(parseEsHits(root));
    }

    private List<DocHit> vecSearch(String queryText, Long boardId, int topK) {
        int k = clampInt(topK, 1, 5000);
        String indexName = ragProps.getEs().getIndex();
        RagSearchSupport.EmbeddedQuery embeddedQuery = RagSearchSupport.embedAndEnsureIndex(
                llmGateway,
                ragProps.getEs().getEmbeddingModel(),
                queryText,
                ragProps.getEs().getEmbeddingDims(),
                indexName,
                dims -> indexService.ensureIndex(indexName, dims)
        );
        float[] vec = embeddedQuery.vector();
        if (vec == null || vec.length == 0) return List.of();

        String body = buildKnnBody(k, Math.clamp(k * 10L, 100, 20_000), boardId, vec);
        JsonNode root = postSearch(indexName, body);
        return filterVisibleHits(parseEsHits(root));
    }

    private List<DocHit> fileVecSearch(String queryText, int topK) {
        List<RagFileAssetChatRetrievalService.Hit> raw = ragFileAssetChatRetrievalService.retrieve(queryText, topK);
        if (raw == null || raw.isEmpty()) return List.of();
        List<DocHit> out = new ArrayList<>();
        for (RagFileAssetChatRetrievalService.Hit h : raw) {
            DocHit d = toDocHitFromFileAssetHit(h);
            if (d != null) out.add(d);
        }
        return out;
    }

    private static DocHit toDocHitFromFileAssetHit(RagFileAssetChatRetrievalService.Hit h) {
        if (h == null || h.getDocId() == null) return null;
        DocHit d = new DocHit();
        d.setDocId(h.getDocId());
        d.setScore(h.getScore());
        d.setSourceType("FILE_ASSET");
        d.setFileAssetId(h.getFileAssetId());
        d.setPostIds(h.getPostIds());
        Long firstPostId = null;
        if (h.getPostIds() != null && !h.getPostIds().isEmpty()) firstPostId = h.getPostIds().getFirst();
        d.setPostId(firstPostId);
        d.setChunkIndex(h.getChunkIndex());
        d.setTitle(h.getFileName());
        d.setContentText(h.getContentText());
        return d;
    }

    private List<DocHit> filterVisibleHits(List<DocHit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        java.util.LinkedHashSet<Long> postIds = new java.util.LinkedHashSet<>();
        for (DocHit h : hits) {
            if (h == null || h.getPostId() == null) continue;
            postIds.add(h.getPostId());
        }
        if (postIds.isEmpty()) return hits;

        List<Long> ids = new ArrayList<>(postIds);
        java.util.HashSet<Long> ok = new java.util.HashSet<>();
        postsRepository.findByIdInAndIsDeletedFalseAndStatus(ids, PostStatus.PUBLISHED)
                .forEach(p -> {
                    if (p != null && p.getId() != null) ok.add(p.getId());
                });

        List<DocHit> out = new ArrayList<>();
        for (DocHit h : hits) {
            if (h == null || h.getPostId() == null) continue;
            if (ok.contains(h.getPostId())) out.add(h);
        }
        return out;
    }

    private List<DocHit> fuse(List<DocHit> bm25, List<DocHit> vec, List<DocHit> fileVec, HybridRetrievalConfigDTO cfg, int maxDocs) {
        Map<String, DocHit> m = new LinkedHashMap<>();
        Map<String, Integer> bmRank = new HashMap<>();
        Map<String, Integer> vecRank = new HashMap<>();
        Map<String, Integer> fileVecRank = new HashMap<>();

        if (bm25 != null) {
            for (int i = 0; i < bm25.size(); i++) {
                DocHit h = bm25.get(i);
                if (h == null || h.getDocId() == null) continue;
                bmRank.put(h.getDocId(), i + 1);
                m.putIfAbsent(h.getDocId(), h.copyShallow());
                m.get(h.getDocId()).setBm25Score(h.getScore());
            }
        }
        if (vec != null) {
            for (int i = 0; i < vec.size(); i++) {
                DocHit h = vec.get(i);
                if (h == null || h.getDocId() == null) continue;
                vecRank.put(h.getDocId(), i + 1);
                m.putIfAbsent(h.getDocId(), h.copyShallow());
                m.get(h.getDocId()).setVecScore(h.getScore());
            }
        }
        if (fileVec != null) {
            for (int i = 0; i < fileVec.size(); i++) {
                DocHit h = fileVec.get(i);
                if (h == null || h.getDocId() == null) continue;
                fileVecRank.put(h.getDocId(), i + 1);
                m.putIfAbsent(h.getDocId(), h.copyShallow());
                m.get(h.getDocId()).setFileVecScore(h.getScore());
            }
        }

        String mode = cfg == null || cfg.getFusionMode() == null ? "RRF" : cfg.getFusionMode().trim().toUpperCase();
        if (!mode.equals("RRF") && !mode.equals("LINEAR")) mode = "RRF";

        double wBm25 = cfg == null || cfg.getBm25Weight() == null ? 1.0 : cfg.getBm25Weight();
        double wVec = cfg == null || cfg.getVecWeight() == null ? 1.0 : cfg.getVecWeight();
        double wFileVec = cfg == null || cfg.getFileVecWeight() == null ? 1.0 : cfg.getFileVecWeight();
        int rrfK = cfg == null || cfg.getRrfK() == null ? 60 : Math.max(1, cfg.getRrfK());

        List<DocHit> out = new ArrayList<>();
        if (mode.equals("LINEAR")) {
            double bmMin, bmMax;
            double vMin, vMax;
            double fvMin, fvMax;

            double[] bmRange = scoreRange(bm25);
            bmMin = bmRange[0];
            bmMax = bmRange[1];
            double[] vecRange = scoreRange(vec);
            vMin = vecRange[0];
            vMax = vecRange[1];
            double[] fileVecRange = scoreRange(fileVec);
            fvMin = fileVecRange[0];
            fvMax = fileVecRange[1];

            for (DocHit h : m.values()) {
                double b = h.getBm25Score() == null ? 0.0 : normalizeMinMax(h.getBm25Score(), bmMin, bmMax);
                double v = h.getVecScore() == null ? 0.0 : normalizeMinMax(h.getVecScore(), vMin, vMax);
                double fv = h.getFileVecScore() == null ? 0.0 : normalizeMinMax(h.getFileVecScore(), fvMin, fvMax);
                h.setFusedScore(wBm25 * b + wVec * v + wFileVec * fv);
                out.add(h);
            }
        } else {
            for (DocHit h : m.values()) {
                Integer rb = bmRank.get(h.getDocId());
                Integer rv = vecRank.get(h.getDocId());
                Integer rfv = fileVecRank.get(h.getDocId());
                double s = 0.0;
                if (rb != null) s += wBm25 / (rrfK + rb);
                if (rv != null) s += wVec / (rrfK + rv);
                if (rfv != null) s += wFileVec / (rrfK + rfv);
                h.setFusedScore(s);
                out.add(h);
            }
        }

        out.sort((a, b) -> Double.compare(b.getFusedScore() == null ? 0.0 : b.getFusedScore(), a.getFusedScore() == null ? 0.0 : a.getFusedScore()));
        if (out.size() > maxDocs) out = out.subList(0, maxDocs);
        return out;
    }

    private List<DocHit> rerank(String queryText, List<DocHit> fused, int rerankK, HybridRetrievalConfigDTO cfg) {
        String modelOverride = cfg == null || cfg.getRerankModel() == null || cfg.getRerankModel().isBlank() ? null : cfg.getRerankModel().trim();

        int perDocMaxTokens = cfg == null || cfg.getPerDocMaxTokens() == null ? 800 : cfg.getPerDocMaxTokens();
        int maxInputTokens = cfg == null || cfg.getMaxInputTokens() == null ? 8000 : cfg.getMaxInputTokens();
        int rerankTimeoutMs = cfg == null || cfg.getRerankTimeoutMs() == null ? 12000 : cfg.getRerankTimeoutMs();
        int rerankSlowThresholdMs = cfg == null || cfg.getRerankSlowThresholdMs() == null ? 6000 : cfg.getRerankSlowThresholdMs();

        List<DocHit> candidates = fused.subList(0, Math.min(rerankK, fused.size()));
        List<DocHit> candidatesUsed = new ArrayList<>();
        List<String> docTexts = new ArrayList<>();
        int budgetLeft = Math.max(500, maxInputTokens);
        int queryTokens = approxTokens(queryText == null ? "" : queryText);
        int estimatedInputTokens = queryTokens;

        HybridRerankDocumentSupport.collectDocsWithinBudget(
                candidates,
                candidatesUsed,
                docTexts,
                HybridRagRetrievalService::buildDocText,
                text -> truncateByApproxTokens(text, perDocMaxTokens),
                HybridRagRetrievalService::approxTokens,
                budgetLeft,
                queryTokens
        );
        for (String text : docTexts) {
            estimatedInputTokens += approxTokens(text);
        }

        if (docTexts.isEmpty()) return fused;

        log.info(
                "HybridRag rerank start: queryTokens={}, candidateCount={}, usedCount={}, estimatedInputTokens={}, perDocMaxTokens={}, maxInputTokens={}, timeoutMs={}",
                queryTokens,
                candidates.size(),
                candidatesUsed.size(),
                estimatedInputTokens,
                perDocMaxTokens,
                maxInputTokens,
                rerankTimeoutMs
        );

        long started = System.currentTimeMillis();
        AiRerankService.RerankResult rr;
        try {
            CompletableFuture<AiRerankService.RerankResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return llmGateway.rerankOnceRouted(
                            LlmQueueTaskType.RERANK,
                            null,
                            modelOverride,
                            queryText,
                            docTexts,
                            docTexts.size(),
                            "Given a web search query, retrieve relevant passages that answer the query.",
                            false,
                            null
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            rr = future.get(Math.max(1000, rerankTimeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Rerank timeout after " + Math.max(1000, rerankTimeoutMs) + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rerank interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re && re.getCause() instanceof IOException ioe) {
                throw new IllegalStateException("Rerank upstream failed: " + ioe.getMessage(), ioe);
            }
            String msg = cause == null ? e.getMessage() : cause.getMessage();
            throw new IllegalStateException("Rerank upstream failed: " + msg, e);
        }
        int latencyMs = (int) (System.currentTimeMillis() - started);
        if (latencyMs > rerankSlowThresholdMs) {
            log.warn(
                    "HybridRag rerank slow: latencyMs={}, thresholdMs={}, usedCount={}, estimatedInputTokens={}",
                    latencyMs,
                    rerankSlowThresholdMs,
                    candidatesUsed.size(),
                    estimatedInputTokens
            );
        } else {
            log.info(
                    "HybridRag rerank done: latencyMs={}, usedCount={}, estimatedInputTokens={}",
                    latencyMs,
                    candidatesUsed.size(),
                    estimatedInputTokens
            );
        }
        if (rr == null || rr.results() == null || rr.results().isEmpty()) return fused;

        List<DocHit> reranked = new ArrayList<>();
        int rank = 0;
        Set<String> seen = new HashSet<>();
        for (AiRerankService.RerankHit hit : rr.results()) {
            if (hit == null) continue;
            int idx = hit.index();
            if (idx < 0 || idx >= candidatesUsed.size()) continue;
            DocHit h = candidatesUsed.get(idx);
            if (h == null || h.getDocId() == null) continue;
            if (!seen.add(h.getDocId())) continue;
            DocHit hh = h.copyShallow();
            hh.setRerankScore(hit.relevanceScore());
            hh.setRerankRank(++rank);
            reranked.add(hh);
        }

        for (DocHit h : candidatesUsed) {
            if (h == null || h.getDocId() == null) continue;
            if (seen.contains(h.getDocId())) continue;
            DocHit hh = h.copyShallow();
            hh.setRerankScore(null);
            hh.setRerankRank(++rank);
            reranked.add(hh);
        }

        for (int i = candidatesUsed.size(); i < candidates.size(); i++) {
            DocHit h = candidates.get(i);
            if (h == null) continue;
            DocHit hh = h.copyShallow();
            hh.setRerankScore(null);
            hh.setRerankRank(++rank);
            reranked.add(hh);
        }

        List<DocHit> finalHits = new ArrayList<>(reranked);
        for (int i = rerankK; i < fused.size(); i++) finalHits.add(fused.get(i));
        return finalHits;
    }

    private static String buildDocText(DocHit h) {
        StringBuilder sb = new StringBuilder();
        if (h.getTitle() != null && !h.getTitle().isBlank()) {
            sb.append(h.getTitle().trim()).append('\n');
        }
        if (h.getContentText() != null) sb.append(h.getContentText());
        return sb.toString();
    }

    private static String buildRerankPrompt(String query, List<Map<String, Object>> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("query:\n").append(query).append("\n\n");
        sb.append("candidates:\n");
        for (Map<String, Object> d : docs) {
            sb.append("- doc_id: ").append(Objects.toString(d.get("doc_id"), "")).append('\n');
            String title = Objects.toString(d.get("title"), "");
            if (!title.isBlank()) sb.append("  title: ").append(title).append('\n');
            sb.append("  text: ").append(Objects.toString(d.get("text"), "")).append("\n\n");
        }
        sb.append("Output format (Strict JSON, no explanation):\n");
        sb.append("{\"ranked\":[{\"doc_id\":\"...\",\"score\":0.0}]}\n");
        return sb.toString();
    }

    private String extractAssistantContent(String rawJson) {
        return rawJson == null ? "" : AiResponseParsingUtils.extractAssistantContent(objectMapper, rawJson);
    }

    private List<ScoredDoc> parseRankingFromAssistant(String assistantText) {
        if (assistantText == null) return List.of();
        String t = assistantText.trim();
        int l = t.indexOf('{');
        int r = t.lastIndexOf('}');
        if (l >= 0 && r > l) t = t.substring(l, r + 1);

        try {
            JsonNode root = objectMapper.readTree(t);
            JsonNode arr = root.path("ranked");
            if (!arr.isArray()) return List.of();
            List<ScoredDoc> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String id = n.path("doc_id").asText(null);
                double score = n.hasNonNull("score") ? n.path("score").asDouble() : 0.0;
                if (id == null || id.isBlank()) continue;
                ScoredDoc sd = new ScoredDoc();
                sd.docId = id;
                sd.score = score;
                out.add(sd);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private JsonNode postSearch(String indexName, String body) {
        dependencyIsolationGuard.requireElasticsearchAllowed();
        return dependencyCircuitBreakerService.run("ES", () -> {
            String endpoint = systemConfigurationService.getConfig("spring.elasticsearch.uris");
            if (endpoint == null || endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
            if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
            if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

            try {
                URL url = java.net.URI.create(endpoint + "/" + indexName + "/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source,hits.hits.highlight").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(20_000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is == null) return objectMapper.createObjectNode();
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (code < 200 || code >= 300) throw new IllegalStateException("ES error HTTP " + code + ": " + json);
                return objectMapper.readTree(json);
            } catch (Exception e) {
                throw new IllegalStateException("ES search failed: " + e.getMessage(), e);
            }
        });
    }

    private List<DocHit> parseEsHits(JsonNode root) {
        List<DocHit> hits = new ArrayList<>();
        JsonNode arr = root == null ? null : root.path("hits").path("hits");
        if (arr != null && arr.isArray()) {
            for (JsonNode h : arr) {
                DocHit out = new DocHit();
                out.setDocId(h.path("_id").asText(null));
                if (h.hasNonNull("_score")) out.setScore(h.path("_score").asDouble());
                JsonNode src = h.path("_source");
                JsonNode hl = h.path("highlight");
                if (src.hasNonNull("post_id")) out.setPostId(src.path("post_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                if (src.hasNonNull("board_id")) out.setBoardId(src.path("board_id").asLong());
                out.setSourceType("POST");
                out.setTitle(src.path("title").asText(null));
                out.setContentText(src.path("content_text").asText(null));
                out.setTitleHighlight(firstHighlightFragment(hl, "title"));
                out.setContentHighlight(firstHighlightFragment(hl, "content_text"));
                hits.add(out);
            }
        }
        return hits;
    }

    private static String buildBm25Body(String query, Long boardId, int size, double titleBoost, double contentBoost) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"track_total_hits\":false");
        sb.append(",\"query\":{\"bool\":{");
        sb.append("\"must\":[{\"multi_match\":{");
        sb.append("\"query\":\"").append(escapeJson(query)).append("\"");
        sb.append(",\"type\":\"best_fields\"");
        sb.append(",\"fields\":[");
        sb.append("\"title^").append(trimTrailingZeros(titleBoost)).append("\"");
        sb.append(",\"content_text^").append(trimTrailingZeros(contentBoost)).append("\"");
        sb.append("]}}]");
        sb.append(",\"filter\":[");
        if (boardId != null) {
            sb.append("{\"term\":{\"board_id\":").append(boardId).append("}}");
        }
        sb.append("]}}");
        appendHighlightClause(sb);
        sb.append('}');
        return sb.toString();
    }

    private static String buildKnnBody(int size, int numCandidates, Long boardId, float[] vec) {
        return RagPostSearchJsonSupport.buildKnnSearchBody(size, numCandidates, boardId, vec, true);
    }

    private static void appendHighlightClause(StringBuilder sb) {
        sb.append(",\"highlight\":{");
        sb.append("\"pre_tags\":[\"<em>\"],\"post_tags\":[\"</em>\"],");
        sb.append("\"fields\":{");
        sb.append("\"content_text\":{\"number_of_fragments\":1,\"fragment_size\":220},");
        sb.append("\"title\":{\"number_of_fragments\":1,\"fragment_size\":120}");
        sb.append("}}");
    }

    private static String firstHighlightFragment(JsonNode highlightNode, String field) {
        return RagSearchSupport.firstHighlightFragment(highlightNode, field);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String trimTrailingZeros(double v) {
        String s = Double.toString(v);
        if (s.indexOf('.') < 0) return s;
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static double normalizeMinMax(double v, double min, double max) {
        if (Double.isInfinite(min) || Double.isInfinite(max) || Double.isNaN(min) || Double.isNaN(max)) return 0.0;
        if (max <= min) return 0.0;
        double x = (v - min) / (max - min);
        if (x < 0) x = 0;
        if (x > 1) x = 1;
        return x;
    }

    private static double[] scoreRange(List<DocHit> hits) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        if (hits != null) {
            for (DocHit h : hits) {
                if (h == null || h.getScore() == null) continue;
                min = Math.min(min, h.getScore());
                max = Math.max(max, h.getScore());
            }
        }
        return new double[]{min, max};
    }

    private static int clampInt(int v, int min, int max) {
        int x = v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static int clampInt(Integer v, int min, int max) {
        if (v == null) return min;
        return clampInt(v.intValue(), min, max);
    }

    private static int approxTokens(String s) {
        return ApproxTokenSupport.approxTokens(s);
    }

    private static String truncateByApproxTokens(String s, int maxTokens) {
        return ApproxTokenSupport.truncateByApproxTokens(s, maxTokens);
    }

    private static Map<String, Object> buildDebugInfo(RetrieveResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bm25LatencyMs", r.getBm25LatencyMs());
        m.put("vecLatencyMs", r.getVecLatencyMs());
        m.put("fileVecLatencyMs", r.getFileVecLatencyMs());
        m.put("fuseLatencyMs", r.getFuseLatencyMs());
        m.put("rerankLatencyMs", r.getRerankLatencyMs());
        m.put("bm25Error", r.getBm25Error());
        m.put("vecError", r.getVecError());
        m.put("fileVecError", r.getFileVecError());
        m.put("rerankError", r.getRerankError());
        m.put("rerankDegraded", r.getRerankDegraded());
        m.put("rerankDegradeReason", r.getRerankDegradeReason());
        return m;
    }


    @Data
    public static class RetrieveResult {
        private String queryText;
        private Long boardId;
        private HybridRetrievalConfigDTO config;

        private Integer bm25LatencyMs;
        private Integer vecLatencyMs;
        private Integer fileVecLatencyMs;
        private Integer fuseLatencyMs;
        private Integer rerankLatencyMs;

        private String bm25Error;
        private String vecError;
        private String fileVecError;
        private String rerankError;
        private Boolean rerankDegraded;
        private String rerankDegradeReason;

        private List<DocHit> bm25Hits;
        private List<DocHit> vecHits;
        private List<DocHit> fileVecHits;
        private List<DocHit> fusedHits;
        private List<DocHit> rerankHits;
        private List<DocHit> finalHits;

        private Map<String, Object> debugInfo;
    }

    @Data
    public static class DocHit {
        private String docId;
        private Double score;
        private Long postId;
        private List<Long> postIds;
        private Long fileAssetId;
        private Integer chunkIndex;
        private Long boardId;
        private String sourceType;
        private String title;
        private String contentText;
        private String titleHighlight;
        private String contentHighlight;

        private Double bm25Score;
        private Double vecScore;
        private Double fileVecScore;
        private Double fusedScore;

        private Integer rerankRank;
        private Double rerankScore;

        private DocHit copyShallow() {
            DocHit h = new DocHit();
            h.docId = this.docId;
            h.score = this.score;
            h.postId = this.postId;
            h.postIds = this.postIds == null ? null : new ArrayList<>(this.postIds);
            h.fileAssetId = this.fileAssetId;
            h.chunkIndex = this.chunkIndex;
            h.boardId = this.boardId;
            h.sourceType = this.sourceType;
            h.title = this.title;
            h.contentText = this.contentText;
            h.titleHighlight = this.titleHighlight;
            h.contentHighlight = this.contentHighlight;
            h.bm25Score = this.bm25Score;
            h.vecScore = this.vecScore;
            h.fileVecScore = this.fileVecScore;
            h.fusedScore = this.fusedScore;
            h.rerankRank = this.rerankRank;
            h.rerankScore = this.rerankScore;
            return h;
        }
    }

    private static class ScoredDoc {
        String docId;
        double score;
    }
}
