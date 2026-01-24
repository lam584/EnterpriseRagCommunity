package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.config.TotpSecurityProperties;
import com.example.EnterpriseRagCommunity.utils.Base32Codec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class TotpServiceTest {

    @Test
    void rfc6238_vectors_sha1_sha256_sha512() {
        TotpService totp = new TotpService();

        byte[] sha1Key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] sha256Key = "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);
        byte[] sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".getBytes(StandardCharsets.US_ASCII);

        assertEquals("94287082", totp.generateCode(sha1Key, 59L, "SHA1", 8, 30));
        assertEquals("07081804", totp.generateCode(sha1Key, 1111111109L, "SHA1", 8, 30));
        assertEquals("14050471", totp.generateCode(sha1Key, 1111111111L, "SHA1", 8, 30));
        assertEquals("89005924", totp.generateCode(sha1Key, 1234567890L, "SHA1", 8, 30));
        assertEquals("69279037", totp.generateCode(sha1Key, 2000000000L, "SHA1", 8, 30));
        assertEquals("65353130", totp.generateCode(sha1Key, 20000000000L, "SHA1", 8, 30));

        assertEquals("46119246", totp.generateCode(sha256Key, 59L, "SHA256", 8, 30));
        assertEquals("68084774", totp.generateCode(sha256Key, 1111111109L, "SHA256", 8, 30));
        assertEquals("67062674", totp.generateCode(sha256Key, 1111111111L, "SHA256", 8, 30));
        assertEquals("91819424", totp.generateCode(sha256Key, 1234567890L, "SHA256", 8, 30));
        assertEquals("90698825", totp.generateCode(sha256Key, 2000000000L, "SHA256", 8, 30));
        assertEquals("77737706", totp.generateCode(sha256Key, 20000000000L, "SHA256", 8, 30));

        assertEquals("90693936", totp.generateCode(sha512Key, 59L, "SHA512", 8, 30));
        assertEquals("25091201", totp.generateCode(sha512Key, 1111111109L, "SHA512", 8, 30));
        assertEquals("99943326", totp.generateCode(sha512Key, 1111111111L, "SHA512", 8, 30));
        assertEquals("93441116", totp.generateCode(sha512Key, 1234567890L, "SHA512", 8, 30));
        assertEquals("38618901", totp.generateCode(sha512Key, 2000000000L, "SHA512", 8, 30));
        assertEquals("47863826", totp.generateCode(sha512Key, 20000000000L, "SHA512", 8, 30));
    }

    @Test
    void base32_roundtrip() {
        byte[] raw = "hello-totp".getBytes(StandardCharsets.UTF_8);
        String b32 = Base32Codec.encode(raw);
        byte[] decoded = Base32Codec.decode(b32);
        assertArrayEquals(raw, decoded);
    }

    @Test
    void crypto_roundtrip() {
        TotpSecurityProperties props = new TotpSecurityProperties();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        props.setMasterKey(Base64.getEncoder().encodeToString(key));
        TotpCryptoService crypto = new TotpCryptoService(props);

        byte[] raw = "secret-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] enc = crypto.encrypt(raw);
        assertNotNull(enc);
        assertTrue(enc.length > 0);

        byte[] dec = crypto.decrypt(enc);
        assertArrayEquals(raw, dec);
    }
}
