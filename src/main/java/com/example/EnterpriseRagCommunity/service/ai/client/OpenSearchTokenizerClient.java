package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.config.OpenSearchPlatformProperties;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OpenSearchTokenizerClient {
    private final OpenSearchPlatformProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenSearchTokenizerClient(OpenSearchPlatformProperties props) {
        this.props = props;
    }

    public OpenSearchTokenizeResponse tokenize(String host, String apiKey, String workspaceName, String serviceId, Object body)
            throws Exception {
        if (host == null || host.isBlank()) host = props.getHost();
        if (apiKey == null || apiKey.isBlank()) apiKey = props.getApiKey();
        if (workspaceName == null || workspaceName.isBlank()) workspaceName = props.getWorkspaceName();
        if (serviceId == null || serviceId.isBlank()) serviceId = props.getServiceId();

        if (host == null || host.isBlank()) throw new IllegalStateException("缺少 app.opensearch.platform.host");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("缺少 app.ai.tokenizer.api-key（或 app.opensearch.platform.api-key）");
        if (workspaceName == null || workspaceName.isBlank()) throw new IllegalStateException("缺少 app.opensearch.platform.workspace-name");
        if (serviceId == null || serviceId.isBlank()) throw new IllegalStateException("缺少 app.opensearch.platform.service-id");

        String endpoint = host.trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        endpoint = endpoint + "/v3/openapi/workspaces/" + urlPathEscape(workspaceName)
                + "/text-generation/" + urlPathEscape(serviceId) + "/tokenizer";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(props.getConnectTimeoutMs());
        conn.setReadTimeout(props.getReadTimeoutMs());
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");

        String jsonBody = objectMapper.writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);

        if (code < 200 || code >= 300) {
            String msg = resp;
            try {
                OpenSearchTokenizeResponse err = objectMapper.readValue(resp, OpenSearchTokenizeResponse.class);
                if (err.getMessage() != null && !err.getMessage().isBlank()) {
                    msg = err.getMessage();
                }
                if (err.getCode() != null && !err.getCode().isBlank()) {
                    msg = err.getCode() + ": " + msg;
                }
                if (err.getRequestId() != null && !err.getRequestId().isBlank()) {
                    msg = msg + " (request_id=" + err.getRequestId() + ")";
                }
            } catch (Exception ignore) {
            }
            throw new IllegalStateException("Token 计算上游失败: HTTP " + code + " - " + msg);
        }

        OpenSearchTokenizeResponse ok = objectMapper.readValue(resp, OpenSearchTokenizeResponse.class);
        if (ok.getCode() != null && !ok.getCode().isBlank()) {
            String msg = (ok.getMessage() == null || ok.getMessage().isBlank()) ? ok.getCode() : (ok.getCode() + ": " + ok.getMessage());
            throw new IllegalStateException("Token 计算上游失败: " + msg);
        }
        return ok;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String urlPathEscape(String s) {
        if (s == null) return "";
        return s.replace("%", "%25")
                .replace("/", "%2F")
                .replace(" ", "%20")
                .replace("?", "%3F")
                .replace("#", "%23");
    }
}
