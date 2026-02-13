package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnBean({AccessLogWriter.class, AdministratorService.class})
@RequiredArgsConstructor
public class AccessLogsFilter extends OncePerRequestFilter {

    private final AccessLogWriter accessLogWriter;
    private final AdministratorService administratorService;

    @Value("${app.logging.access.capture-body:true}")
    private boolean captureBodyEnabled;

    @Value("${app.logging.access.capture-response-body:true}")
    private boolean captureResponseBodyEnabled;

    @Value("${app.logging.access.max-body-bytes:65536}")
    private int maxBodyBytes;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ConcurrentHashMap<String, UserIdCacheEntry> USER_ID_CACHE = new ConcurrentHashMap<>();
    private static final long USER_ID_CACHE_TTL_MS = 5 * 60 * 1000L;

    private record UserIdCacheEntry(Long userId, long expiresAtMs) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
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

        String clientIp = resolveClientIp(request);
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

        Integer statusCode = null;
        Integer latencyMs = null;
        try {
            filterChain.doFilter(reqForChain, respForChain);
        } finally {
            try {
                statusCode = respForChain.getStatus();
                long cost = System.currentTimeMillis() - startMs;
                latencyMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0, cost));

                if (captureBodyEnabled) {
                    Map<String, Object> reqBody = extractRequestBody(cachingRequest, request, safeMaxBodyBytes);
                    if (reqBody != null && !reqBody.isEmpty()) reqDetails.put("reqBody", reqBody);
                    Map<String, Object> resBody = extractResponseBody(cachingResponse, respForChain, safeMaxBodyBytes);
                    if (resBody != null && !resBody.isEmpty()) reqDetails.put("resBody", resBody);
                }

                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                Long userId = resolveUserId(auth);
                String username = resolveUsername(auth);

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

    private Long resolveUserId(Authentication auth) {
        String username = resolveUsername(auth);
        if (username == null) return null;

        long now = System.currentTimeMillis();
        UserIdCacheEntry cached = USER_ID_CACHE.get(username);
        if (cached != null && cached.expiresAtMs() > now) return cached.userId();

        Optional<UsersEntity> user = administratorService.findByUsername(username);
        Long id = user.map(UsersEntity::getId).orElse(null);
        USER_ID_CACHE.put(username, new UserIdCacheEntry(id, now + USER_ID_CACHE_TTL_MS));
        return id;
    }

    private static String resolveUsername(Authentication auth) {
        if (auth == null) return null;
        if (!auth.isAuthenticated()) return null;
        if ("anonymousUser".equals(auth.getPrincipal())) return null;
        String name = auth.getName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            String ip = parseForwardedFor(forwarded);
            if (ip != null) return ip;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            if (parts.length > 0) {
                String first = parts[0].trim();
                if (!first.isBlank()) return first;
            }
        }

        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();

        String ra = request.getRemoteAddr();
        return ra == null ? null : ra.trim();
    }

    private static String parseForwardedFor(String forwarded) {
        String[] parts = forwarded.split(";");
        for (String p : parts) {
            String t = p.trim();
            if (t.regionMatches(true, 0, "for=", 0, 4)) {
                String v = t.substring(4).trim();
                v = stripQuotes(v);
                if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1);
                int colon = v.indexOf(':');
                if (colon > 0) v = v.substring(0, colon);
                return v.isBlank() ? null : v;
            }
        }
        return null;
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String sanitizeQueryString(String qs) {
        if (qs == null || qs.isBlank()) return null;
        String[] pairs = qs.split("&");
        StringBuilder out = new StringBuilder();
        for (String pair : pairs) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            String k = idx >= 0 ? pair.substring(0, idx) : pair;
            String v = idx >= 0 ? pair.substring(idx + 1) : "";
            String key = urlDecodeSafe(k).toLowerCase();
            boolean sensitive = key.contains("password") || key.contains("passwd") || key.contains("token") || key.contains("secret") || key.contains("code");
            String val = sensitive ? "***" : urlDecodeSafe(v);
            if (out.length() > 0) out.append('&');
            out.append(urlEncodeSafe(k)).append('=').append(urlEncodeSafe(val));
        }
        return out.toString();
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
        if (contentType.startsWith("image/")) return false;
        return true;
    }

    private static boolean shouldWrapResponse(HttpServletRequest request) {
        if (request == null) return true;
        String accept = safeLower(request.getHeader("Accept"));
        if (accept != null && accept.contains("text/event-stream")) return false;
        return true;
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

    private static Map<String, Object> extractRequestBody(ContentCachingRequestWrapper cachingRequest, HttpServletRequest rawRequest, int maxBytes) {
        if (cachingRequest == null) return null;
        String contentType = safeLower(rawRequest == null ? null : rawRequest.getContentType());
        if (contentType == null) return null;
        if (contentType.startsWith("multipart/form-data")) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("contentType", rawRequest.getContentType());
            out.put("captured", false);
            out.put("reason", "multipart");
            return out;
        }
        if (contentType.startsWith("application/octet-stream") || contentType.startsWith("image/")) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("contentType", rawRequest.getContentType());
            out.put("captured", false);
            out.put("reason", "binary");
            return out;
        }

        byte[] bytes = cachingRequest.getContentAsByteArray();
        if (bytes == null || bytes.length == 0) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contentType", safeString(rawRequest.getContentType(), 128));
        out.put("capturedBytes", bytes.length);
        out.put("limitBytes", maxBytes);
        out.put("sha256", sha256Hex(bytes));

        String encoding = rawRequest == null ? null : rawRequest.getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) encoding = StandardCharsets.UTF_8.name();

        String bodyText = safeDecode(bytes, encoding);
        bodyText = sanitizeBodyText(contentType, bodyText);
        BodySnippet snippet = snippet(bodyText, maxBytes);
        boolean declaredTooLarge = false;
        try {
            if (rawRequest != null && maxBytes > 0 && rawRequest.getContentLengthLong() > maxBytes) declaredTooLarge = true;
        } catch (Exception ignored) {
        }
        out.put("truncated", snippet.truncated() || declaredTooLarge);
        out.put("body", snippet.text());
        return out;
    }

    private static Map<String, Object> extractResponseBody(LimitedCaptureResponseWrapper cachingResponse, HttpServletResponse response, int maxBytes) {
        if (cachingResponse == null || response == null) return null;
        String contentType = safeLower(response.getContentType());
        if (contentType != null) {
            if (contentType.startsWith("multipart/form-data")) return Map.of("contentType", response.getContentType(), "captured", false, "reason", "multipart");
            if (contentType.startsWith("application/octet-stream") || contentType.startsWith("image/")) return Map.of("contentType", response.getContentType(), "captured", false, "reason", "binary");
            if (contentType.startsWith("text/event-stream")) return Map.of("contentType", response.getContentType(), "captured", false, "reason", "streaming");
        }

        byte[] bytes = cachingResponse.getCapturedAsByteArray();
        if (bytes == null || bytes.length == 0) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (response.getContentType() != null) out.put("contentType", safeString(response.getContentType(), 128));
            out.put("capturedBytes", 0);
            out.put("limitBytes", maxBytes);
            out.put("sha256", null);
            return out;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (response.getContentType() != null) out.put("contentType", safeString(response.getContentType(), 128));
        out.put("capturedBytes", bytes.length);
        out.put("limitBytes", maxBytes);
        out.put("sha256", sha256Hex(bytes));

        String encoding = response.getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) encoding = StandardCharsets.UTF_8.name();
        String bodyText = safeDecode(bytes, encoding);
        bodyText = sanitizeBodyText(contentType, bodyText);
        BodySnippet snippet = snippet(bodyText, maxBytes);
        out.put("truncated", snippet.truncated() || cachingResponse.isTruncated());
        out.put("body", snippet.text());
        out.put("status", response.getStatus());
        return out;
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

    private static String sanitizeUrlEncodedBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) return bodyText;
        String[] pairs = bodyText.split("&");
        StringBuilder out = new StringBuilder();
        for (String pair : pairs) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            String k = idx >= 0 ? pair.substring(0, idx) : pair;
            String v = idx >= 0 ? pair.substring(idx + 1) : "";
            String key = urlDecodeSafe(k).toLowerCase();
            boolean sensitive = isSensitiveKey(key);
            String val = sensitive ? "***" : urlDecodeSafe(v);
            if (out.length() > 0) out.append('&');
            out.append(urlEncodeSafe(k)).append('=').append(urlEncodeSafe(val));
        }
        return out.toString();
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
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode obj = ((ObjectNode) node).deepCopy();
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
        if (node.isArray()) {
            ArrayNode arr = ((ArrayNode) node).deepCopy();
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, maskJsonNode(arr.get(i)));
            }
            return arr;
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

    private static BodySnippet snippet(String text, int maxBytes) {
        if (text == null) return new BodySnippet(null, false);
        if (maxBytes <= 0) return new BodySnippet("", true);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return new BodySnippet(text, false);
        int end = Math.min(text.length(), Math.max(0, maxBytes));
        String cut = text.substring(0, end);
        while (cut.getBytes(StandardCharsets.UTF_8).length > maxBytes && cut.length() > 0) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return new BodySnippet(cut, true);
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
        public void write(byte[] b, int off, int len) throws IOException {
            if (delegate != null) delegate.write(b, off, len);
            if (b == null || len <= 0) return;
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
