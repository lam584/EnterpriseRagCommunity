package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.config.TotpSecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TotpCryptoService {
    private static final byte VERSION_1 = 1;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TotpSecurityProperties props;

    public TotpCryptoService(TotpSecurityProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        String k = props.getMasterKey();
        return k != null && !k.isBlank();
    }

    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) throw new IllegalArgumentException("plaintext is required");
        SecretKeySpec key = loadKeyOrThrow();

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] payload = new byte[1 + 1 + iv.length + ciphertext.length];
            payload[0] = VERSION_1;
            payload[1] = (byte) iv.length;
            System.arraycopy(iv, 0, payload, 2, iv.length);
            System.arraycopy(ciphertext, 0, payload, 2 + iv.length, ciphertext.length);
            return payload;
        } catch (Exception e) {
            throw new IllegalArgumentException("encrypt failed: " + e.getMessage(), e);
        }
    }

    public byte[] decrypt(byte[] payload) {
        if (payload == null || payload.length < 3) throw new IllegalArgumentException("payload is invalid");
        SecretKeySpec key = loadKeyOrThrow();

        byte version = payload[0];
        if (version != VERSION_1) throw new IllegalArgumentException("unsupported payload version: " + version);

        int ivLen = payload[1] & 0xFF;
        if (ivLen <= 0 || payload.length < 2 + ivLen + 1) throw new IllegalArgumentException("payload iv length is invalid");

        byte[] iv = new byte[ivLen];
        System.arraycopy(payload, 2, iv, 0, ivLen);

        int ctLen = payload.length - 2 - ivLen;
        byte[] ciphertext = new byte[ctLen];
        System.arraycopy(payload, 2 + ivLen, ciphertext, 0, ctLen);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalArgumentException("decrypt failed: " + e.getMessage(), e);
        }
    }

    private SecretKeySpec loadKeyOrThrow() {
        String raw = props.getMasterKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("TOTP master key not configured (app.security.totp.master-key)");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("TOTP master key must be base64 encoded", e);
        }
        if (!(decoded.length == 16 || decoded.length == 24 || decoded.length == 32)) {
            throw new IllegalArgumentException("TOTP master key must be 16/24/32 bytes after base64 decode");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}

