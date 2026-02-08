package com.example.EnterpriseRagCommunity.service.retrieval;

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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HybridRagRetrievalService {

    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    private final AiEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final AiRerankService aiRerankService;
    private final LlmGateway llmGateway;
    private final PostsRepository postsRepository;

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String elasticsearchUris;

    @Value("${app.es.api-key:}")
    private String elasticsearchApiKey;

    public RetrieveResult retrieve(String queryText, Long boardId, HybridRetrievalConfigDTO cfg, boolean debug) {
        RetrieveResult out = new RetrieveResult();
        out.setQueryText(queryText == null ? "" : queryText);
        out.setBoardId(boardId);
        out.setConfig(cfg);

        if (queryText == null || queryText.isBlank()) {
            out.setFinalHits(List.of());
            return out;
        }

        HybridRetrievalConfigDTO safe = cfg == null ? null : cfg;
        int bm25K = safe == null ? 0 : safe.getBm25K() == null ? 0 : safe.getBm25K();
        int vecK = safe == null ? 0 : safe.getVecK() == null ? 0 : safe.getVecK();
        int hybridK = safe == null ? 6 : safe.getHybridK() == null ? 6 : safe.getHybridK();
        int maxDocs = safe == null ? 500 : safe.getMaxDocs() == null ? 500 : safe.getMaxDocs();

        bm25K = clampInt(bm25K, 0, Math.max(0, maxDocs));
        vecK = clampInt(vecK, 0, Math.max(0, maxDocs));
        hybridK = clampInt(hybridK, 1, Math.max(1, maxDocs));

        long t0 = System.currentTimeMillis();
        List<DocHit> bm25Hits = List.of();
        try {
            if (bm25K > 0) bm25Hits = bm25Search(queryText, boardId, bm25K, safe);
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

        long t2 = System.currentTimeMillis();
        List<DocHit> fused = fuse(bm25Hits, vecHits, safe, maxDocs);
        out.setFusedHits(fused);
        out.setFuseLatencyMs((int) (System.currentTimeMillis() - t2));

        List<DocHit> finalHits = fused;

        boolean rerankEnabled = safe != null && Boolean.TRUE.equals(safe.getRerankEnabled());
        int rerankK = safe == null ? 0 : safe.getRerankK() == null ? 0 : safe.getRerankK();
        rerankK = clampInt(rerankK, 0, Math.min(maxDocs, fused.size()));

        if (rerankEnabled && rerankK > 0 && !fused.isEmpty()) {
            long t3 = System.currentTimeMillis();
            try {
                List<DocHit> reranked = rerank(queryText, fused, rerankK, safe);
                out.setRerankHits(reranked);
                finalHits = reranked;
            } catch (Exception e) {
                out.setRerankError(e.getMessage());
                out.setRerankHits(List.of());
                finalHits = fused;
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

        AiEmbeddingService.EmbeddingResult er;
        try {
            er = embeddingService.embedOnce(queryText, ragProps.getEs().getEmbeddingModel());
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }
        float[] vec = er == null ? null : er.vector();
        if (vec == null || vec.length == 0) return List.of();

        int configuredDims = ragProps.getEs().getEmbeddingDims();
        int inferredDims = vec.length;
        if (configuredDims > 0 && configuredDims != inferredDims) {
            throw new IllegalStateException("Embedding dims mismatch: configured=" + configuredDims + " but embedding length=" + inferredDims);
        }
        int dimsToUse = configuredDims > 0 ? configuredDims : inferredDims;

        String indexName = ragProps.getEs().getIndex();
        try {
            indexService.ensureIndex(indexName, dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }

        String body = buildKnnBody(k, Math.max(100, Math.min(20_000, k * 10)), boardId, vec);
        JsonNode root = postSearch(indexName, body);
        return filterVisibleHits(parseEsHits(root));
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

    private List<DocHit> fuse(List<DocHit> bm25, List<DocHit> vec, HybridRetrievalConfigDTO cfg, int maxDocs) {
        Map<String, DocHit> m = new LinkedHashMap<>();
        Map<String, Integer> bmRank = new HashMap<>();
        Map<String, Integer> vecRank = new HashMap<>();

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

        String mode = cfg == null || cfg.getFusionMode() == null ? "RRF" : cfg.getFusionMode().trim().toUpperCase();
        if (!mode.equals("RRF") && !mode.equals("LINEAR")) mode = "RRF";

        double wBm25 = cfg == null || cfg.getBm25Weight() == null ? 1.0 : cfg.getBm25Weight();
        double wVec = cfg == null || cfg.getVecWeight() == null ? 1.0 : cfg.getVecWeight();
        int rrfK = cfg == null || cfg.getRrfK() == null ? 60 : Math.max(1, cfg.getRrfK());

        List<DocHit> out = new ArrayList<>();
        if (mode.equals("LINEAR")) {
            double bmMin = Double.POSITIVE_INFINITY, bmMax = Double.NEGATIVE_INFINITY;
            double vMin = Double.POSITIVE_INFINITY, vMax = Double.NEGATIVE_INFINITY;

            if (bm25 != null) {
                for (DocHit h : bm25) {
                    if (h == null || h.getScore() == null) continue;
                    bmMin = Math.min(bmMin, h.getScore());
                    bmMax = Math.max(bmMax, h.getScore());
                }
            }
            if (vec != null) {
                for (DocHit h : vec) {
                    if (h == null || h.getScore() == null) continue;
                    vMin = Math.min(vMin, h.getScore());
                    vMax = Math.max(vMax, h.getScore());
                }
            }

            for (DocHit h : m.values()) {
                double b = h.getBm25Score() == null ? 0.0 : normalizeMinMax(h.getBm25Score(), bmMin, bmMax);
                double v = h.getVecScore() == null ? 0.0 : normalizeMinMax(h.getVecScore(), vMin, vMax);
                h.setFusedScore(wBm25 * b + wVec * v);
                out.add(h);
            }
        } else {
            for (DocHit h : m.values()) {
                Integer rb = bmRank.get(h.getDocId());
                Integer rv = vecRank.get(h.getDocId());
                double s = 0.0;
                if (rb != null) s += wBm25 / (rrfK + rb);
                if (rv != null) s += wVec / (rrfK + rv);
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

        int perDocMaxTokens = cfg == null || cfg.getPerDocMaxTokens() == null ? 4000 : cfg.getPerDocMaxTokens();
        int maxInputTokens = cfg == null || cfg.getMaxInputTokens() == null ? 30000 : cfg.getMaxInputTokens();

        List<DocHit> candidates = fused.subList(0, Math.min(rerankK, fused.size()));
        List<DocHit> candidatesUsed = new ArrayList<>();
        List<String> docTexts = new ArrayList<>();
        int budgetLeft = Math.max(500, maxInputTokens);
        int queryTokens = approxTokens(queryText == null ? "" : queryText);

        for (DocHit h : candidates) {
            if (h == null || h.getDocId() == null) continue;
            String t = buildDocText(h);
            t = truncateByApproxTokens(t, perDocMaxTokens);
            int tokens = approxTokens(t);
            if (tokens <= 0) continue;
            int cost = tokens + Math.max(0, queryTokens);
            if (budgetLeft - cost < 200) break;
            budgetLeft -= cost;
            candidatesUsed.add(h);
            docTexts.add(t);
        }

        if (docTexts.isEmpty()) return fused;

        AiRerankService.RerankResult rr;
        try {
            rr = llmGateway.rerankOnceRouted(
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
            throw new IllegalStateException("Rerank upstream failed: " + e.getMessage(), e);
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
        sb.append("输出格式（严格 JSON，不要解释）：\n");
        sb.append("{\"ranked\":[{\"doc_id\":\"...\",\"score\":0.0}]}\n");
        return sb.toString();
    }

    private String extractAssistantContent(String rawJson) {
        if (rawJson == null) return "";
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (contentNode.isTextual()) return contentNode.asText();
                JsonNode textNode = first.path("text");
                if (textNode.isTextual()) return textNode.asText();
            }
        } catch (Exception ignore) {
        }
        return rawJson;
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
        String endpoint = elasticsearchUris;
        if (endpoint == null || endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
        if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

        try {
            URL url = new URL(endpoint + "/" + indexName + "/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(20_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey.trim());
            }

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
                if (src.hasNonNull("post_id")) out.setPostId(src.path("post_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                if (src.hasNonNull("board_id")) out.setBoardId(src.path("board_id").asLong());
                out.setTitle(src.path("title").asText(null));
                out.setContentText(src.path("content_text").asText(null));
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
        sb.append('}');
        return sb.toString();
    }

    private static String buildKnnBody(int size, int numCandidates, Long boardId, float[] vec) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"query\":{\"bool\":{\"filter\":[");
        if (boardId != null) {
            sb.append("{\"term\":{\"board_id\":").append(boardId).append("}}");
        }
        sb.append("]}}");
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vec[i]));
        }
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        sb.append('}');
        return sb.toString();
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

    private static int clampInt(int v, int min, int max) {
        int x = v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static int clampInt(Integer v, int min, int max) {
        if (v == null) return min;
        return clampInt(v, min, max);
    }

    private static int approxTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        double t = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x7f) t += 0.25;
            else t += 1.0;
        }
        return (int) Math.ceil(t);
    }

    private static String truncateByApproxTokens(String s, int maxTokens) {
        if (s == null) return "";
        if (maxTokens <= 0) return "";
        double t = 0;
        int end = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            t += (c <= 0x7f) ? 0.25 : 1.0;
            if (t > maxTokens) break;
            end = i + 1;
        }
        return s.substring(0, end);
    }

    private static Map<String, Object> buildDebugInfo(RetrieveResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bm25LatencyMs", r.getBm25LatencyMs());
        m.put("vecLatencyMs", r.getVecLatencyMs());
        m.put("fuseLatencyMs", r.getFuseLatencyMs());
        m.put("rerankLatencyMs", r.getRerankLatencyMs());
        m.put("bm25Error", r.getBm25Error());
        m.put("vecError", r.getVecError());
        m.put("rerankError", r.getRerankError());
        return m;
    }

    @Data
    public static class RetrieveResult {
        private String queryText;
        private Long boardId;
        private HybridRetrievalConfigDTO config;

        private Integer bm25LatencyMs;
        private Integer vecLatencyMs;
        private Integer fuseLatencyMs;
        private Integer rerankLatencyMs;

        private String bm25Error;
        private String vecError;
        private String rerankError;

        private List<DocHit> bm25Hits;
        private List<DocHit> vecHits;
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
        private Integer chunkIndex;
        private Long boardId;
        private String title;
        private String contentText;

        private Double bm25Score;
        private Double vecScore;
        private Double fusedScore;

        private Integer rerankRank;
        private Double rerankScore;

        private DocHit copyShallow() {
            DocHit h = new DocHit();
            h.docId = this.docId;
            h.score = this.score;
            h.postId = this.postId;
            h.chunkIndex = this.chunkIndex;
            h.boardId = this.boardId;
            h.title = this.title;
            h.contentText = this.contentText;
            h.bm25Score = this.bm25Score;
            h.vecScore = this.vecScore;
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
