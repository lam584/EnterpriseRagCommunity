package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmSecretsCryptoService {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private final TotpCryptoService totpCryptoService;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return totpCryptoService.isConfigured();
    }

    public byte[] encryptStringOrNull(String plaintext) {
        if (plaintext == null) return null;
        String s = plaintext.trim();
        if (s.isEmpty()) return null;
        return totpCryptoService.encrypt(s.getBytes(StandardCharsets.UTF_8));
    }

    public String decryptStringOrNull(byte[] payload) {
        if (payload == null || payload.length == 0) return null;
        byte[] raw = totpCryptoService.decrypt(payload);
        String s = new String(raw, StandardCharsets.UTF_8);
        return s.isBlank() ? null : s;
    }

    public byte[] encryptHeadersOrNull(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return null;
        try {
            byte[] json = objectMapper.writeValueAsBytes(headers);
            return totpCryptoService.encrypt(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("headers 加密失败: " + e.getMessage(), e);
        }
    }

    public Map<String, String> decryptHeadersOrEmpty(byte[] payload) {
        if (payload == null || payload.length == 0) return Map.of();
        byte[] json = totpCryptoService.decrypt(payload);
        try {
            Map<String, String> m = objectMapper.readValue(json, STRING_MAP);
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            throw new IllegalArgumentException("headers 解密失败: " + e.getMessage(), e);
        }
    }
}
