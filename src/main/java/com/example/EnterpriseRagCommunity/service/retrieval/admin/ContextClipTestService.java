package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContextClipTestService {
    private static final List<ContextWindowPolicy> DEFAULT_COMPARISON_MODES = List.of(
            ContextWindowPolicy.TOPK,
            ContextWindowPolicy.SLIDING,
            ContextWindowPolicy.IMPORTANCE,
            ContextWindowPolicy.HYBRID
    );

    private final ContextClipConfigService contextClipConfigService;
    private final CitationConfigService citationConfigService;
    private final HybridRetrievalConfigService hybridRetrievalConfigService;
    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final RagPostChatRetrievalService ragRetrievalService;
    private final RagContextPromptService ragContextPromptService;

    @Transactional(readOnly = true)
    public ContextClipTestResponse test(ContextClipTestRequest req) {
        String query = req == null ? null : req.getQueryText();
        Long boardId = req == null ? null : req.getBoardId();

        ContextClipConfigDTO cfg;
        if (req == null || Boolean.TRUE.equals(req.getUseSavedConfig()) || req.getConfig() == null) {
            cfg = contextClipConfigService.getConfigOrDefault();
        } else {
            cfg = contextClipConfigService.normalizeConfig(req.getConfig());
        }

        CitationConfigDTO citationCfg = citationConfigService.getConfigOrDefault();

        List<RagPostChatRetrievalService.Hit> ragHits;
        try {
            HybridRetrievalConfigDTO hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
            if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
                HybridRagRetrievalService.RetrieveResult rr = hybridRagRetrievalService.retrieve(query, boardId, hybridCfg, false);
                ragHits = toRagHits(rr == null ? null : rr.getFinalHits());
            } else {
                int k = cfg == null || cfg.getMaxItems() == null ? 6 : Math.max(1, cfg.getMaxItems());
                ragHits = ragRetrievalService.retrieve(query, Math.min(50, k), boardId);
            }
        } catch (Exception ignored) {
            ragHits = List.of();
        }

        ContextWindowPolicy primaryPolicy = cfg != null && cfg.getPolicy() != null ? cfg.getPolicy() : ContextWindowPolicy.TOPK;
        ContextClipConfigDTO primaryConfig = cloneConfig(cfg);
        primaryConfig.setPolicy(primaryPolicy);
        RagContextPromptService.AssembleResult assembled = ragContextPromptService.assemble(query, ragHits, primaryConfig, citationCfg);

        ContextClipTestResponse out = new ContextClipTestResponse();
        out.setQueryText(query == null ? "" : query);
        out.setBoardId(boardId);
        out.setConfig(primaryConfig);
        fillPrimaryResult(out, assembled);
        out.setComparisons(buildComparisons(req, query, ragHits, primaryConfig, assembled, citationCfg));
        return out;
    }

    private List<ContextClipTestResponse.Comparison> buildComparisons(
            ContextClipTestRequest req,
            String query,
            List<RagPostChatRetrievalService.Hit> ragHits,
            ContextClipConfigDTO primaryConfig,
            RagContextPromptService.AssembleResult primaryAssembled,
            CitationConfigDTO citationCfg
    ) {
        List<ContextWindowPolicy> modes = resolveModes(req);
        List<ContextClipTestResponse.Comparison> out = new ArrayList<>();
        int baseUsedTokens = primaryAssembled == null ? 0 : safeInt(primaryAssembled.getUsedTokens());
        int baseBudgetTokens = primaryAssembled == null ? 0 : safeInt(primaryAssembled.getBudgetTokens());
        for (ContextWindowPolicy mode : modes) {
            if (mode == null) continue;
            ContextClipConfigDTO modeCfg = cloneConfig(primaryConfig);
            modeCfg.setPolicy(mode);
            RagContextPromptService.AssembleResult assembled = ragContextPromptService.assemble(query, ragHits, modeCfg, citationCfg);

            ContextClipTestResponse.Comparison c = new ContextClipTestResponse.Comparison();
            c.setMode(mode.name());
            c.setConfig(modeCfg);
            c.setBudgetTokens(assembled == null ? null : assembled.getBudgetTokens());
            c.setUsedTokens(assembled == null ? 0 : safeInt(assembled.getUsedTokens()));
            c.setUsedTokensDiff((assembled == null ? 0 : safeInt(assembled.getUsedTokens())) - baseUsedTokens);
            c.setBudgetTokensDiff((assembled == null ? 0 : safeInt(assembled.getBudgetTokens())) - baseBudgetTokens);
            c.setContextPrompt(assembled == null ? "" : safeString(assembled.getContextPrompt()));
            List<ContextClipTestResponse.Item> selected = mapItems(assembled == null ? null : assembled.getSelected());
            List<ContextClipTestResponse.Item> dropped = mapItems(assembled == null ? null : assembled.getDropped());
            c.setSelected(selected);
            c.setDropped(dropped);
            c.setItemsSelected(selected.size());
            c.setItemsDropped(dropped.size());
            out.add(c);
        }
        return out;
    }

    private void fillPrimaryResult(ContextClipTestResponse out, RagContextPromptService.AssembleResult assembled) {
        out.setBudgetTokens(assembled == null ? null : assembled.getBudgetTokens());
        out.setUsedTokens(assembled == null ? 0 : safeInt(assembled.getUsedTokens()));
        out.setContextPrompt(assembled == null ? "" : safeString(assembled.getContextPrompt()));

        List<ContextClipTestResponse.Item> selected = mapItems(assembled == null ? null : assembled.getSelected());
        List<ContextClipTestResponse.Item> dropped = mapItems(assembled == null ? null : assembled.getDropped());
        out.setSelected(selected);
        out.setDropped(dropped);
        out.setItemsSelected(selected.size());
        out.setItemsDropped(dropped.size());
    }

    private static List<ContextClipTestResponse.Item> mapItems(List<RagContextPromptService.Item> items) {
        List<ContextClipTestResponse.Item> out = new ArrayList<>();
        if (items == null) return out;
        for (RagContextPromptService.Item it : items) {
            if (it == null) continue;
            ContextClipTestResponse.Item o = new ContextClipTestResponse.Item();
            o.setRank(it.getRank());
            o.setPostId(it.getPostId());
            o.setChunkIndex(it.getChunkIndex());
            o.setScore(it.getScore());
            o.setRelScore(it.getRelScore());
            o.setImpScore(it.getImpScore());
            o.setRedScore(it.getRedScore());
            o.setFinalScore(it.getFinalScore());
            o.setSource(it.getSource());
            o.setTitle(it.getTitle());
            o.setTokens(it.getTokens());
            o.setReason(it.getReason());
            out.add(o);
        }
        return out;
    }

    private static List<ContextWindowPolicy> resolveModes(ContextClipTestRequest req) {
        if (req == null || req.getModes() == null || req.getModes().isEmpty()) {
            return DEFAULT_COMPARISON_MODES;
        }
        Set<ContextWindowPolicy> normalized = new LinkedHashSet<>();
        for (String raw : req.getModes()) {
            ContextWindowPolicy policy = normalizePolicy(raw);
            if (policy != null) normalized.add(policy);
        }
        if (normalized.isEmpty()) return DEFAULT_COMPARISON_MODES;
        return new ArrayList<>(normalized);
    }

    private static ContextWindowPolicy normalizePolicy(String raw) {
        if (raw == null) return null;
        String key = raw.trim();
        if (key.isBlank()) return null;
        try {
            return ContextWindowPolicy.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ContextClipConfigDTO cloneConfig(ContextClipConfigDTO source) {
        ContextClipConfigDTO target = new ContextClipConfigDTO();
        if (source != null) BeanUtils.copyProperties(source, target);
        return target;
    }

    private static List<RagPostChatRetrievalService.Hit> toRagHits(List<HybridRagRetrievalService.DocHit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        List<RagPostChatRetrievalService.Hit> out = new ArrayList<>();
        for (HybridRagRetrievalService.DocHit h : hits) {
            if (h == null) continue;
            RagPostChatRetrievalService.Hit rr = new RagPostChatRetrievalService.Hit();
            rr.setDocId(h.getDocId());
            Double s = h.getRerankScore();
            if (s == null) s = h.getFusedScore();
            if (s == null) s = h.getScore();
            rr.setScore(s);
            rr.setPostId(h.getPostId());
            rr.setChunkIndex(h.getChunkIndex());
            rr.setBoardId(h.getBoardId());
            rr.setTitle(h.getTitle());
            rr.setContentText(h.getContentText());
            out.add(rr);
        }
        return out;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }
}
