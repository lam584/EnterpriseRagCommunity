package com.example.EnterpriseRagCommunity.service.moderation.web;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebContentFetchService {

    @Value("${app.web-extraction.max-urls:20}")
    private int maxUrls;

    @Value("${app.web-extraction.timeout-millis:5000}")
    private int timeoutMillis;

    @Value("${app.web-extraction.max-page-bytes:2097152}")
    private int maxPageBytes;

    @Value("${app.web-extraction.max-page-chars:20000}")
    private int maxPageChars;

    @Value("${app.web-extraction.max-redirects:3}")
    private int maxRedirects;

    @Value("${app.web-extraction.allowed-ports:80,443,8080,8443}")
    private String allowedPorts;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofMillis(5000))
            .build();

    private final Tika tika = new Tika();

    @FunctionalInterface
    interface HostAddressResolver {
        InetAddress[] resolve(String host) throws Exception;
    }

    private HostAddressResolver hostAddressResolver = InetAddress::getAllByName;

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"']+");

    public Map<String, Object> fetchUrlsToMeta(List<String> urls) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        out.put("items", items);

        List<String> list = normalizeUrls(urls, maxUrls);
        out.put("requestedCount", urls == null ? 0 : urls.size());
        out.put("dedupedCount", list.size());

        for (String u : list) {
            if (u.isBlank()) continue;
            Map<String, Object> one = fetchOne(u);
            items.add(one);
        }
        return out;
    }

    public String buildWebBlock(Map<String, Object> meta) {
        if (meta == null) return null;
        Object items = meta.get("items");
        if (!(items instanceof List<?> list) || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("[WEB]\n");
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String url = stringOrNull(m.get("url"));
            String status = stringOrNull(m.get("status"));
            String text = stringOrNull(m.get("text"));
            if (url == null) continue;
            sb.append("- url=").append(url);
            if (status != null) sb.append(" status=").append(status);
            sb.append('\n');
            if (text != null && !text.isBlank()) {
                sb.append(text.trim()).append('\n');
            }
            sb.append('\n');
        }
        String out = sb.toString().trim();
        return out.isBlank() ? null : out;
    }

    public List<String> extractUrls(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher m = URL_PATTERN.matcher(text);
        Set<String> out = new LinkedHashSet<>();
        while (m.find()) {
            String raw = m.group();
            String u = normalizeOneUrl(raw);
            if (u == null) continue;
            out.add(u);
            if (out.size() >= Math.max(1, maxUrls)) break;
        }
        return new ArrayList<>(out);
    }

    private Map<String, Object> fetchOne(String url) {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("url", url);

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            meta.put("status", "INVALID_URL");
            return meta;
        }

        String blocked = validateUrlForFetch(uri);
        if (blocked != null) {
            meta.put("status", "SSRF_BLOCKED");
            meta.put("blockedReason", blocked);
            return meta;
        }

        URI cur = uri;
        for (int i = 0; i <= Math.max(0, maxRedirects); i++) {
            HttpResponse<InputStream> res;
            try {
                HttpRequest req = HttpRequest.newBuilder(cur)
                        .timeout(Duration.ofMillis(Math.max(1000, timeoutMillis)))
                        .header("User-Agent", "EnterpriseRagCommunity/1.0")
                        .GET()
                        .build();
                res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                meta.put("status", "FETCH_ERROR");
                meta.put("error", safeMsg(e));
                return meta;
            }

            int code = res.statusCode();
            meta.put("httpStatus", code);
            if (code >= 300 && code < 400) {
                String loc = res.headers().firstValue("location").orElse(null);
                if (loc == null || loc.isBlank()) {
                    meta.put("status", "REDIRECT_NO_LOCATION");
                    return meta;
                }
                URI next;
                try {
                    next = cur.resolve(loc.trim());
                } catch (Exception e) {
                    meta.put("status", "REDIRECT_INVALID");
                    return meta;
                }
                String blockedNext = validateUrlForFetch(next);
                if (blockedNext != null) {
                    meta.put("status", "SSRF_BLOCKED");
                    meta.put("blockedReason", blockedNext);
                    meta.put("redirectBlockedAt", next.toString());
                    return meta;
                }
                cur = next;
                meta.put("redirectedTo", cur.toString());
                continue;
            }

            if (code < 200 || code >= 300) {
                meta.put("status", "NON_2XX");
                return meta;
            }

            String ct = res.headers().firstValue("content-type").orElse(null);
            if (ct != null) meta.put("contentType", ct);
            if (!isSupportedContentType(ct)) {
                meta.put("status", "UNSUPPORTED_CONTENT_TYPE");
                return meta;
            }

            byte[] bytes;
            try (InputStream body = res.body()) {
                bytes = readLimited(body, Math.max(0, maxPageBytes));
            } catch (Exception e) {
                meta.put("status", "READ_ERROR");
                meta.put("error", safeMsg(e));
                return meta;
            }
            meta.put("bytes", bytes.length);

            String txt;
            try {
                txt = tika.parseToString(new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                txt = new String(bytes, StandardCharsets.UTF_8);
            }
            txt = normalizeText(txt);
            if (txt.length() > Math.max(0, maxPageChars)) {
                txt = txt.substring(0, Math.max(0, maxPageChars));
                meta.put("truncated", true);
                meta.put("truncatedReason", "TEXT_CHAR_LIMIT");
            } else {
                meta.put("truncated", false);
            }
            meta.put("textChars", txt.length());
            meta.put("text", txt);
            meta.put("status", "OK");
            return meta;
        }

        meta.put("status", "REDIRECT_TOO_MANY");
        return meta;
    }

    private String validateUrlForFetch(URI uri) {
        if (uri == null) return "NULL";
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().trim().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return "SCHEME";
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) return "USERINFO";
        String host = uri.getHost();
        if (host == null || host.isBlank()) return "HOST";
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")) return "LOCALHOST";

        int port = uri.getPort();
        if (port != -1 && !isAllowedPort(port)) return "PORT";

        try {
            InetAddress[] addrs = resolveHostAddresses(host);
            for (InetAddress a : addrs) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
                    return "PRIVATE_IP";
                }
            }
        } catch (Exception e) {
            return "DNS";
        }
        return null;
    }

    private InetAddress[] resolveHostAddresses(String host) throws Exception {
        HostAddressResolver resolver = hostAddressResolver == null ? InetAddress::getAllByName : hostAddressResolver;
        return resolver.resolve(host);
    }

    private boolean isAllowedPort(int port) {
        if (port <= 0 || port > 65535) return false;
        Set<Integer> allowed = new LinkedHashSet<>();
        String s = allowedPorts == null ? "" : allowedPorts.trim();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (t.isBlank()) continue;
            try {
                allowed.add(Integer.parseInt(t));
            } catch (Exception ignore) {
            }
        }
        if (allowed.isEmpty()) {
            allowed.add(80);
            allowed.add(443);
        }
        return allowed.contains(port);
    }

    private static boolean isSupportedContentType(String ct) {
        if (ct == null || ct.isBlank()) return true;
        String t = ct.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("text/")) return true;
        return t.startsWith("application/xhtml+xml");
    }

    private static byte[] readLimited(InputStream in, int limitBytes) throws Exception {
        if (limitBytes <= 0) return new byte[0];
        byte[] buf = new byte[8192];
        int total = 0;
        var baos = new java.io.ByteArrayOutputStream();
        while (true) {
            int n = in.read(buf);
            if (n < 0) break;
            int can = Math.min(n, limitBytes - total);
            baos.write(buf, 0, can);
            total += can;
            if (total >= limitBytes) break;
        }
        return baos.toByteArray();
    }

    private static List<String> normalizeUrls(List<String> urls, int max) {
        if (urls == null || urls.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        for (String u : urls) {
            String nu = normalizeOneUrl(u);
            if (nu == null) continue;
            seen.add(nu);
            if (seen.size() >= Math.max(1, max)) break;
        }
        return new ArrayList<>(seen);
    }

    private static String normalizeOneUrl(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (u.isBlank()) return null;
        while (u.endsWith(".") || u.endsWith(",") || u.endsWith(";") || u.endsWith(")") || u.endsWith("]") || u.endsWith("}") || u.endsWith(">")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.toLowerCase(Locale.ROOT).startsWith("http://") && !u.toLowerCase(Locale.ROOT).startsWith("https://")) return null;
        try {
            URI uri = URI.create(u);
            if (uri.getHost() == null || uri.getHost().isBlank()) return null;
            return uri.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeText(String txt) {
        if (txt == null) return "";
        String t = txt.replace('\u0000', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        String s = m.trim();
        if (s.isBlank()) return e.getClass().getSimpleName();
        if (s.length() > 300) s = s.substring(0, 300);
        return s;
    }

    private static String stringOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
