package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipTestResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.service.ai.RagContextPromptService;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContextClipTestService {

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

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
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

        RagContextPromptService.AssembleResult assembled = ragContextPromptService.assemble(query, ragHits, cfg, citationCfg);

        ContextClipTestResponse out = new ContextClipTestResponse();
        out.setQueryText(query == null ? "" : query);
        out.setBoardId(boardId);
        out.setConfig(cfg);
        out.setBudgetTokens(assembled == null ? null : assembled.getBudgetTokens());
        out.setUsedTokens(assembled == null ? 0 : safeInt(assembled.getUsedTokens()));
        out.setContextPrompt(assembled == null ? "" : safeString(assembled.getContextPrompt()));

        List<ContextClipTestResponse.Item> selected = new ArrayList<>();
        List<RagContextPromptService.Item> sel = assembled == null ? null : assembled.getSelected();
        if (sel != null) {
            for (RagContextPromptService.Item it : sel) {
                if (it == null) continue;
                ContextClipTestResponse.Item o = new ContextClipTestResponse.Item();
                o.setRank(it.getRank());
                o.setPostId(it.getPostId());
                o.setChunkIndex(it.getChunkIndex());
                o.setScore(it.getScore());
                o.setTitle(it.getTitle());
                o.setTokens(it.getTokens());
                o.setReason(it.getReason());
                selected.add(o);
            }
        }
        List<ContextClipTestResponse.Item> dropped = new ArrayList<>();
        List<RagContextPromptService.Item> dr = assembled == null ? null : assembled.getDropped();
        if (dr != null) {
            for (RagContextPromptService.Item it : dr) {
                if (it == null) continue;
                ContextClipTestResponse.Item o = new ContextClipTestResponse.Item();
                o.setRank(it.getRank());
                o.setPostId(it.getPostId());
                o.setChunkIndex(it.getChunkIndex());
                o.setScore(it.getScore());
                o.setTitle(it.getTitle());
                o.setTokens(it.getTokens());
                o.setReason(it.getReason());
                dropped.add(o);
            }
        }

        out.setSelected(selected);
        out.setDropped(dropped);
        out.setItemsSelected(selected.size());
        out.setItemsDropped(dropped.size());
        return out;
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
