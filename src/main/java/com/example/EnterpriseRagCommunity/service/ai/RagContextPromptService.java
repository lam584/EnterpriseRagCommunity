package com.example.EnterpriseRagCommunity.service.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;

import lombok.Data;

@Service
public class RagContextPromptService {

    private static final String ABLATION_NONE = "NONE";
    private static final String ABLATION_REL_ONLY = "REL_ONLY";
    private static final String ABLATION_REL_IMP = "REL_IMP";
    private static final String ABLATION_REL_IMP_RED = "REL_IMP_RED";

    private static List<CitationSource> buildSources(CitationConfigDTO cfg, List<Item> selected) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return List.of();
        int max = cfg.getMaxSources() == null ? 0 : Math.max(0, cfg.getMaxSources());
        if (max == 0) return List.of();
        List<CitationSource> out = new ArrayList<>();
        int n = Math.min(max, selected == null ? 0 : selected.size());
        for (int i = 0; i < n; i++) {
            Item it = selected.get(i);
            if (it == null) continue;
            CitationSource s = new CitationSource();
            s.setIndex(i + 1);
            s.setPostId(it.getPostId());
            s.setCommentId(it.getCommentId());
            s.setChunkIndex(it.getChunkIndex());
            s.setScore(it.getScore());
            s.setTitle(it.getTitle());
            s.setUrl(buildCitationUrl(cfg, it.getPostId(), it.getCommentId()));
            s.setSnippet(it.getSnippet());
            out.add(s);
        }
        return out;
    }

    private static class Candidate {
        Item item;
        String text;
        Integer tokens;
        Long contentHash;
        String titleKey;
        String sourceKey;
        String textKey;
        Set<String> tokenSet = Collections.emptySet();
        Double relScore;
        Double impScore;
        Double redScore;
        Double finalScore;
    }

    private static class SelectionState {
        boolean dedupByPostId;
        boolean dedupByTitle;
        boolean dedupByContentHash;
        boolean crossSourceDedup;
        int maxSamePostItems;
        final Set<Long> seenPostIds = new HashSet<>();
        final Set<String> seenTitles = new HashSet<>();
        final Set<Long> seenContentHash = new HashSet<>();
        final Map<String, String> textKeySources = new HashMap<>();
        final Map<Long, Integer> postCount = new HashMap<>();
    }

    private static List<Candidate> selectSliding(
            List<Candidate> candidates,
            SelectionState st,
            int maxItems,
            int budgetTokens,
            List<Item> dropped,
            double alpha,
            double beta,
            double gamma,
            String ablationMode
    ) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<Candidate> out = new ArrayList<>();
        int used = 0;
        for (Candidate c : candidates) {
            if (c == null || c.item == null) continue;
            if (out.size() >= maxItems) break;

            String reason = canTake(c, st);
            if (reason != null) {
                c.item.setReason(reason);
                dropped.add(c.item);
                continue;
            }

            double red = redundancyWithSelected(c, out);
            applyCandidateScore(c, alpha, beta, gamma, ablationMode, red);
            int tok = c.tokens == null ? 0 : c.tokens;
            int remaining = Math.max(0, budgetTokens - used);
            if (tok > remaining) {
                if (remaining >= 50) {
                    Candidate clipped = clipToTokens(c, remaining);
                    double clippedRed = redundancyWithSelected(clipped, out);
                    applyCandidateScore(clipped, alpha, beta, gamma, ablationMode, clippedRed);
                    markTaken(clipped, st);
                    out.add(clipped);
                    break;
                }
                c.item.setReason("budgetExceeded");
                dropped.add(c.item);
                continue;
            }

            markTaken(c, st);
            out.add(c);
            used += tok;
        }
        return out;
    }

    private static List<Candidate> selectImportance(
            List<Candidate> candidates,
            SelectionState st,
            int maxItems,
            int budgetTokens,
            List<Item> dropped,
            double alpha,
            double beta,
            double gamma,
            String ablationMode
    ) {
        return selectGreedyByScore(candidates, st, maxItems, budgetTokens, dropped, false, alpha, beta, gamma, ablationMode);
    }

    private static List<Candidate> selectHybrid(
            List<Candidate> candidates,
            SelectionState st,
            int maxItems,
            int budgetTokens,
            List<Item> dropped,
            double alpha,
            double beta,
            double gamma,
            String ablationMode
    ) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        int headN = Math.min(2, maxItems);

        List<Candidate> out = new ArrayList<>();
        int used = 0;
        for (Candidate c : candidates) {
            if (c == null || c.item == null) continue;
            if (out.size() >= headN) break;

            String reason = canTake(c, st);
            if (reason != null) {
                c.item.setReason(reason);
                dropped.add(c.item);
                continue;
            }

            double red = redundancyWithSelected(c, out);
            applyCandidateScore(c, alpha, beta, gamma, ablationMode, red);
            int tok = c.tokens == null ? 0 : c.tokens;
            if (used + tok > budgetTokens) {
                c.item.setReason("budgetExceeded");
                dropped.add(c.item);
                continue;
            }

            markTaken(c, st);
            out.add(c);
            used += tok;
        }

        int remainingBudget = Math.max(0, budgetTokens - used);
        int remainingSlots = Math.max(0, maxItems - out.size());
        if (remainingBudget == 0 || remainingSlots == 0) return out;

        List<Candidate> rest = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c == null || c.item == null) continue;
            if (out.contains(c)) continue;
            String reason = canTake(c, st);
            if (reason != null) {
                c.item.setReason(reason);
                dropped.add(c.item);
                continue;
            }
            rest.add(c);
        }
        List<Candidate> tail = selectGreedyByScore(rest, st, remainingSlots, remainingBudget, dropped, true, alpha, beta, gamma, ablationMode);
        for (Candidate c : tail) {
            if (c == null || c.item == null) continue;
            markTaken(c, st);
            out.add(c);
        }
        return out;
    }

    private static List<Candidate> selectGreedyByScore(
            List<Candidate> pool,
            SelectionState st,
            int maxItems,
            int budgetTokens,
            List<Item> dropped,
            boolean allowSlidingFill,
            double alpha,
            double beta,
            double gamma,
            String ablationMode
    ) {
        if (pool == null || pool.isEmpty() || maxItems <= 0 || budgetTokens <= 0) return List.of();
        List<Candidate> remaining = new ArrayList<>(pool);
        List<Candidate> out = new ArrayList<>();
        int used = 0;
        while (!remaining.isEmpty() && out.size() < maxItems) {
            Candidate best = null;
            int bestIdx = -1;
            for (int i = 0; i < remaining.size(); i++) {
                Candidate c = remaining.get(i);
                if (c == null || c.item == null) {
                    remaining.remove(i);
                    i--;
                    continue;
                }
                String reason = canTake(c, st);
                if (reason != null) {
                    c.item.setReason(reason);
                    dropped.add(c.item);
                    remaining.remove(i);
                    i--;
                    continue;
                }
                double red = redundancyWithSelected(c, out);
                applyCandidateScore(c, alpha, beta, gamma, ablationMode, red);
                if (isBetterGreedyCandidate(c, best, ablationMode)) {
                    best = c;
                    bestIdx = i;
                }
            }
            if (best == null) break;
            remaining.remove(bestIdx);
            int tok = best.tokens == null ? 0 : best.tokens;
            int remainingBudget = Math.max(0, budgetTokens - used);
            if (tok > remainingBudget) {
                if (allowSlidingFill && remainingBudget >= 50) {
                    Candidate clipped = clipToTokens(best, remainingBudget);
                    double red = redundancyWithSelected(clipped, out);
                    applyCandidateScore(clipped, alpha, beta, gamma, ablationMode, red);
                    markTaken(clipped, st);
                    out.add(clipped);
                    break;
                }
                best.item.setReason("budgetExceeded");
                dropped.add(best.item);
                continue;
            }
            markTaken(best, st);
            out.add(best);
            used += tok;
        }
        for (Candidate c : remaining) {
            if (c == null || c.item == null) continue;
            if (c.item.getReason() != null && !c.item.getReason().isBlank()) continue;
            c.item.setReason("notSelected");
            dropped.add(c.item);
        }
        return out;
    }

    private static Candidate clipToTokens(Candidate c, int maxTokens) {
        Candidate out = new Candidate();
        Item src = c.item;
        Item item = new Item();
        item.setRank(src.getRank());
        item.setPostId(src.getPostId());
        item.setChunkIndex(src.getChunkIndex());
        item.setScore(src.getScore());
        item.setTitle(src.getTitle());

        String text = c.text == null ? "" : c.text;
        String clipped = truncateByApproxTokens(text, maxTokens);
        int tokens = approxTokens(clipped);
        item.setTokens(tokens);
        item.setSnippet(buildSnippet(clipped));

        out.item = item;
        out.text = clipped;
        out.tokens = tokens;
        out.titleKey = c.titleKey;
        out.contentHash = c.contentHash;
        out.sourceKey = c.sourceKey;
        out.textKey = normalizeTextKey(clipped);
        out.tokenSet = tokenizeSet(clipped);
        out.relScore = c.relScore;
        out.impScore = c.impScore;
        out.redScore = c.redScore;
        out.finalScore = c.finalScore;
        return out;
    }

    private static String canTake(Candidate c, SelectionState st) {
        if (c == null || c.item == null) return "invalid";
        if (st.dedupByPostId && c.item.getPostId() != null && st.seenPostIds.contains(c.item.getPostId())) return "dedupPostId";
        if (st.dedupByTitle && c.titleKey != null && st.seenTitles.contains(c.titleKey)) return "dedupTitle";
        if (st.dedupByContentHash && c.contentHash != null && st.seenContentHash.contains(c.contentHash)) return "dedupContent";
        if (st.crossSourceDedup && c.textKey != null && c.sourceKey != null) {
            String prev = st.textKeySources.get(c.textKey);
            if (prev != null && !prev.equals(c.sourceKey)) return "crossSourceDedup";
        }
        if (st.maxSamePostItems > 0 && c.item.getPostId() != null) {
            int cnt = st.postCount.getOrDefault(c.item.getPostId(), 0);
            if (cnt >= st.maxSamePostItems) return "maxSamePostItems";
        }
        return null;
    }

    private static void markTaken(Candidate c, SelectionState st) {
        if (c == null || c.item == null) return;
        if (st.dedupByPostId && c.item.getPostId() != null) st.seenPostIds.add(c.item.getPostId());
        if (st.dedupByTitle && c.titleKey != null) st.seenTitles.add(c.titleKey);
        if (st.dedupByContentHash && c.contentHash != null) st.seenContentHash.add(c.contentHash);
        if (st.crossSourceDedup && c.textKey != null && c.sourceKey != null) {
            st.textKeySources.putIfAbsent(c.textKey, c.sourceKey);
        }
        if (st.maxSamePostItems > 0 && c.item.getPostId() != null) {
            st.postCount.put(c.item.getPostId(), st.postCount.getOrDefault(c.item.getPostId(), 0) + 1);
        }
    }

    private static boolean isBetterGreedyCandidate(Candidate a, Candidate b, String ablationMode) {
        if (a == null || a.item == null) return false;
        if (b == null || b.item == null) return true;
        if (ABLATION_NONE.equals(ablationMode)) {
            int ar = a.item.getRank() == null ? Integer.MAX_VALUE : a.item.getRank();
            int br = b.item.getRank() == null ? Integer.MAX_VALUE : b.item.getRank();
            return ar < br;
        }
        double av = a.finalScore == null ? 0.0 : a.finalScore;
        double bv = b.finalScore == null ? 0.0 : b.finalScore;
        int c = Double.compare(av, bv);
        if (c != 0) return c > 0;
        double ar = a.relScore == null ? 0.0 : a.relScore;
        double br = b.relScore == null ? 0.0 : b.relScore;
        c = Double.compare(ar, br);
        if (c != 0) return c > 0;
        int at = a.tokens == null ? Integer.MAX_VALUE : a.tokens;
        int bt = b.tokens == null ? Integer.MAX_VALUE : b.tokens;
        c = Integer.compare(bt, at);
        if (c != 0) return c > 0;
        int arank = a.item.getRank() == null ? Integer.MAX_VALUE : a.item.getRank();
        int brank = b.item.getRank() == null ? Integer.MAX_VALUE : b.item.getRank();
        return arank < brank;
    }

    public static Map<String, Object> buildChunkIds(AssembleResult r) {
        if (r == null) return Map.of("ids", List.of(), "items", List.of(), "stats", Map.of());
        List<Item> selected = r.getSelected() == null ? List.of() : r.getSelected();
        List<Long> ids = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item it : selected) {
            if (it == null) continue;
            if (it.getPostId() != null) ids.add(it.getPostId());
            items.add(toChunkIdItem(it));
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("budgetTokens", r.getBudgetTokens());
        stats.put("usedTokens", r.getUsedTokens());
        stats.put("policy", enumName(r.getPolicy()));
        return Map.of(
                "ids", ids,
                "items", items,
                "stats", stats
        );
    }

    private static String renderHeader(String tpl, int idx, Item item, boolean showPostId, boolean showChunkIndex, boolean showScore, boolean showTitle) {
        if (tpl == null || tpl.isBlank()) {
            if (!showPostId && !showChunkIndex && !showScore && !showTitle) return "";
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(idx).append("] ");
            if (showPostId && item.getPostId() != null) sb.append("post_id=").append(item.getPostId()).append(' ');
            if (showChunkIndex && item.getChunkIndex() != null) sb.append("chunk=").append(item.getChunkIndex()).append(' ');
            if (showScore && item.getScore() != null) sb.append("score=").append(String.format(Locale.ROOT, "%.4f", item.getScore())).append(' ');
            sb.append('\n');
            if (showTitle && item.getTitle() != null && !item.getTitle().isBlank()) sb.append("标题：").append(item.getTitle().trim()).append('\n');
            return sb.toString();
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("{i}", String.valueOf(idx));
        vars.put("{postId}", showPostId ? Objects.toString(item.getPostId(), "") : "");
        vars.put("{chunkIndex}", showChunkIndex ? Objects.toString(item.getChunkIndex(), "") : "");
        vars.put("{score}", showScore && item.getScore() != null ? String.format(Locale.ROOT, "%.4f", item.getScore()) : "");
        vars.put("{title}", showTitle ? Objects.toString(item.getTitle(), "") : "");
        String out = tpl;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static Map<String, Object> toChunkIdItem(Item it) {
        Map<String, Object> m = new HashMap<>();
        m.put("rank", it.getRank());
        m.put("postId", it.getPostId());
        m.put("chunkIndex", it.getChunkIndex());
        m.put("score", it.getScore());
        m.put("relScore", it.getRelScore());
        m.put("impScore", it.getImpScore());
        m.put("redScore", it.getRedScore());
        m.put("finalScore", it.getFinalScore());
        m.put("source", it.getSource());
        m.put("title", it.getTitle());
        m.put("tokens", it.getTokens());
        m.put("reason", it.getReason());
        return m;
    }

    private static String appendExactCitationRule(String citationInstruction) {
        String base = citationInstruction == null ? "" : citationInstruction.trim();
        if (base.isBlank()) return base;
        String extra = "引用来源时请尽量使用短引文并保持与来源原文逐字一致；每条引文必须使用成对中文引号“”包裹，不要混用半角双引号或转义反斜杠，再在引文后紧跟对应编号（例如：“原文片段”[1]）。";
        if (base.contains("逐字一致") && base.contains("中文引号“”")) return base;
        return base + "\n" + extra;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    static String renderSourcesText(CitationConfigDTO cfg, List<CitationSource> sources) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return "";
        String mode = cfg.getCitationMode() == null ? "" : cfg.getCitationMode().trim().toUpperCase();
        if (!mode.equals("SOURCES_SECTION") && !mode.equals("BOTH")) return "";
        if (sources == null || sources.isEmpty()) return "";
        if (cfg.getSourcesTitle() == null || cfg.getSourcesTitle().isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.getSourcesTitle().trim()).append("：\n");
        for (CitationSource s : sources) {
            if (s == null) continue;
            sb.append('[').append(s.getIndex() == null ? "" : s.getIndex()).append("] ");
            if (Boolean.TRUE.equals(cfg.getIncludeTitle()) && s.getTitle() != null && !s.getTitle().isBlank()) {
                sb.append(s.getTitle().trim()).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludeUrl()) && s.getUrl() != null && !s.getUrl().isBlank()) {
                sb.append(s.getUrl().trim()).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludeScore()) && s.getScore() != null) {
                sb.append("score=").append(String.format(Locale.ROOT, "%.4f", s.getScore())).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludePostId()) && s.getPostId() != null) {
                sb.append("post_id=").append(s.getPostId()).append(' ');
            }
            if (s.getCommentId() != null) {
                sb.append("comment_id=").append(s.getCommentId()).append(' ');
            }
            if (Boolean.TRUE.equals(cfg.getIncludeChunkIndex()) && s.getChunkIndex() != null) {
                sb.append("chunk=").append(s.getChunkIndex()).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String buildCitationUrl(CitationConfigDTO cfg, Long postId, Long commentId) {
        String base = buildPostUrl(cfg, postId);
        if (base == null || base.isBlank() || commentId == null) return base;
        String glue = base.contains("?") ? "&" : "?";
        return base + glue + "commentId=" + commentId + "#comment-" + commentId;
    }

    public AssembleResult assemble(String queryText, List<RagPostChatRetrievalService.Hit> hits, ContextClipConfigDTO cfg, CitationConfigDTO citationCfg) {
        ContextClipConfigDTO safe = cfg;
        if (safe == null) safe = new ContextClipConfigDTO();
        boolean enabled = safe.getEnabled() == null || safe.getEnabled();
        if (!enabled) {
            AssembleResult out = new AssembleResult();
            out.setContextPrompt("");
            out.setBudgetTokens(0);
            out.setUsedTokens(0);
            out.setSelected(List.of());
            out.setDropped(List.of());
            out.setSources(List.of());
            out.setSourcesText("");
            out.setChunkIds(buildChunkIds(out));
            out.setPolicy(safe.getPolicy() == null ? ContextWindowPolicy.TOPK : safe.getPolicy());
            return out;
        }

        ContextWindowPolicy policy = safe.getPolicy() == null ? ContextWindowPolicy.TOPK : safe.getPolicy();
        int maxItems = safe.getMaxItems() == null ? 6 : clampInt(safe.getMaxItems(), 1, 100, 6);
        int maxContextTokens = safe.getMaxContextTokens() == null ? 12000 : clampInt(safe.getMaxContextTokens(), 1, 1_000_000, 12000);
        int contextTokenBudget = safe.getContextTokenBudget() == null ? maxContextTokens : clampInt(safe.getContextTokenBudget(), 100, 1_000_000, 3000);
        int reserve = safe.getReserveAnswerTokens() == null ? 2000 : clampInt(safe.getReserveAnswerTokens(), 0, 1_000_000, 2000);
        int perItemMaxTokens = safe.getPerItemMaxTokens() == null ? 2000 : clampInt(safe.getPerItemMaxTokens(), 50, 200_000, 2000);
        int maxPromptChars = safe.getMaxPromptChars() == null ? 200_000 : clampInt(safe.getMaxPromptChars(), 1000, 2_000_000, 200_000);

        int budgetTokens = switch (policy) {
            case FIXED -> contextTokenBudget;
            case ADAPTIVE -> Math.max(0, contextTokenBudget - reserve - Math.max(0, approxTokens(queryText)) * 2);
            default -> Math.max(0, contextTokenBudget - reserve);
        };

        Double minScore = safe.getMinScore();
        int maxSamePostItems = safe.getMaxSamePostItems() == null ? 0 : Math.max(0, safe.getMaxSamePostItems());
        boolean requireTitle = Boolean.TRUE.equals(safe.getRequireTitle());
        boolean dedupByPostId = Boolean.TRUE.equals(safe.getDedupByPostId());
        boolean dedupByTitle = Boolean.TRUE.equals(safe.getDedupByTitle());
        boolean dedupByContentHash = Boolean.TRUE.equals(safe.getDedupByContentHash());
        boolean crossSourceDedup = safe.getCrossSourceDedup() == null || safe.getCrossSourceDedup();
        double alpha = clampDouble(safe.getAlpha());
        double beta = clampDouble(safe.getBeta());
        double gamma = clampDouble(safe.getGamma());
        String ablationMode = normalizeAblationMode(safe.getAblationMode());

        String sectionTitle = trimOrDefault(safe.getSectionTitle());
        String separator = safe.getSeparator() == null ? "\n\n" : safe.getSeparator();
        String headerTpl = trimOrDefault(safe.getItemHeaderTemplate());

        boolean showPostId = safe.getShowPostId() == null || safe.getShowPostId();
        boolean showChunkIndex = safe.getShowChunkIndex() == null || safe.getShowChunkIndex();
        boolean showScore = safe.getShowScore() == null || safe.getShowScore();
        boolean showTitle = safe.getShowTitle() == null || safe.getShowTitle();

        List<Item> selected = new ArrayList<>();
        List<Item> dropped = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        if (!sectionTitle.isBlank()) {
            sb.append(sectionTitle.trim()).append("\n\n");
        }

        int usedTokens;
        int idxOut = 0;

        if (policy == ContextWindowPolicy.SLIDING || policy == ContextWindowPolicy.IMPORTANCE || policy == ContextWindowPolicy.HYBRID) {
            int scanLimit = Math.min(
                    hits == null ? 0 : hits.size(),
                    Math.clamp(maxItems * 8L, 50, 500)
            );

            List<Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < scanLimit; i++) {
                RagPostChatRetrievalService.Hit h = hits.get(i);
                if (h == null) continue;

                Item item = new Item();
                item.setRank(i + 1);
                item.setPostId(h.getPostId());
                item.setCommentId(h.getCommentId());
                item.setChunkIndex(h.getChunkIndex());
                item.setScore(h.getScore());
                item.setTitle(trimOrNull(h.getTitle()));

                Double score = item.getScore();
                if (score == null) score = 0.0;
                if (minScore != null && score < minScore) {
                    item.setReason("minScore");
                    dropped.add(item);
                    continue;
                }

                if (requireTitle && (item.getTitle() == null || item.getTitle().isBlank())) {
                    item.setReason("requireTitle");
                    dropped.add(item);
                    continue;
                }

                String text = h.getContentText();
                if (text == null || text.isBlank()) {
                    item.setReason("emptyContent");
                    dropped.add(item);
                    continue;
                }

                String trimmed = text.trim();
                String truncated = truncateByApproxTokens(trimmed, perItemMaxTokens);
                int tokens = approxTokens(truncated);
                item.setTokens(tokens);
                item.setSnippet(buildSnippet(truncated));
                if (tokens <= 0) {
                    item.setReason("noTokens");
                    dropped.add(item);
                    continue;
                }

                Candidate c = new Candidate();
                c.item = item;
                c.text = truncated;
                c.tokens = tokens;
                c.sourceKey = resolveSourceKey(h);
                if (dedupByTitle && item.getTitle() != null) {
                    String key = normalizeTitle(item.getTitle());
                    c.titleKey = key.isBlank() ? null : key;
                }
                if (dedupByContentHash) {
                    c.contentHash = crc32(truncated);
                }
                c.textKey = normalizeTextKey(truncated);
                c.tokenSet = tokenizeSet(truncated);
                candidates.add(c);
            }

            prepareCandidateScores(candidates);

            SelectionState st = new SelectionState();
            st.dedupByPostId = dedupByPostId;
            st.dedupByTitle = dedupByTitle;
            st.dedupByContentHash = dedupByContentHash;
            st.crossSourceDedup = crossSourceDedup;
            st.maxSamePostItems = maxSamePostItems;

            List<Candidate> selectedCandidates;
            if (policy == ContextWindowPolicy.SLIDING) {
                selectedCandidates = selectSliding(candidates, st, maxItems, budgetTokens, dropped, alpha, beta, gamma, ablationMode);
            } else if (policy == ContextWindowPolicy.IMPORTANCE) {
                selectedCandidates = selectImportance(candidates, st, maxItems, budgetTokens, dropped, alpha, beta, gamma, ablationMode);
            } else {
                selectedCandidates = selectHybrid(candidates, st, maxItems, budgetTokens, dropped, alpha, beta, gamma, ablationMode);
            }

            usedTokens = 0;
            for (Candidate c : selectedCandidates) {
                if (c == null || c.item == null) continue;
                idxOut++;
                String header = renderHeader(headerTpl, idxOut, c.item, showPostId, showChunkIndex, showScore, showTitle);
                if (!header.isBlank()) sb.append(header);
                sb.append(c.text == null ? "" : c.text);
                sb.append(separator);
                usedTokens += Math.max(0, c.tokens);
                c.item.setReason("selected");
                selected.add(c.item);
                if (sb.length() > maxPromptChars) break;
            }
        } else {
            Set<Long> seenPostIds = new HashSet<>();
            Set<String> seenTitles = new HashSet<>();
            Set<Long> seenContentHash = new HashSet<>();
            Map<String, String> textKeySource = new HashMap<>();
            Map<Long, Integer> postCount = new HashMap<>();

            usedTokens = 0;
            int n = Math.min(maxItems, hits == null ? 0 : hits.size());
            for (int i = 0; i < n; i++) {
                RagPostChatRetrievalService.Hit h = hits.get(i);
                if (h == null) continue;

                Item item = new Item();
                item.setRank(i + 1);
                item.setPostId(h.getPostId());
                item.setCommentId(h.getCommentId());
                item.setChunkIndex(h.getChunkIndex());
                item.setScore(h.getScore());
                item.setTitle(trimOrNull(h.getTitle()));

                Double score = item.getScore();
                if (score == null) score = 0.0;
                if (minScore != null && score < minScore) {
                    item.setReason("minScore");
                    dropped.add(item);
                    continue;
                }

                if (requireTitle && (item.getTitle() == null || item.getTitle().isBlank())) {
                    item.setReason("requireTitle");
                    dropped.add(item);
                    continue;
                }

                if (dedupByPostId && item.getPostId() != null) {
                    if (seenPostIds.contains(item.getPostId())) {
                        item.setReason("dedupPostId");
                        dropped.add(item);
                        continue;
                    }
                }

                if (dedupByTitle && item.getTitle() != null) {
                    String key = normalizeTitle(item.getTitle());
                    if (!key.isBlank() && seenTitles.contains(key)) {
                        item.setReason("dedupTitle");
                        dropped.add(item);
                        continue;
                    }
                }

                String text = h.getContentText();
                if (text == null || text.isBlank()) {
                    item.setReason("emptyContent");
                    dropped.add(item);
                    continue;
                }

                String trimmed = text.trim();
                String truncated = truncateByApproxTokens(trimmed, perItemMaxTokens);
                int tokens = approxTokens(truncated);
                String textKey = normalizeTextKey(truncated);
                item.setTokens(tokens);
                item.setSnippet(buildSnippet(truncated));
                if (tokens <= 0) {
                    item.setReason("noTokens");
                    dropped.add(item);
                    continue;
                }

                if (dedupByContentHash) {
                    long h32 = crc32(truncated);
                    if (seenContentHash.contains(h32)) {
                        item.setReason("dedupContent");
                        dropped.add(item);
                        continue;
                    }
                    seenContentHash.add(h32);
                }

                if (crossSourceDedup && textKey != null) {
                    String sourceKey = resolveSourceKey(h);
                    String prevSource = textKeySource.get(textKey);
                    if (prevSource != null && sourceKey != null && !sourceKey.equals(prevSource)) {
                        item.setReason("crossSourceDedup");
                        dropped.add(item);
                        continue;
                    }
                }

                if (maxSamePostItems > 0 && item.getPostId() != null) {
                    int c = postCount.getOrDefault(item.getPostId(), 0);
                    if (c >= maxSamePostItems) {
                        item.setReason("maxSamePostItems");
                        dropped.add(item);
                        continue;
                    }
                }

                if (usedTokens + tokens > budgetTokens) {
                    item.setReason("budgetExceeded");
                    dropped.add(item);
                    continue;
                }

                idxOut++;
                String header = renderHeader(headerTpl, idxOut, item, showPostId, showChunkIndex, showScore, showTitle);
                if (!header.isBlank()) sb.append(header);
                sb.append(truncated);
                sb.append(separator);

                usedTokens += tokens;
                item.setReason("selected");
                selected.add(item);

                if (dedupByPostId && item.getPostId() != null) seenPostIds.add(item.getPostId());
                if (dedupByTitle && item.getTitle() != null) {
                    String key = normalizeTitle(item.getTitle());
                    if (!key.isBlank()) seenTitles.add(key);
                }
                if (maxSamePostItems > 0 && item.getPostId() != null) {
                    postCount.put(item.getPostId(), postCount.getOrDefault(item.getPostId(), 0) + 1);
                }
                if (crossSourceDedup && textKey != null) {
                    String sourceKey = resolveSourceKey(h);
                    textKeySource.putIfAbsent(textKey, sourceKey == null ? "UNKNOWN" : sourceKey);
                }
                if (sb.length() > maxPromptChars) break;
            }
        }

        String prompt = sb.toString().trim();

        String extraInstruction = trimOrNull(safe.getExtraInstruction());
        if (extraInstruction != null && !extraInstruction.isBlank()) {
            prompt = (prompt.isBlank() ? "" : prompt + "\n\n") + extraInstruction.trim();
        }

        String citationInstruction = null;
        if (citationCfg != null && Boolean.TRUE.equals(citationCfg.getEnabled())) {
            String mode = citationCfg.getCitationMode() == null ? "" : citationCfg.getCitationMode().trim().toUpperCase();
            if (mode.equals("MODEL_INLINE") || mode.equals("BOTH")) {
                citationInstruction = trimOrNull(citationCfg.getInstructionTemplate());
            }
        }
        if (citationInstruction != null && !citationInstruction.isBlank()) {
            prompt = (prompt.isBlank() ? "" : prompt + "\n\n") + appendExactCitationRule(citationInstruction);
        }

        List<CitationSource> sources = buildSources(citationCfg, selected);
        String sourcesText = renderSourcesText(citationCfg, sources);

        AssembleResult out = new AssembleResult();
        out.setPolicy(policy);
        out.setBudgetTokens(budgetTokens);
        out.setUsedTokens(usedTokens);
        out.setContextPrompt(prompt);
        out.setSelected(selected);
        out.setDropped(dropped);
        out.setSources(sources);
        out.setSourcesText(sourcesText);
        out.setChunkIds(buildChunkIds(out));
        return out;
    }

    private static String buildPostUrl(CitationConfigDTO cfg, Long postId) {
        if (cfg == null) return null;
        String tpl = cfg.getPostUrlTemplate();
        if (tpl == null || tpl.isBlank()) return null;
        String id = postId == null ? "" : String.valueOf(postId);
        return tpl.replace("{postId}", id);
    }

    private static long crc32(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    private static String normalizeTitle(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    private static void prepareCandidateScores(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return;
        double maxRel = 0.0;
        double maxImp = 0.0;
        for (Candidate c : candidates) {
            if (c == null || c.item == null) continue;
            double rel = Math.max(0.0, c.item.getScore() == null ? 0.0 : c.item.getScore());
            int tok = Math.max(1, c.tokens == null ? 0 : c.tokens);
            double imp = 1.0 / Math.sqrt(tok);
            maxRel = Math.max(maxRel, rel);
            maxImp = Math.max(maxImp, imp);
        }
        if (maxRel <= 0) maxRel = 1.0;
        if (maxImp <= 0) maxImp = 1.0;
        for (Candidate c : candidates) {
            if (c == null || c.item == null) continue;
            double rel = Math.max(0.0, c.item.getScore() == null ? 0.0 : c.item.getScore()) / maxRel;
            int tok = Math.max(1, c.tokens == null ? 0 : c.tokens);
            double imp = (1.0 / Math.sqrt(tok)) / maxImp;
            c.relScore = rel;
            c.impScore = imp;
            c.redScore = 0.0;
            c.finalScore = rel;
            syncCandidateScoreToItem(c);
        }
    }

    private static void applyCandidateScore(Candidate c, double alpha, double beta, double gamma, String ablationMode, double red) {
        if (c == null || c.item == null) return;
        double rel = c.relScore == null ? 0.0 : c.relScore;
        double imp = c.impScore == null ? 0.0 : c.impScore;
        double redClamped = Math.max(0.0, Math.min(1.0, red));
        double finalScore = switch (ablationMode) {
            case ABLATION_NONE -> rel;
            case ABLATION_REL_ONLY -> alpha * rel;
            case ABLATION_REL_IMP -> alpha * rel + beta * imp;
            default -> alpha * rel + beta * imp - gamma * redClamped;
        };
        c.redScore = redClamped;
        c.finalScore = finalScore;
        syncCandidateScoreToItem(c);
    }

    private static void syncCandidateScoreToItem(Candidate c) {
        if (c == null || c.item == null) return;
        c.item.setRelScore(c.relScore);
        c.item.setImpScore(c.impScore);
        c.item.setRedScore(c.redScore);
        c.item.setFinalScore(c.finalScore);
        c.item.setSource(c.sourceKey);
    }

    private static double redundancyWithSelected(Candidate c, List<Candidate> selected) {
        if (c == null || selected == null || selected.isEmpty()) return 0.0;
        double best = 0.0;
        for (Candidate s : selected) {
            if (s == null || s.item == null) continue;
            double sim = similarity(c, s);
            if (sim > best) best = sim;
        }
        return best;
    }

    private static double similarity(Candidate a, Candidate b) {
        if (a == null || b == null) return 0.0;
        double score = 0.0;
        if (a.contentHash != null && b.contentHash != null && a.contentHash.equals(b.contentHash)) score = 1.0;
        if (a.textKey != null && b.textKey != null && a.textKey.equals(b.textKey)) score = 1.0;
        if (a.titleKey != null && b.titleKey != null && a.titleKey.equals(b.titleKey)) score = Math.max(score, 0.85);
        if (a.item != null && b.item != null && a.item.getPostId() != null && a.item.getPostId().equals(b.item.getPostId())) score = Math.max(score, 0.75);
        if (a.tokenSet != null && !a.tokenSet.isEmpty() && b.tokenSet != null && !b.tokenSet.isEmpty()) {
            int inter = 0;
            for (String tok : a.tokenSet) {
                if (b.tokenSet.contains(tok)) inter++;
            }
            int union = a.tokenSet.size() + b.tokenSet.size() - inter;
            if (union > 0) {
                double jac = (double) inter / (double) union;
                score = Math.max(score, jac);
            }
        }
        return Math.clamp(score, 0.0, 1.0);
    }

    private static Set<String> tokenizeSet(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (normalized.isBlank()) return Collections.emptySet();
        String[] arr = normalized.split("\\s+");
        Set<String> out = new HashSet<>();
        for (String s : arr) {
            if (s == null || s.isBlank()) continue;
            out.add(s);
            if (out.size() >= 80) break;
        }
        return out;
    }

    private static String normalizeTextKey(String text) {
        if (text == null) return null;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) return null;
        int cap = Math.min(normalized.length(), 600);
        return normalized.substring(0, cap);
    }

    private static String resolveSourceKey(RagPostChatRetrievalService.Hit h) {
        if (h == null) return null;
        if (h.getType() != null) return h.getType().name();
        String docId = trimOrNull(h.getDocId());
        if (docId == null) return "UNKNOWN";
        int p = docId.indexOf(':');
        if (p > 0) return docId.substring(0, p).toUpperCase(Locale.ROOT);
        return "DOC";
    }

    private static String trimOrDefault(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.isBlank() ? "" : t;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String buildSnippet(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.isBlank()) return null;
        int maxChars = 320;
        if (t.length() <= maxChars) return t;
        return t.substring(0, maxChars) + "…";
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
    }

    private static double clampDouble(Double v) {
        double x = v == null ? 1.0 : v;
        if (Double.isNaN(x) || Double.isInfinite(x)) x = 1.0;
        if (x < 0.0) x = 0.0;
        if (x > 10.0) x = 10.0;
        return x;
    }

    private static String normalizeAblationMode(String mode) {
        String x = trimOrNull(mode);
        if (x == null) return ABLATION_REL_IMP_RED;
        String key = x.toUpperCase(Locale.ROOT).replace('-', '_').replace('+', '_');
        return switch (key) {
            case "NONE", "NO_PRUNING" -> ABLATION_NONE;
            case "REL", "REL_ONLY" -> ABLATION_REL_ONLY;
            case "REL_IMP", "RELATIVE_IMPORTANCE" -> ABLATION_REL_IMP;
            default -> ABLATION_REL_IMP_RED;
        };
    }

    static int approxTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        double t = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x7f) t += 0.25;
            else t += 1.0;
        }
        return Math.max(0, (int) Math.ceil(t));
    }

    static String truncateByApproxTokens(String s, int maxTokens) {
        if (s == null) return "";
        int cap = Math.max(0, maxTokens);
        if (cap == 0) return "";
        if (approxTokens(s) <= cap) return s;
        int lo = 0;
        int hi = s.length();
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            String sub = s.substring(0, mid);
            int tok = approxTokens(sub);
            if (tok <= cap) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, best);
    }

    @Data
    public static class AssembleResult {
        private ContextWindowPolicy policy;
        private Integer budgetTokens;
        private Integer usedTokens;
        private String contextPrompt;
        private List<Item> selected;
        private List<Item> dropped;
        private List<CitationSource> sources;
        private String sourcesText;
        private Map<String, Object> chunkIds;
    }

    @Data
    public static class Item {
        private Integer rank;
        private Long postId;
        private Long commentId;
        private Integer chunkIndex;
        private Double score;
        private Double relScore;
        private Double impScore;
        private Double redScore;
        private Double finalScore;
        private String source;
        private String title;
        private String snippet;
        private Integer tokens;
        private String reason;
    }

    @Data
    public static class CitationSource {
        private Integer index;
        private Long postId;
        private Long commentId;
        private Integer chunkIndex;
        private Double score;
        private String title;
        private String url;
        private String snippet;
    }
}
