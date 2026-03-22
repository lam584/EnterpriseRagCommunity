package com.example.EnterpriseRagCommunity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ClientIpResolver {

    @Value("${app.security.client-ip.trust-forward-headers:false}")
    private boolean trustForwardHeaders;

    @Value("${app.security.client-ip.trusted-proxies:}")
    private String trustedProxiesRaw;

    private volatile String cachedTrustedProxiesRaw = null;
    private volatile List<IpMatcher> cachedTrustedMatchers = Collections.emptyList();

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (!trustForwardHeaders) return remoteAddr;
        if (!isTrustedProxy(remoteAddr)) return remoteAddr;

        String forwarded = request.getHeader("Forwarded");
        String fromForwarded = parseForwardedFor(forwarded);
        if (fromForwarded != null) return fromForwarded;

        String xff = request.getHeader("X-Forwarded-For");
        String fromXff = parseXForwardedFor(xff);
        if (fromXff != null) return fromXff;

        String xri = normalizeIp(request.getHeader("X-Real-IP"));
        if (xri != null) return xri;
        return remoteAddr;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) return false;
        InetAddress addr = parseInetAddress(ip);
        if (addr == null) return false;
        List<IpMatcher> matchers = getTrustedMatchers();
        if (matchers.isEmpty()) return false;
        for (IpMatcher matcher : matchers) {
            if (matcher.matches(addr)) return true;
        }
        return false;
    }

    private List<IpMatcher> getTrustedMatchers() {
        String raw = trustedProxiesRaw == null ? "" : trustedProxiesRaw.trim();
        String cachedRaw = cachedTrustedProxiesRaw;
        if (raw.equals(cachedRaw)) return cachedTrustedMatchers;
        synchronized (this) {
            raw = trustedProxiesRaw == null ? "" : trustedProxiesRaw.trim();
            if (raw.equals(cachedTrustedProxiesRaw)) return cachedTrustedMatchers;
            cachedTrustedMatchers = parseMatchers(raw);
            cachedTrustedProxiesRaw = raw;
            return cachedTrustedMatchers;
        }
    }

    private static List<IpMatcher> parseMatchers(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        String[] parts = raw.split("[,\\s]+");
        List<IpMatcher> matchers = new ArrayList<>();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            IpMatcher matcher = parseMatcher(p.trim());
            if (matcher != null) matchers.add(matcher);
        }
        return matchers.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(matchers);
    }

    private static IpMatcher parseMatcher(String raw) {
        int slash = raw.indexOf('/');
        if (slash <= 0 || slash >= raw.length() - 1) {
            InetAddress exact = parseInetAddress(raw);
            if (exact == null) return null;
            byte[] exactBytes = exact.getAddress();
            return addr -> java.util.Arrays.equals(exactBytes, addr.getAddress());
        }
        String ipPart = raw.substring(0, slash).trim();
        String prefixPart = raw.substring(slash + 1).trim();
        InetAddress networkAddress = parseInetAddress(ipPart);
        if (networkAddress == null) return null;
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(prefixPart);
        } catch (NumberFormatException ignored) {
            return null;
        }
        byte[] networkBytes = networkAddress.getAddress();
        int maxBits = networkBytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxBits) return null;
        return addr -> matchCidr(networkBytes, prefixLength, addr.getAddress());
    }

    private static boolean matchCidr(byte[] network, int prefixLength, byte[] ip) {
        if (network == null || ip == null) return false;
        if (network.length != ip.length) return false;
        int fullBytes = prefixLength / 8;
        int restBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (network[i] != ip[i]) return false;
        }
        if (restBits == 0) return true;
        int mask = (0xFF << (8 - restBits)) & 0xFF;
        return (network[fullBytes] & mask) == (ip[fullBytes] & mask);
    }

    private static InetAddress parseInetAddress(String raw) {
        try {
            String normalized = normalizeIp(raw);
            if (normalized == null) return null;
            return InetAddress.getByName(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String parseForwardedFor(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) return null;
        String[] parts = forwarded.split(";");
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (!t.regionMatches(true, 0, "for=", 0, 4)) continue;
            String v = t.substring(4).trim();
            String ip = normalizeIp(stripQuotes(v));
            if (ip != null) return ip;
        }
        return null;
    }

    private static String parseXForwardedFor(String xff) {
        if (xff == null || xff.isBlank()) return null;
        String[] parts = xff.split(",");
        if (parts.length == 0) return null;
        return normalizeIp(parts[0]);
    }

    private static String normalizeIp(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isBlank()) return null;
        v = stripQuotes(v);
        if (v.startsWith("[")) {
            int end = v.indexOf(']');
            if (end > 0) return v.substring(1, end).trim();
        }
        if (v.indexOf(':') > 0 && v.indexOf('.') >= 0 && v.chars().filter(ch -> ch == ':').count() == 1) {
            return v.substring(0, v.lastIndexOf(':')).trim();
        }
        return v;
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    @FunctionalInterface
    private interface IpMatcher {
        boolean matches(InetAddress address);
    }
}
