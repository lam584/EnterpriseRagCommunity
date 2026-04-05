package com.example.EnterpriseRagCommunity.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.AiTokenizerProperties;
import com.example.EnterpriseRagCommunity.config.OpenSearchPlatformProperties;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenSearchTokenizerClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OpenSearchTokenizeService {
    private final OpenSearchPlatformProperties props;
    private final AiTokenizerProperties tokenizerProps;

    public OpenSearchTokenizeResponse tokenize(OpenSearchTokenizeRequest req) {
        String workspaceName = blankToNull(req.getWorkspaceName());
        String serviceId = blankToNull(req.getServiceId());

        List<Map<String, String>> messages = normalizeMessages(req);

        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);

        String apiKey = blankToNull(tokenizerProps.getApiKey());
        if (!canCallUpstream(apiKey, workspaceName, serviceId)) {
            return fallbackResponse(messages);
        }

        try {
            return new OpenSearchTokenizerClient(props).tokenize(null, apiKey, workspaceName, serviceId, body);
        } catch (Exception e) {
            return fallbackResponse(messages);
        }
    }

    private boolean canCallUpstream(String apiKey, String workspaceName, String serviceId) {
        if (apiKey == null || apiKey.isBlank()) return false;
        String host = blankToNull(props.getHost());
        if (host == null) return false;
        String ws = workspaceName == null ? blankToNull(props.getWorkspaceName()) : workspaceName;
        if (ws == null) return false;
        String sid = serviceId == null ? blankToNull(props.getServiceId()) : serviceId;
        return sid != null;
    }

    private static OpenSearchTokenizeResponse fallbackResponse(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        if (messages != null) {
            for (Map<String, String> m : messages) {
                if (m == null) continue;
                String c = m.get("content");
                if (c == null || c.isBlank()) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(c);
            }
        }
        int est = estimateTokens(sb.toString());

        OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
        OpenSearchTokenizeResponse.Usage usage = new OpenSearchTokenizeResponse.Usage();
        usage.setInputTokens(est);
        resp.setUsage(usage);
        return resp;
    }

    private static int estimateTokens(String text) {
        if (text == null) return 0;
        String t = text.trim();
        if (t.isEmpty()) return 0;

        int cjk = 0;
        int other = 0;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);

            if (isCjkLike(cp)) {
                cjk++;
            } else {
                other++;
            }
        }

        int otherTokens = (other + 3) / 4;
        int total = cjk + otherTokens;
        return Math.max(1, total);
    }

    private static boolean isCjkLike(int cp) {
        if (cp >= 0x4E00 && cp <= 0x9FFF) return true;
        if (cp >= 0x3400 && cp <= 0x4DBF) return true;
        if (cp >= 0x20000 && cp <= 0x2A6DF) return true;
        if (cp >= 0x2A700 && cp <= 0x2B73F) return true;
        if (cp >= 0x2B740 && cp <= 0x2B81F) return true;
        if (cp >= 0x2B820 && cp <= 0x2CEAF) return true;
        if (cp >= 0xF900 && cp <= 0xFAFF) return true;
        if (cp >= 0x2F800 && cp <= 0x2FA1F) return true;
        if (cp >= 0x3040 && cp <= 0x30FF) return true;
        return cp >= 0xAC00 && cp <= 0xD7AF;
    }

    private static List<Map<String, String>> normalizeMessages(OpenSearchTokenizeRequest req) {
        List<OpenSearchTokenizeRequest.Message> raw = req.getMessages();
        if (raw != null && !raw.isEmpty()) {
            List<Map<String, String>> out = new ArrayList<>();
            for (OpenSearchTokenizeRequest.Message m : raw) {
                if (m == null) continue;
                String role = blankToNull(m.getRole());
                if (role == null) continue;
                String content = m.getContent() == null ? "" : m.getContent();
                Map<String, String> mm = new HashMap<>();
                mm.put("role", role);
                mm.put("content", content);
                out.add(mm);
            }
            if (out.isEmpty()) {
                throw new IllegalArgumentException("messages 不能为空");
            }

            String lastRole = blankToNull(out.getLast().get("role"));
            if (lastRole == null || !lastRole.equalsIgnoreCase("user")) {
                throw new IllegalArgumentException("Messages must be end with role[user].");
            }
            return out;
        }

        String text = blankToNull(req.getText());
        if (text == null) throw new IllegalArgumentException("text 不能为空");
        return List.of(Map.of("role", "user", "content", text));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
