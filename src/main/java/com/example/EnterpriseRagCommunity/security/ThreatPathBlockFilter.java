package com.example.EnterpriseRagCommunity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ThreatPathBlockFilter extends OncePerRequestFilter {

    @Value("${app.security.scan-block.enabled:true}")
    private boolean enabled;

    @Value("${app.security.scan-block.patterns:/.env,/.git/,phpinfo.php,/info.php,/wp-admin,/wp-login,/vendor/,/api/keys,/api/apikey,/api/v1/stripe/config.js,/api/v2/payment/keys.js,/api/webhook.js,.bak,.sql,.zip,.tar.gz,/.aws/,.ssh/}")
    private String rawPatterns;

    private final ObjectMapper objectMapper;

    public ThreatPathBlockFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || request == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = normalize(request.getRequestURI());
        String matched = matchPattern(uri);
        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }
        request.setAttribute("threatBlocked", true);
        request.setAttribute("threatPattern", matched);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        if (uri.startsWith("/api/")) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 404);
            body.put("error", "Not Found");
            body.put("message", "Not Found");
            body.put("path", uri);
            response.getWriter().write(objectMapper.writeValueAsString(body));
        }
    }

    private String matchPattern(String uri) {
        if (uri == null || uri.isBlank()) return null;
        List<String> patterns = parsePatterns(rawPatterns);
        if (patterns.isEmpty()) return null;
        for (String p : patterns) {
            if (uri.contains(p)) return p;
        }
        return null;
    }

    private static List<String> parsePatterns(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[,\\n\\r\\t]+"))
                .map(ThreatPathBlockFilter::normalize)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        return v.isBlank() ? null : v;
    }
}
