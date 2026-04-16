package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogWriter;
import com.example.EnterpriseRagCommunity.service.access.RequestAuditContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

@Component
@ConditionalOnBean({AccessLogWriter.class, AdministratorService.class})
@RequiredArgsConstructor
public class AccessLogsFilter extends OncePerRequestFilter {

    private static final String[] EXCLUDED_PATH_PREFIXES = {
            "/api/admin/access-logs",
            "/api/admin/audit-logs",
            "/api/admin/log-retention"
    };

    private final AccessLogWriter accessLogWriter;
    private final AdministratorService administratorService;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.logging.access.capture-body:true}")
    private boolean captureBodyEnabled;

    @Value("${app.logging.access.capture-response-body:true}")
    private boolean captureResponseBodyEnabled;

    @Value("${app.logging.access.max-body-bytes:65536}")
    private int maxBodyBytes;

    @Value("${app.logging.access.sample-rate:1.0}")
    private double sampleRate = 1.0;

    @Value("${app.logging.access.keep-error:true}")
    private boolean keepErrorLogsWhenSampling = true;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ConcurrentHashMap<String, UserIdCacheSupport.UserIdCacheEntry> USER_ID_CACHE = new ConcurrentHashMap<>();
    private static final long USER_ID_CACHE_TTL_MS = 5 * 60 * 1000L;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) return false;
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String sanitizeQueryString(String qs) {
        return sanitizeUrlEncodedPairs(qs, AccessLogsFilter::isQuerySensitiveKey);
    }

    private Long resolveUserId(Authentication auth) {
        String username = resolveUsername(auth);
        if (username == null) return null;
        return UserIdCacheSupport.resolveUserId(USER_ID_CACHE, USER_ID_CACHE_TTL_MS, administratorService, username);
    }

    private static String resolveUsername(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String name = auth.getName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private static Map<String, Object> extractRequestBody(ContentCachingRequestWrapper cachingRequest, HttpServletRequest rawRequest, int maxBytes) {
        if (cachingRequest == null) return null;
        if (rawRequest == null) return null;
        String contentType = safeLower(rawRequest.getContentType());
        if (contentType == null) return null;
        if (contentType.startsWith("multipart/form-data")) {
            return unsupportedBody(rawRequest.getContentType(), "multipart");
        }
        if (contentType.startsWith("application/octet-stream") || contentType.startsWith("image/")) {
            return unsupportedBody(rawRequest.getContentType(), "binary");
        }

        byte[] bytes = cachingRequest.getContentAsByteArray();
        if (bytes.length == 0) return null;

        Map<String, Object> out = captureTextBody(rawRequest.getContentType(), contentType, bytes, maxBytes, rawRequest.getCharacterEncoding());
        boolean declaredTooLarge = false;
        try {
            if (maxBytes > 0 && rawRequest.getContentLengthLong() > maxBytes) declaredTooLarge = true;
        } catch (Exception ignored) {
        }
        out.put("truncated", Boolean.TRUE.equals(out.get("truncated")) || declaredTooLarge);
        return out;
    }

    private static String urlDecodeSafe(String s) {
        if (s == null) return "";
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String urlEncodeSafe(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String firstNonBlank(String... vs) {
        if (vs == null) return null;
        for (String v : vs) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static Integer safeInt(int v) {
        return v <= 0 ? null : v;
    }

    private static String safeString(String s, int maxLen) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return null;
        if (t.length() <= maxLen) return t;
        return t.substring(0, maxLen);
    }

    private static void putIfNotBlank(Map<String, Object> map, String k, String v) {
        if (map == null || k == null) return;
        if (v == null || v.isBlank()) return;
        map.put(k, v);
    }

    private static String sha256Hex(String s) {
        if (s == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static int clampMaxBodyBytes(int v) {
        if (v <= 0) return 0;
        return Math.min(v, 1024 * 1024);
    }

    private static double normalizeSampleRate(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 1.0;
        return Math.clamp(v, 0.0, 1.0);
    }

    private boolean shouldWriteAccessLog(int statusCode) {
        double rate = normalizeSampleRate(sampleRate);
        if (rate >= 1.0) return true;
        if (keepErrorLogsWhenSampling && statusCode >= 500) return true;
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private static boolean shouldWrapRequest(HttpServletRequest request) {
        if (request == null) return false;
        String method = request.getMethod();
        if (method == null) return false;
        String m = method.trim().toUpperCase();
        if ("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m)) return false;
        String contentType = safeLower(request.getContentType());
        if (contentType == null) return false;
        if (contentType.startsWith("multipart/form-data")) return false;
        if (contentType.startsWith("application/octet-stream")) return false;
        return !contentType.startsWith("image/");
    }

    private static boolean shouldWrapResponse(HttpServletRequest request) {
        if (request == null) return true;
        String accept = safeLower(request.getHeader("Accept"));
        return accept == null || !accept.contains("text/event-stream");
    }

    private static Map<String, Object> extractHeaderSnapshot(HttpServletRequest request, String requestId, String traceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (request == null) return out;
        putIfNotBlank(out, "contentType", safeString(request.getContentType(), 128));
        putIfNotBlank(out, "accept", safeString(request.getHeader("Accept"), 256));
        putIfNotBlank(out, "contentLength", safeString(request.getHeader("Content-Length"), 32));
        putIfNotBlank(out, "requestId", safeString(requestId, 64));
        putIfNotBlank(out, "traceId", safeString(traceId, 64));
        return out;
    }

    private static Map<String, Object> extractResponseBody(LimitedCaptureResponseWrapper cachingResponse, HttpServletResponse response, int maxBytes) {
        if (cachingResponse == null || response == null) return null;
        String contentType = safeLower(response.getContentType());
        if (contentType != null) {
            if (contentType.startsWith("multipart/form-data")) return unsupportedBody(response.getContentType(), "multipart");
            if (contentType.startsWith("application/octet-stream") || contentType.startsWith("image/")) return unsupportedBody(response.getContentType(), "binary");
            if (contentType.startsWith("text/event-stream")) return unsupportedBody(response.getContentType(), "streaming");
        }

        byte[] bytes = cachingResponse.getCapturedAsByteArray();
        if (bytes.length == 0) {
            Map<String, Object> out = new LinkedHashMap<>();
            putCaptureMeta(out, response.getContentType(), 0, maxBytes, null);
            return out;
        }

        Map<String, Object> out = captureTextBody(response.getContentType(), contentType, bytes, maxBytes, response.getCharacterEncoding());
        out.put("truncated", Boolean.TRUE.equals(out.get("truncated")) || cachingResponse.isTruncated());
        out.put("status", response.getStatus());
        return out;
    }

    private static Map<String, Object> captureTextBody(String rawContentType, String normalizedContentType,
                                                       byte[] bytes, int maxBytes, String encoding) {
        Map<String, Object> out = new LinkedHashMap<>();
        putCaptureMeta(out, rawContentType, bytes.length, maxBytes, sha256Hex(bytes));

        String encodingToUse = encoding;
        if (encodingToUse == null || encodingToUse.isBlank()) encodingToUse = StandardCharsets.UTF_8.name();
        String bodyText = safeDecode(bytes, encodingToUse);
        bodyText = sanitizeBodyText(normalizedContentType, bodyText);
        BodySnippet snippet = snippet(bodyText, maxBytes);
        out.put("truncated", snippet.truncated());
        out.put("body", snippet.text());
        return out;
    }

    private static String sanitizeUrlEncodedBody(String bodyText) {
        return sanitizeUrlEncodedPairs(bodyText, AccessLogsFilter::isSensitiveKey);
    }

    private static Map<String, Object> unsupportedBody(String contentType, String reason) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contentType", contentType);
        out.put("captured", false);
        out.put("reason", reason);
        return out;
    }

    private static void putCaptureMeta(Map<String, Object> out, String contentType, int capturedBytes, int maxBytes, String sha256) {
        if (out == null) return;
        if (contentType != null) out.put("contentType", safeString(contentType, 128));
        out.put("capturedBytes", capturedBytes);
        out.put("limitBytes", maxBytes);
        out.put("sha256", sha256);
    }

    private static String sanitizeUrlEncodedPairs(String raw, Predicate<String> sensitiveKeyMatcher) {
        if (raw == null || raw.isBlank()) return raw;
        String[] pairs = raw.split("&");
        StringBuilder out = new StringBuilder();
        for (String pair : pairs) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            String k = idx >= 0 ? pair.substring(0, idx) : pair;
            String v = idx >= 0 ? pair.substring(idx + 1) : "";
            String key = urlDecodeSafe(k).toLowerCase();
            boolean sensitive = sensitiveKeyMatcher != null && sensitiveKeyMatcher.test(key);
            String val = sensitive ? "***" : urlDecodeSafe(v);
            if (!out.isEmpty()) out.append('&');
            out.append(urlEncodeSafe(k)).append('=').append(urlEncodeSafe(val));
        }
        return out.toString();
    }

    private static boolean isQuerySensitiveKey(String keyLower) {
        if (keyLower == null || keyLower.isBlank()) return false;
        return keyLower.contains("password")
                || keyLower.contains("passwd")
                || keyLower.contains("token")
                || keyLower.contains("secret")
                || keyLower.contains("code");
    }

    private static String safeLower(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return null;
        return t.toLowerCase();
    }

    private static String safeDecode(byte[] bytes, String encoding) {
        try {
            return new String(bytes, encoding);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String sanitizeBodyText(String contentTypeLower, String bodyText) {
        if (bodyText == null) return null;
        if (contentTypeLower == null) return bodyText;
        if (contentTypeLower.contains("json") || contentTypeLower.contains("+json")) {
            return maskJsonIfPossible(bodyText);
        }
        if (contentTypeLower.startsWith("application/x-www-form-urlencoded")) {
            return sanitizeUrlEncodedBody(bodyText);
        }
        return bodyText;
    }

    private static BodySnippet snippet(String text, int maxBytes) {
        if (text == null) return new BodySnippet(null, false);
        if (maxBytes <= 0) return new BodySnippet("", true);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return new BodySnippet(text, false);
        int end = Math.clamp(maxBytes, 0, text.length());
        String cut = text.substring(0, end);
        while (cut.getBytes(StandardCharsets.UTF_8).length > maxBytes && !cut.isEmpty()) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return new BodySnippet(cut, true);
    }

    private static String maskJsonIfPossible(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) return jsonText;
        try {
            JsonNode root = MAPPER.readTree(jsonText);
            JsonNode masked = maskJsonNode(root);
            return MAPPER.writeValueAsString(masked);
        } catch (Exception e) {
            return jsonText;
        }
    }

    private static JsonNode maskJsonNode(JsonNode node) {
        switch (node) {
            case null -> {
                return null;
            }
            case ObjectNode objNode -> {
                ObjectNode obj = objNode.deepCopy();
                obj.fieldNames().forEachRemaining((String k) -> {
                    String kl = k == null ? "" : k.toLowerCase();
                    if (isSensitiveKey(kl)) {
                        obj.put(k, "***");
                    } else {
                        obj.set(k, maskJsonNode(obj.get(k)));
                    }
                });
                return obj;
            }
            case ArrayNode arrNode -> {
                ArrayNode arr = arrNode.deepCopy();
                for (int i = 0; i < arr.size(); i++) {
                    arr.set(i, maskJsonNode(arr.get(i)));
                }
                return arr;
            }
            default -> {
            }
        }
        return node;
    }

    private static boolean isSensitiveKey(String keyLower) {
        if (keyLower == null || keyLower.isBlank()) return false;
        return keyLower.contains("password")
                || keyLower.contains("passwd")
                || keyLower.contains("token")
                || keyLower.contains("secret")
                || keyLower.contains("authorization")
                || keyLower.contains("cookie")
                || keyLower.contains("xsrf")
                || keyLower.contains("csrf")
                || keyLower.contains("code");
    }

    private record BodySnippet(String text, boolean truncated) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        long startMs = System.currentTimeMillis();

        int safeMaxBodyBytes = clampMaxBodyBytes(maxBodyBytes);

        String requestId = firstNonBlank(
                request.getHeader("X-Request-Id"),
                request.getHeader("X-Correlation-Id"),
                request.getHeader("X-Trace-Id")
        );
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        response.setHeader("X-Request-Id", requestId);

        String traceId = firstNonBlank(request.getHeader("X-Trace-Id"), requestId);

        String clientIp = clientIpResolver.resolveClientIp(request);
        Integer clientPort = safeInt(request.getRemotePort());
        String serverIp = safeString(request.getLocalAddr(), 64);
        Integer serverPort = safeInt(request.getLocalPort());

        String method = safeString(request.getMethod(), 16);
        String path = safeString(request.getRequestURI(), 512);
        String scheme = safeString(request.getScheme(), 16);
        String host = safeString(request.getHeader("Host"), 255);

        String referer = safeString(request.getHeader("Referer"), 512);
        String userAgent = safeString(request.getHeader("User-Agent"), 512);

        String queryString = sanitizeQueryString(request.getQueryString());
        queryString = safeString(queryString, 1024);

        Map<String, Object> reqDetails = new LinkedHashMap<>();
        putIfNotBlank(reqDetails, "forwarded", safeString(request.getHeader("Forwarded"), 1024));
        putIfNotBlank(reqDetails, "xForwardedFor", safeString(request.getHeader("X-Forwarded-For"), 1024));
        putIfNotBlank(reqDetails, "xRealIp", safeString(request.getHeader("X-Real-IP"), 64));
        putIfNotBlank(reqDetails, "acceptLanguage", safeString(request.getHeader("Accept-Language"), 256));
        putIfNotBlank(reqDetails, "secChUa", safeString(request.getHeader("Sec-CH-UA"), 256));
        putIfNotBlank(reqDetails, "secChUaPlatform", safeString(request.getHeader("Sec-CH-UA-Platform"), 256));
        putIfNotBlank(reqDetails, "secChUaMobile", safeString(request.getHeader("Sec-CH-UA-Mobile"), 64));
        putIfNotBlank(reqDetails, "clientFingerprint", safeString(request.getHeader("X-Client-Fingerprint"), 256));
        Map<String, Object> headerSnapshot = extractHeaderSnapshot(request, requestId, traceId);
        if (!headerSnapshot.isEmpty()) reqDetails.put("headers", headerSnapshot);

        HttpSession session = request.getSession(false);
        if (session != null) {
            reqDetails.put("sessionIdHash", sha256Hex(session.getId()));
        }

        HttpServletRequest reqForChain = request;
        HttpServletResponse respForChain = response;
        ContentCachingRequestWrapper cachingRequest = null;
        LimitedCaptureResponseWrapper cachingResponse = null;
        if (captureBodyEnabled && shouldWrapRequest(request)) {
            cachingRequest = new ContentCachingRequestWrapper(request, safeMaxBodyBytes);
            reqForChain = cachingRequest;
        }
        if (captureBodyEnabled && captureResponseBodyEnabled && shouldWrapResponse(request)) {
            cachingResponse = new LimitedCaptureResponseWrapper(response, safeMaxBodyBytes);
            respForChain = cachingResponse;
        }

        RequestAuditContextHolder.set(new RequestAuditContextHolder.RequestAuditContext(
                requestId,
                traceId,
                clientIp,
                clientPort,
                serverIp,
                serverPort,
                method,
                path,
                scheme,
                host,
                userAgent,
                referer,
                reqDetails
        ));

        int statusCode;
        int latencyMs;
        try {
            filterChain.doFilter(reqForChain, respForChain);
        } finally {
            try {
                statusCode = respForChain.getStatus();
                long cost = System.currentTimeMillis() - startMs;
                latencyMs = Math.clamp(cost, 0, Integer.MAX_VALUE);

                if (captureBodyEnabled) {
                    Map<String, Object> reqBody = extractRequestBody(cachingRequest, request, safeMaxBodyBytes);
                    if (reqBody != null && !reqBody.isEmpty()) reqDetails.put("reqBody", reqBody);
                    Map<String, Object> resBody = extractResponseBody(cachingResponse, respForChain, safeMaxBodyBytes);
                    if (resBody != null && !resBody.isEmpty()) reqDetails.put("resBody", resBody);
                }

                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                Long userId = resolveUserId(auth);
                String username = resolveUsername(auth);

                if (!shouldWriteAccessLog(statusCode)) {
                    return;
                }

                accessLogWriter.write(
                        null,
                        userId,
                        username,
                        method,
                        path,
                        queryString,
                        statusCode,
                        latencyMs,
                        clientIp,
                        clientPort,
                        serverIp,
                        serverPort,
                        scheme,
                        host,
                        requestId,
                        traceId,
                        userAgent,
                        referer,
                        reqDetails
                );
            } catch (Exception ignored) {
                // ignore
            } finally {
                RequestAuditContextHolder.clear();
            }
        }
    }

    private static String sha256Hex(byte[] bytes) {
        if (bytes == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class LimitedCaptureResponseWrapper extends HttpServletResponseWrapper {
        private final int limitBytes;
        private final ByteArrayOutputStream capture;
        private ServletOutputStream outputStream;
        private TeeServletOutputStream tee;
        private PrintWriter writer;

        private LimitedCaptureResponseWrapper(HttpServletResponse response, int limitBytes) {
            super(response);
            this.limitBytes = Math.max(0, limitBytes);
            this.capture = new ByteArrayOutputStream(Math.min(this.limitBytes, 4096));
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) throw new IllegalStateException("getWriter() has already been called");
            if (outputStream == null) {
                ServletOutputStream delegate = super.getOutputStream();
                tee = new TeeServletOutputStream(delegate, capture, limitBytes);
                outputStream = tee;
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStream != null) throw new IllegalStateException("getOutputStream() has already been called");
            if (writer == null) {
                String enc = getCharacterEncoding();
                if (enc == null || enc.isBlank()) enc = StandardCharsets.UTF_8.name();
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), enc), true);
            }
            return writer;
        }

        private byte[] getCapturedAsByteArray() {
            try {
                if (writer != null) writer.flush();
                if (outputStream != null) outputStream.flush();
            } catch (Exception ignored) {
            }
            return capture.toByteArray();
        }

        private boolean isTruncated() {
            return tee != null && tee.isTruncated();
        }
    }

    private static final class TeeServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;
        private final ByteArrayOutputStream capture;
        private final int limitBytes;
        private int captured;
        private boolean truncated;

        private TeeServletOutputStream(ServletOutputStream delegate, ByteArrayOutputStream capture, int limitBytes) {
            this.delegate = delegate;
            this.capture = capture;
            this.limitBytes = Math.max(0, limitBytes);
            this.captured = 0;
            this.truncated = false;
        }

        @Override
        public boolean isReady() {
            return delegate == null || delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            if (delegate != null) delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int b) throws IOException {
            if (delegate != null) delegate.write(b);
            if (capture != null && captured < limitBytes) {
                capture.write(b);
                captured++;
            } else if (limitBytes > 0) {
                truncated = true;
            }
        }

        @Override
        public void write(byte @NonNull [] b, int off, int len) throws IOException {
            if (delegate != null) delegate.write(b, off, len);
            if (len <= 0) return;
            if (capture != null && captured < limitBytes) {
                int remain = limitBytes - captured;
                int toWrite = Math.min(remain, len);
                if (toWrite > 0) {
                    capture.write(b, off, toWrite);
                    captured += toWrite;
                }
                if (len > toWrite && limitBytes > 0) truncated = true;
            } else if (limitBytes > 0) {
                truncated = true;
            }
        }

        private boolean isTruncated() {
            return truncated;
        }
    }
}
