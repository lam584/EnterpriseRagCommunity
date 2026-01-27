package com.example.EnterpriseRagCommunity.service.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    public AssembleResult assemble(String queryText, List<RagPostChatRetrievalService.Hit> hits, ContextClipConfigDTO cfg, CitationConfigDTO citationCfg) {
        ContextClipConfigDTO safe = cfg;
        if (safe == null) safe = new ContextClipConfigDTO();
        boolean enabled = safe.getEnabled() == null || Boolean.TRUE.equals(safe.getEnabled());
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
        int maxContextTokens = safe.getMaxContextTokens() == null ? 12000 : clampInt(safe.getMaxContextTokens(), 100, 1_000_000, 12000);
        int reserve = safe.getReserveAnswerTokens() == null ? 2000 : clampInt(safe.getReserveAnswerTokens(), 0, 1_000_000, 2000);
        int perItemMaxTokens = safe.getPerItemMaxTokens() == null ? 2000 : clampInt(safe.getPerItemMaxTokens(), 50, 200_000, 2000);
        int maxPromptChars = safe.getMaxPromptChars() == null ? 200_000 : clampInt(safe.getMaxPromptChars(), 1000, 2_000_000, 200_000);

        int budgetTokens = switch (policy) {
            case FIXED -> maxContextTokens;
            case ADAPTIVE -> Math.max(0, maxContextTokens - reserve - Math.max(0, approxTokens(queryText)) * 2);
            default -> Math.max(0, maxContextTokens - reserve);
        };

        Double minScore = safe.getMinScore();
        int maxSamePostItems = safe.getMaxSamePostItems() == null ? 0 : Math.max(0, safe.getMaxSamePostItems());
        boolean requireTitle = Boolean.TRUE.equals(safe.getRequireTitle());
        boolean dedupByPostId = Boolean.TRUE.equals(safe.getDedupByPostId());
        boolean dedupByTitle = Boolean.TRUE.equals(safe.getDedupByTitle());
        boolean dedupByContentHash = Boolean.TRUE.equals(safe.getDedupByContentHash());

        String sectionTitle = trimOrDefault(safe.getSectionTitle(), "");
        String separator = safe.getSeparator() == null ? "\n\n" : safe.getSeparator();
        String headerTpl = trimOrDefault(safe.getItemHeaderTemplate(), "");

        boolean showPostId = safe.getShowPostId() == null || Boolean.TRUE.equals(safe.getShowPostId());
        boolean showChunkIndex = safe.getShowChunkIndex() == null || Boolean.TRUE.equals(safe.getShowChunkIndex());
        boolean showScore = safe.getShowScore() == null || Boolean.TRUE.equals(safe.getShowScore());
        boolean showTitle = safe.getShowTitle() == null || Boolean.TRUE.equals(safe.getShowTitle());

        List<Item> selected = new ArrayList<>();
        List<Item> dropped = new ArrayList<>();

        Set<Long> seenPostIds = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        Set<Long> seenContentHash = new HashSet<>();
        Map<Long, Integer> postCount = new HashMap<>();

        StringBuilder sb = new StringBuilder();
        if (!sectionTitle.isBlank()) {
            sb.append(sectionTitle.trim()).append("\n\n");
        }

        int usedTokens = 0;
        int n = Math.min(maxItems, hits == null ? 0 : hits.size());
        int idxOut = 0;

        for (int i = 0; i < n; i++) {
            RagPostChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;

            Item item = new Item();
            item.setRank(i + 1);
            item.setPostId(h.getPostId());
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
            item.setTokens(tokens);
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
            selected.add(item);

            if (dedupByPostId && item.getPostId() != null) seenPostIds.add(item.getPostId());
            if (dedupByTitle && item.getTitle() != null) {
                String key = normalizeTitle(item.getTitle());
                if (!key.isBlank()) seenTitles.add(key);
            }
            if (maxSamePostItems > 0 && item.getPostId() != null) {
                postCount.put(item.getPostId(), postCount.getOrDefault(item.getPostId(), 0) + 1);
            }

            if (sb.length() > maxPromptChars) break;
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
            prompt = (prompt.isBlank() ? "" : prompt + "\n\n") + citationInstruction.trim();
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

    public static Map<String, Object> buildChunkIds(AssembleResult r) {
        if (r == null) return Map.of("ids", List.of(), "items", List.of(), "stats", Map.of());
        List<Item> selected = r.getSelected() == null ? List.of() : r.getSelected();
        List<Long> ids = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item it : selected) {
            if (it == null) continue;
            if (it.getPostId() != null) ids.add(it.getPostId());
            Map<String, Object> m = new HashMap<>();
            m.put("rank", it.getRank());
            m.put("postId", it.getPostId());
            m.put("chunkIndex", it.getChunkIndex());
            m.put("score", it.getScore());
            m.put("title", it.getTitle());
            m.put("tokens", it.getTokens());
            items.add(m);
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("budgetTokens", r.getBudgetTokens());
        stats.put("usedTokens", r.getUsedTokens());
        stats.put("policy", r.getPolicy() == null ? null : r.getPolicy().name());
        return Map.of(
                "ids", ids,
                "items", items,
                "stats", stats
        );
    }

    private static String renderHeader(String tpl, int idx, Item item, boolean showPostId, boolean showChunkIndex, boolean showScore, boolean showTitle) {
        String t = tpl;
        if (t == null || t.isBlank()) {
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
        String out = t;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static List<CitationSource> buildSources(CitationConfigDTO cfg, List<Item> selected) {
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return List.of();
        int max = cfg.getMaxSources() == null ? 0 : Math.max(0, cfg.getMaxSources());
        if (max <= 0) return List.of();
        List<CitationSource> out = new ArrayList<>();
        int n = Math.min(max, selected == null ? 0 : selected.size());
        for (int i = 0; i < n; i++) {
            Item it = selected.get(i);
            if (it == null) continue;
            CitationSource s = new CitationSource();
            s.setIndex(i + 1);
            s.setPostId(it.getPostId());
            s.setChunkIndex(it.getChunkIndex());
            s.setScore(it.getScore());
            s.setTitle(it.getTitle());
            s.setUrl(buildPostUrl(cfg, it.getPostId()));
            out.add(s);
        }
        return out;
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
            if (Boolean.TRUE.equals(cfg.getIncludeChunkIndex()) && s.getChunkIndex() != null) {
                sb.append("chunk=").append(s.getChunkIndex()).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
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

    private static String trimOrDefault(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isBlank() ? def : t;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) x = min;
        if (x > max) x = max;
        return x;
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
        if (cap <= 0) return "";
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
        private Integer chunkIndex;
        private Double score;
        private String title;
        private Integer tokens;
        private String reason;
    }

    @Data
    public static class CitationSource {
        private Integer index;
        private Long postId;
        private Integer chunkIndex;
        private Double score;
        private String title;
        private String url;
    }
}
