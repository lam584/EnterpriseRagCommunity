package com.example.EnterpriseRagCommunity.service.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;

final class AiChatCitationSupport {

    private AiChatCitationSupport() {
    }

    static String normalizeCitationMode(CitationConfigDTO citationCfg) {
        if (citationCfg == null) return "MODEL_INLINE";
        String mode = citationCfg.getCitationMode() == null
                ? ""
                : citationCfg.getCitationMode().trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("MODEL_INLINE") && !mode.equals("SOURCES_SECTION") && !mode.equals("BOTH")) {
            return "MODEL_INLINE";
        }
        return mode;
    }

    static boolean shouldExposeCitationSources(CitationConfigDTO citationCfg) {
        if (citationCfg == null || !Boolean.TRUE.equals(citationCfg.getEnabled())) return false;
        String mode = normalizeCitationMode(citationCfg);
        return mode.equals("SOURCES_SECTION") || mode.equals("BOTH");
    }

    static boolean shouldStripInlineCitations(CitationConfigDTO citationCfg) {
        if (citationCfg == null || !Boolean.TRUE.equals(citationCfg.getEnabled())) return false;
        return "SOURCES_SECTION".equals(normalizeCitationMode(citationCfg));
    }

    static String enforceCitationModeAnswerBody(CitationConfigDTO citationCfg, String answerText) {
        if (answerText == null || answerText.isBlank()) return answerText;
        if (!shouldStripInlineCitations(citationCfg)) return answerText;
        return stripInlineCitationMarkers(answerText);
    }

    static List<RagContextPromptService.CitationSource> resolveSourcesForOutput(
            CitationConfigDTO citationCfg,
            List<RagContextPromptService.CitationSource> sources,
            String answerText
    ) {
        if (!shouldExposeCitationSources(citationCfg)) return List.of();
        List<RagContextPromptService.CitationSource> cited = filterSourcesByCitations(sources, answerText);
        if (!cited.isEmpty()) return cited;
        if (sources == null || sources.isEmpty()) return List.of();
        return sources;
    }

    static List<AiChatResponseDTO.AiCitationSourceDTO> toCitationSourceDtos(List<RagContextPromptService.CitationSource> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        int n = Math.min(200, sources.size());
        List<AiChatResponseDTO.AiCitationSourceDTO> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RagContextPromptService.CitationSource s = sources.get(i);
            if (s == null) continue;
            AiChatResponseDTO.AiCitationSourceDTO dto = new AiChatResponseDTO.AiCitationSourceDTO();
            dto.setIndex(s.getIndex());
            dto.setPostId(s.getPostId());
            dto.setCommentId(s.getCommentId());
            dto.setChunkIndex(s.getChunkIndex());
            dto.setScore(s.getScore());
            dto.setTitle(s.getTitle());
            dto.setUrl(s.getUrl());
            dto.setSnippet(s.getSnippet());
            out.add(dto);
        }
        return out;
    }

    static String buildSourcesEventData(List<RagContextPromptService.CitationSource> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"sources\":[");
        int n = Math.min(200, sources == null ? 0 : sources.size());
        for (int i = 0; i < n; i++) {
            RagContextPromptService.CitationSource s = sources.get(i);
            if (s == null) continue;
            if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
            sb.append('{');
            sb.append("\"index\":").append(s.getIndex() == null ? "null" : s.getIndex());
            sb.append(",\"postId\":").append(s.getPostId() == null ? "null" : s.getPostId());
            sb.append(",\"commentId\":").append(s.getCommentId() == null ? "null" : s.getCommentId());
            sb.append(",\"chunkIndex\":").append(s.getChunkIndex() == null ? "null" : s.getChunkIndex());
            sb.append(",\"score\":").append(s.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", s.getScore()));
            sb.append(",\"title\":\"").append(AiChatJsonSupport.jsonEscape(s.getTitle() == null ? "" : s.getTitle())).append('"');
            sb.append(",\"url\":\"").append(AiChatJsonSupport.jsonEscape(s.getUrl() == null ? "" : s.getUrl())).append('"');
            sb.append(",\"snippet\":\"").append(AiChatJsonSupport.jsonEscape(s.getSnippet() == null ? "" : s.getSnippet())).append('"');
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    static List<RagContextPromptService.CitationSource> filterSourcesByCitations(
            List<RagContextPromptService.CitationSource> sources,
            String answerText
    ) {
        if (sources == null || sources.isEmpty()) return List.of();
        if (answerText == null || answerText.isBlank()) return List.of();

        int maxIndex = 0;
        for (RagContextPromptService.CitationSource s : sources) {
            if (s == null || s.getIndex() == null) continue;
            maxIndex = Math.max(maxIndex, s.getIndex());
        }
        if (maxIndex <= 0) return List.of();

        Set<Integer> cited = extractCitationIndexes(answerText, maxIndex);
        if (cited.isEmpty()) return List.of();

        List<RagContextPromptService.CitationSource> out = new ArrayList<>();
        for (RagContextPromptService.CitationSource s : sources) {
            if (s == null || s.getIndex() == null) continue;
            if (cited.contains(s.getIndex())) out.add(s);
        }
        return out;
    }

    static String stripInlineCitationMarkers(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder out = new StringBuilder(text.length());
        boolean inFence = false;
        boolean inInlineCode = false;
        int n = text.length();

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);

            if (c == '`') {
                if (i + 2 < n && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
                    inFence = !inFence;
                    out.append("```");
                    i += 2;
                    continue;
                }
                if (!inFence) inInlineCode = !inInlineCode;
                out.append(c);
                continue;
            }

            if (inFence || inInlineCode) {
                out.append(c);
                continue;
            }

            if (c != '[') {
                out.append(c);
                continue;
            }

            int j = i + 1;
            int value = 0;
            int digits = 0;
            while (j < n && digits < 3) {
                char d = text.charAt(j);
                if (d < '0' || d > '9') break;
                value = value * 10 + (d - '0');
                digits++;
                j++;
            }

            boolean isCitation = digits > 0
                    && j < n
                    && text.charAt(j) == ']'
                    && !(j + 1 < n && text.charAt(j + 1) == '(')
                    && value > 0;

            if (!isCitation) {
                out.append(c);
                continue;
            }

            i = j;

            int next = i + 1;
            boolean leftWs = !out.isEmpty() && Character.isWhitespace(out.charAt(out.length() - 1));
            boolean rightWs = next < n && Character.isWhitespace(text.charAt(next));
            if (leftWs && rightWs) {
                while (next < n && Character.isWhitespace(text.charAt(next))) {
                    next++;
                }
                i = next - 1;
            }
        }

        return out.toString();
    }

    static Set<Integer> extractCitationIndexes(String text, int maxIndex) {
        Set<Integer> out = new HashSet<>();
        if (text == null || text.isEmpty()) return out;

        boolean inFence = false;
        boolean inInlineCode = false;
        int n = text.length();

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);

            if (c == '`') {
                if (i + 2 < n && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
                    inFence = !inFence;
                    i += 2;
                    continue;
                }
                if (!inFence) inInlineCode = !inInlineCode;
                continue;
            }

            if (inFence || inInlineCode) continue;
            if (c != '[') continue;

            int j = i + 1;
            int value = 0;
            int digits = 0;
            while (j < n && digits < 3) {
                char d = text.charAt(j);
                if (d < '0' || d > '9') break;
                value = value * 10 + (d - '0');
                digits++;
                j++;
            }
            if (digits == 0) continue;
            if (j >= n || text.charAt(j) != ']') continue;
            if (j + 1 < n && text.charAt(j + 1) == '(') continue;
            if (value <= 0 || value > maxIndex) continue;
            out.add(value);
        }

        return out;
    }

    static String normalizeCitationQuoteFormatting(String text) {
        if (text == null || text.isBlank()) return text;
        StringBuilder out = new StringBuilder(text.length());
        boolean inFence = false;
        boolean inInlineCode = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '`') {
                if (i + 2 < n && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
                    inFence = !inFence;
                    out.append("```");
                    i += 2;
                    continue;
                }
                if (!inFence) inInlineCode = !inInlineCode;
                out.append(c);
                continue;
            }
            if (inFence || inInlineCode) {
                out.append(c);
                continue;
            }
            if (c == '\\' && i + 1 < n && isCitationQuote(text.charAt(i + 1))) {
                continue;
            }
            if (!isCitationOpenQuote(c)) {
                out.append(c);
                continue;
            }

            int closeIndex = findCitationQuoteClose(text, i + 1);
            if (closeIndex < 0) {
                out.append(c);
                continue;
            }
            int tailIndex = closeIndex + 1;
            if (tailIndex >= n || text.charAt(tailIndex) != '[') {
                out.append(c);
                continue;
            }
            out.append('“');
            out.append(text, i + 1, closeIndex);
            out.append('”');
            i = closeIndex;
        }
        return out.toString();
    }

    private static boolean isCitationQuote(char c) {
        return c == '"' || c == '“' || c == '”' || c == '「' || c == '」' || c == '『' || c == '』';
    }

    private static boolean isCitationOpenQuote(char c) {
        return c == '"' || c == '“' || c == '「' || c == '『';
    }

    private static int findCitationQuoteClose(String text, int start) {
        int n = text.length();
        for (int i = start; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') return -1;
            if (c == '\\' && i + 1 < n && isCitationQuote(text.charAt(i + 1))) {
                return i + 1;
            }
            if (c == '"' || c == '”' || c == '」' || c == '』') {
                return i;
            }
        }
        return -1;
    }
}
