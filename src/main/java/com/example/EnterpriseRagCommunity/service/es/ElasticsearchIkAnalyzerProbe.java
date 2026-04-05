package com.example.EnterpriseRagCommunity.service.es;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.ElasticsearchHttpSupport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ElasticsearchIkAnalyzerProbe {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIkAnalyzerProbe.class);

    private final SystemConfigurationService systemConfigurationService;

    private final AtomicReference<Boolean> cached = new AtomicReference<>();

    public boolean isIkSupported() {
        Boolean v = cached.get();
        if (v != null) return v;
        boolean supported = probeOnce();
        cached.compareAndSet(null, supported);
        return Boolean.TRUE.equals(cached.get());
    }

    private boolean probeOnce() {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            URL url = java.net.URI.create(endpoint + "/_analyze").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

            String payload = "{\"analyzer\":\"ik_smart\",\"text\":\"test\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) return true;

            InputStream is = conn.getErrorStream();
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String b = body.toLowerCase();
            if (b.contains("unknown analyzer type") || b.contains("unknown analyzer") || b.contains("ik_smart")) {
                log.warn("Elasticsearch IK analyzer not available. Set app.*.es.ik-enabled=false or install analysis-ik plugin. resp={}", body);
                return false;
            }

            log.warn("Elasticsearch IK probe failed (treat as unsupported). http={}, resp={}", code, body);
            return false;
        } catch (Exception e) {
            log.warn("Elasticsearch IK probe failed (treat as unsupported). err={}", e.getMessage());
            return false;
        }
    }
}
