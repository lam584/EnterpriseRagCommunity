package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarHitsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarHitsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ModerationSimilarityService {

    private final AiEmbeddingService embeddingService;
    private final LlmGateway llmGateway;
    private final LlmRoutingService llmRoutingService;
    private final ModerationSamplesIndexService indexService;
    private final ModerationSamplesIndexConfigService indexConfigService;
    private final ModerationSimilarHitsRepository similarHitsRepository;
    private final ModerationSimilarityConfigRepository configRepository;
    private final ModerationPolicyConfigRepository policyConfigRepository;
    private final SystemConfigurationService systemConfigurationService;

    public SimilarityCheckResponse check(SimilarityCheckRequest req) {
        if (req == null) throw new IllegalArgumentException("req is null");

        String text = req.getText();
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text is required");

        ModerationSimilarityConfigEntity cfg = loadConfigOrNull();

        int topK = firstNonNull(req.getTopK(), cfg == null ? null : cfg.getDefaultTopK(), 5);
        topK = Math.clamp(topK, 1, 50);

        double threshold = resolveThreshold(req.getThreshold(), req.getContentType());
        if (threshold < 0) threshold = 0;

        Integer numCandidates0 = firstNonNull(req.getNumCandidates(), cfg == null ? null : cfg.getDefaultNumCandidates(), null);
        int numCandidates;
        if (numCandidates0 == null || numCandidates0 <= 0) {
            numCandidates = Math.max(100, topK * 10);
        } else {
            numCandidates = Math.clamp(numCandidates0, 10, 10_000);
        }

        Integer maxInputChars0 = firstNonNull(req.getMaxInputChars(), cfg == null ? null : cfg.getMaxInputChars(), 0);
        int maxInputChars = Math.max(0, maxInputChars0);

        String modelToUse = toNonBlank(req.getEmbeddingModel());
        boolean reqHasModel = modelToUse != null;
        if (modelToUse == null) modelToUse = toNonBlank(cfg == null ? null : cfg.getEmbeddingModel());
        if (modelToUse == null) modelToUse = toNonBlank(indexConfigService.getEmbeddingModelOrDefault());
        if (!reqHasModel && modelToUse != null) {
            String candidateModel = modelToUse;
            boolean enabled = llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING)
                    .stream()
                    .anyMatch(t -> t != null && candidateModel.equals(t.modelName()));
            if (!enabled) modelToUse = null;
        }

        Integer configuredDimsOverride = req.getEmbeddingDims();
    Integer configuredDimsCfg = cfg == null ? null : cfg.getEmbeddingDims();
    int configuredDims = firstNonNull(configuredDimsOverride, configuredDimsCfg, indexConfigService.getEmbeddingDimsOrDefault());
        if (configuredDims < 0) configuredDims = 0;

        // Embedding
        AiEmbeddingService.EmbeddingResult er;
        try {
            String inputText = truncateByChars(text, maxInputChars);
            if (modelToUse == null) {
                er = llmGateway.embedOnceRouted(LlmQueueTaskType.SIMILARITY_EMBEDDING, null, null, inputText);
            } else {
                er = embeddingService.embedOnceForTask(inputText, modelToUse, null, LlmQueueTaskType.SIMILARITY_EMBEDDING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }

        float[] vec = er == null ? null : er.vector();
        if (vec == null || vec.length == 0) {
            throw new IllegalStateException("Embedding returned empty vector; cannot run vector search");
        }

        // Prefer configured dims, but allow auto-infer from embedding result when not configured.
        int inferredDims = vec.length;
        if (configuredDims > 0 && configuredDims != inferredDims) {
            throw new IllegalStateException(
                    "Embedding dims mismatch: configuredDims=" + configuredDims
                            + " but embedding service returned vector length=" + inferredDims
                            + ". Please fix config and (re)create ES index mapping 'embedding' with correct dims.");
        }

        // Ensure ES index exists (mapping depends on embedding dims). If dims wasn't configured, auto-infer.
        int dimsToUse = configuredDims > 0 ? configuredDims : inferredDims;
        indexService.ensureIndex(dimsToUse);

        List<SimilarityCheckResponse.Hit> hits = queryKnn(topK, numCandidates, vec);

        SimilarityCheckResponse resp = new SimilarityCheckResponse();
        resp.setThreshold(threshold);
        resp.setTopK(topK);
        resp.setNumCandidates(numCandidates);
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(er.model());
        resp.setMaxInputChars(maxInputChars);
        resp.setHits(hits);

        Double best = hits.isEmpty() ? null : hits.getFirst().getDistance();
        resp.setBestDistance(best);
        resp.setHit(best != null && best <= threshold);

        // Optional: persist audit hit into MySQL if request contains content linkage.
        if (req.getContentType() != null && req.getContentId() != null) {
            persistHits(req.getContentType(), req.getContentId(), threshold, hits);
        }

        return resp;
    }

    private double resolveThreshold(Double reqThreshold, ContentType contentType) {
        if (reqThreshold != null) return reqThreshold;
        if (contentType != null) {
            ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(contentType).orElse(null);
            if (policy != null && policy.getConfig() != null) {
                try {
                    Map<String, Object> precheck = (Map<String, Object>) policy.getConfig().get("precheck");
                    if (precheck != null) {
                        Map<String, Object> vec = (Map<String, Object>) precheck.get("vec");
                        if (vec != null && vec.get("threshold") != null) {
                            return ((Number) vec.get("threshold")).doubleValue();
                        }
                    }
                } catch (Exception ignore) {}
            }
        }
        return 0.15; // default fallback
    }

    private ModerationSimilarityConfigEntity loadConfigOrNull() {
        try {
            return configRepository.findAll().stream().findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String truncateByChars(String s, int maxChars) {
        if (s == null) return "";
        if (maxChars <= 0) return s;
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars);
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static Integer firstNonNull(Integer a, Integer b, Integer c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }

    private static Double firstNonNull(Double a, Double b, Double c) {
        if (a != null) return a;
        if (b != null) return b;
        return c;
    }

    private void persistHits(ContentType contentType, Long contentId, double threshold, List<SimilarityCheckResponse.Hit> hits) {
        if (hits == null) return;
        LocalDateTime now = LocalDateTime.now();
        for (SimilarityCheckResponse.Hit h : hits) {
            if (h == null || h.getDistance() == null) continue;
            if (h.getDistance() > threshold) continue;

            ModerationSimilarHitsEntity e = new ModerationSimilarHitsEntity();
            e.setContentType(contentType);
            e.setContentId(contentId);
            e.setCandidateId(h.getSampleId());
            e.setDistance(h.getDistance());
            e.setThreshold(threshold);
            e.setMatchedAt(now);
            similarHitsRepository.save(e);
        }
    }

    /**
     * Query ES kNN and return hits with cosine distance.
     * Uses a minimal REST call to avoid tight coupling to client internals.
     */
    private List<SimilarityCheckResponse.Hit> queryKnn(int topK, int numCandidates, float[] queryVector) {
        try {
            String body = buildKnnSearchBody(topK, numCandidates, queryVector);

            String elasticsearchUris = systemConfigurationService.getConfig("spring.elasticsearch.uris");
            if (elasticsearchUris == null || elasticsearchUris.isBlank()) {
                elasticsearchUris = "http://127.0.0.1:9200";
            }
            String elasticsearchApiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");

            String endpoint = elasticsearchUris;
            if (endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
            // Support comma-separated uris; take the first one for now.
            if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
            if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

            URI uri = URI.create(endpoint + "/" + indexService.getIndexName() + "/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            // Auth: ApiKey only
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey.trim());
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) throw new IllegalStateException("ES returned HTTP " + code + " without body");

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("ES error HTTP " + code + ": " + json);
            }

            return parseHits(json);
        } catch (Exception e) {
            throw new IllegalStateException("ES kNN search failed: " + e.getMessage(), e);
        }
    }

    private static String buildKnnSearchBody(int size, int numCandidates, float[] vec) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"query\":{\"bool\":{\"filter\":[{\"term\":{\"enabled\":true}}]}}");
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    /**
     * Minimal parser to extract _id and _score and a few _source fields.
     */
    static List<SimilarityCheckResponse.Hit> parseHits(String json) {
        if (json == null) return List.of();

        List<SimilarityCheckResponse.Hit> out = new ArrayList<>();
        int idx = 0;
        while (true) {
            int idKey = json.indexOf("\"_id\"", idx);
            if (idKey < 0) break;

            int q1 = json.indexOf('"', json.indexOf(':', idKey));
            int q2 = json.indexOf('"', q1 + 1);
            String idStr = (q1 >= 0 && q2 > q1) ? json.substring(q1 + 1, q2) : null;

            int scoreKey = json.indexOf("\"_score\"", q2);
            int scoreStart = json.indexOf(':', scoreKey) + 1;
            int scoreEnd = scoreStart;
            while (scoreEnd < json.length() && "0123456789.-".indexOf(json.charAt(scoreEnd)) >= 0) scoreEnd++;
            double score = Double.parseDouble(json.substring(scoreStart, scoreEnd));
            double distance = 1.0 - score;

            SimilarityCheckResponse.Hit h = new SimilarityCheckResponse.Hit();
            try {
                h.setSampleId(idStr == null ? null : Long.parseLong(idStr));
            } catch (Exception ignore) {
                h.setSampleId(null);
            }
            h.setDistance(distance);

            h.setCategory(extractSourceString(json, "category", scoreEnd));
            h.setRiskLevel(extractSourceInt(json, "risk_level", scoreEnd));
            String raw = extractSourceString(json, "raw_text", scoreEnd);
            if (raw != null && raw.length() > 80) raw = raw.substring(0, 80) + "...";
            h.setRawTextPreview(raw);

            out.add(h);
            idx = scoreEnd;
        }

        out.sort(Comparator.comparing(SimilarityCheckResponse.Hit::getDistance, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private static String extractSourceString(String json, String key, int from) {
        String pat = "\"" + key + "\"";
        int k = json.indexOf(pat, from);
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static Integer extractSourceInt(String json, String key, int from) {
        String pat = "\"" + key + "\"";
        int k = json.indexOf(pat, from);
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int j = i;
        while (j < json.length() && "0123456789-".indexOf(json.charAt(j)) >= 0) j++;
        if (j <= i) return null;
        try {
            return Integer.parseInt(json.substring(i, j));
        } catch (Exception e) {
            return null;
        }
    }
}
