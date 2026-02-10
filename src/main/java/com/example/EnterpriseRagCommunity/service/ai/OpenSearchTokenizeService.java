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

        try {
            String apiKey = blankToNull(tokenizerProps.getApiKey());
            return new OpenSearchTokenizerClient(props).tokenize(null, apiKey, workspaceName, serviceId, body);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Token 计算失败: " + e.getMessage(), e);
        }
    }

    private static List<Map<String, String>> normalizeMessages(OpenSearchTokenizeRequest req) {
        List<OpenSearchTokenizeRequest.Message> raw = req.getMessages();
        if (raw != null && !raw.isEmpty()) {
            OpenSearchTokenizeRequest.Message last = raw.get(raw.size() - 1);
            String lastRole = last == null ? null : blankToNull(last.getRole());
            if (lastRole == null || !lastRole.equalsIgnoreCase("user")) {
                throw new IllegalArgumentException("Messages must be end with role[user].");
            }

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
