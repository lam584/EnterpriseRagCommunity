package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.EnterpriseRagCommunity.service.ai.ApproxTokenSupport;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRerankTestDocumentDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRerankTestHitDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRerankTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRerankTestResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalEventLogDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalHitLogDTO;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.access.DateTimeParamSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRerankDocumentSupport;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalLogsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/retrieval/hybrid")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRetrievalHybridController {

    private final HybridRetrievalConfigService hybridRetrievalConfigService;
    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final HybridRetrievalLogsService hybridRetrievalLogsService;
    private final AiRerankService aiRerankService;
    private final LlmGateway llmGateway;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','access'))")
    public HybridRetrievalConfigDTO getConfig() {
        return hybridRetrievalConfigService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','write'))")
    public HybridRetrievalConfigDTO updateConfig(@RequestBody HybridRetrievalConfigDTO payload) {
        return hybridRetrievalConfigService.updateConfig(payload);
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','write'))")
    public HybridRagRetrievalService.RetrieveResult test(@RequestBody HybridRetrievalTestRequest req) {
        String query = req == null ? null : req.getQueryText();
        Long boardId = req == null ? null : req.getBoardId();
        boolean debug = req != null && Boolean.TRUE.equals(req.getDebug());

        HybridRetrievalConfigDTO cfg;
        if (req == null || Boolean.TRUE.equals(req.getUseSavedConfig()) || req.getConfig() == null) {
            cfg = hybridRetrievalConfigService.getConfigOrDefault();
        } else {
            cfg = hybridRetrievalConfigService.normalizeConfig(req.getConfig());
        }
        return hybridRagRetrievalService.retrieve(query, boardId, cfg, debug);
    }

    @PostMapping("/test-rerank")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','write'))")
    public HybridRerankTestResponse testRerank(@RequestBody HybridRerankTestRequest req) {
        HybridRerankTestResponse out = new HybridRerankTestResponse();
        String query = req == null ? null : req.getQueryText();
        String queryFinal = query == null ? "" : query;
        boolean debug = req != null && Boolean.TRUE.equals(req.getDebug());
        out.setQueryText(queryFinal);

        List<HybridRerankTestDocumentDTO> docs = normalizeDocs(req == null ? null : req.getDocuments());

        if (queryFinal.isBlank()) {
            out.setOk(false);
            out.setErrorMessage("queryText is required");
            out.setResults(List.of());
            return out;
        }
        if (docs.isEmpty()) {
            out.setOk(false);
            out.setErrorMessage("documents is required");
            out.setResults(List.of());
            return out;
        }

        HybridRetrievalConfigDTO cfg;
        if (Boolean.TRUE.equals(req.getUseSavedConfig()) || req.getConfig() == null) {
            cfg = hybridRetrievalConfigService.getConfigOrDefault();
        } else {
            cfg = hybridRetrievalConfigService.normalizeConfig(req.getConfig());
        }

        int perDocMaxTokens = cfg == null || cfg.getPerDocMaxTokens() == null ? 4000 : cfg.getPerDocMaxTokens();
        int maxInputTokens = cfg == null || cfg.getMaxInputTokens() == null ? 30000 : cfg.getMaxInputTokens();
        int budgetLeft = Math.max(500, maxInputTokens);
        int queryTokens = approxTokens(queryFinal);

        List<HybridRerankTestDocumentDTO> docsUsed = new ArrayList<>();
        List<String> docTexts = new ArrayList<>();
        HybridRerankDocumentSupport.collectDocsWithinBudget(
                docs,
                docsUsed,
                docTexts,
                AdminRetrievalHybridController::buildDocText,
                text -> truncateByApproxTokens(text, perDocMaxTokens),
                AdminRetrievalHybridController::approxTokens,
                budgetLeft,
                queryTokens
        );

        if (docTexts.isEmpty()) {
            out.setOk(false);
            out.setErrorMessage("documents is empty after token budget truncation");
            out.setResults(List.of());
            return out;
        }

        int topN = req.getTopN() == null ? docsUsed.size() : Math.max(1, req.getTopN());
        if (topN > docsUsed.size()) topN = docsUsed.size();
        out.setTopN(topN);

        long t0 = System.currentTimeMillis();
        try {
            AiRerankService.RerankResult rr = llmGateway.rerankOnceRouted(
                    LlmQueueTaskType.RERANK,
                    null,
                    cfg == null ? null : cfg.getRerankModel(),
                    queryFinal,
                    docTexts,
                    topN,
                    "Given a web search query, retrieve relevant passages that answer the query.",
                    false,
                    null
            );
            out.setLatencyMs((int) (System.currentTimeMillis() - t0));
            out.setOk(true);
            out.setUsedProviderId(rr == null ? null : rr.providerId());
            out.setUsedModel(rr == null ? null : rr.model());
            out.setTotalTokens(rr == null ? null : rr.totalTokens());

            out.setResults(toRerankHits(rr, docsUsed));
        } catch (Exception e) {
            out.setLatencyMs((int) (System.currentTimeMillis() - t0));
            out.setOk(false);
            out.setErrorMessage(e.getMessage());
            out.setResults(List.of());
        }

        if (debug) {
            Map<String, Object> dbg = new LinkedHashMap<>();
            dbg.put("docCountInput", docs.size());
            dbg.put("docCountUsed", docsUsed.size());
            dbg.put("topN", out.getTopN());
            dbg.put("perDocMaxTokens", perDocMaxTokens);
            dbg.put("maxInputTokens", maxInputTokens);
            dbg.put("queryTokensApprox", queryTokens);
            out.setDebugInfo(dbg);
        }
        return out;
    }

    @GetMapping("/logs/events")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','access'))")
    public Page<RetrievalEventLogDTO> listEvents(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        return hybridRetrievalLogsService.listEvents(parseTimeOrNull(from), parseTimeOrNull(to), page, size);
    }

    @GetMapping("/logs/events/{eventId}/hits")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_hybrid','access'))")
    public List<RetrievalHitLogDTO> listHits(@PathVariable("eventId") long eventId) {
        return hybridRetrievalLogsService.listHits(eventId);
    }

    private static LocalDateTime parseTimeOrNull(String s) {
        return DateTimeParamSupport.parseOrNull(s);
    }

    private static String buildDocText(HybridRerankTestDocumentDTO d) {
        StringBuilder sb = new StringBuilder();
        if (d.getTitle() != null && !d.getTitle().isBlank()) sb.append(d.getTitle().trim()).append('\n');
        if (d.getText() != null) sb.append(d.getText());
        return sb.toString();
    }

    private static List<HybridRerankTestDocumentDTO> normalizeDocs(List<HybridRerankTestDocumentDTO> docsIn) {
        List<HybridRerankTestDocumentDTO> docs = new ArrayList<>();
        if (docsIn == null) return docs;
        for (int i = 0; i < docsIn.size(); i++) {
            HybridRerankTestDocumentDTO d = docsIn.get(i);
            if (d == null) continue;
            HybridRerankTestDocumentDTO dd = new HybridRerankTestDocumentDTO();
            String id = d.getDocId() == null ? null : d.getDocId().trim();
            dd.setDocId(id == null || id.isBlank() ? String.valueOf(i + 1) : id);
            dd.setTitle(d.getTitle());
            dd.setText(d.getText());
            docs.add(dd);
        }
        return docs;
    }

    private static List<HybridRerankTestHitDTO> toRerankHits(AiRerankService.RerankResult rr,
                                                            List<HybridRerankTestDocumentDTO> docsUsed) {
        List<HybridRerankTestHitDTO> hits = new ArrayList<>();
        if (rr == null || rr.results() == null) return hits;
        for (AiRerankService.RerankHit h : rr.results()) {
            HybridRerankTestHitDTO hit = toRerankHit(h, docsUsed);
            if (hit != null) hits.add(hit);
        }
        return hits;
    }

    private static HybridRerankTestHitDTO toRerankHit(AiRerankService.RerankHit h,
                                                      List<HybridRerankTestDocumentDTO> docsUsed) {
        if (h == null) return null;
        int idx = h.index();
        if (idx < 0 || idx >= docsUsed.size()) return null;
        HybridRerankTestDocumentDTO d = docsUsed.get(idx);
        if (d == null) return null;
        HybridRerankTestHitDTO hh = new HybridRerankTestHitDTO();
        hh.setIndex(idx);
        hh.setRelevanceScore(h.relevanceScore());
        hh.setDocId(d.getDocId());
        hh.setTitle(d.getTitle());
        hh.setText(d.getText());
        return hh;
    }

    private static int approxTokens(String s) {
        return ApproxTokenSupport.approxTokens(s);
    }

    private static String truncateByApproxTokens(String s, int maxTokens) {
        return ApproxTokenSupport.truncateByApproxTokens(s, maxTokens);
    }
}
