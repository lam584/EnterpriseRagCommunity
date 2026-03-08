package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmSecretsCryptoServiceTest {

    @Test
    void isConfigured_delegatesToTotpCryptoService() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        when(crypto.isConfigured()).thenReturn(true);

        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertEquals(true, svc.isConfigured());
    }

    @Test
    void encryptStringOrNull_returnsNullForNullOrBlankInput() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertNull(svc.encryptStringOrNull(null));
        assertNull(svc.encryptStringOrNull("   "));
    }

    @Test
    void encryptStringOrNull_trimsAndEncryptsNonBlankText() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        byte[] encrypted = new byte[]{1, 2, 3};
        when(crypto.encrypt(any())).thenReturn(encrypted);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        byte[] out = svc.encryptStringOrNull("  token-value  ");

        assertSame(encrypted, out);
        verify(crypto).encrypt("token-value".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decryptStringOrNull_returnsNullForNullOrEmptyPayload() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertNull(svc.decryptStringOrNull(null));
        assertNull(svc.decryptStringOrNull(new byte[0]));
    }

    @Test
    void decryptStringOrNull_returnsNullWhenPlaintextIsBlank() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        byte[] payload = new byte[]{9};
        when(crypto.decrypt(payload)).thenReturn("   ".getBytes(StandardCharsets.UTF_8));
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertNull(svc.decryptStringOrNull(payload));
    }

    @Test
    void decryptStringOrNull_returnsDecodedTextWhenNonBlank() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        byte[] payload = new byte[]{7};
        when(crypto.decrypt(payload)).thenReturn("abc".getBytes(StandardCharsets.UTF_8));
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertEquals("abc", svc.decryptStringOrNull(payload));
    }

    @Test
    void encryptHeadersOrNull_returnsNullForNullOrEmptyMap() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertNull(svc.encryptHeadersOrNull(null));
        assertNull(svc.encryptHeadersOrNull(Map.of()));
    }

    @Test
    void encryptHeadersOrNull_serializesAndEncryptsHeaders() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        ObjectMapper mapper = new ObjectMapper();
        byte[] encrypted = new byte[]{8, 8};
        when(crypto.encrypt(any())).thenReturn(encrypted);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, mapper);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("k1", "v1");
        headers.put("k2", "v2");

        byte[] out = svc.encryptHeadersOrNull(headers);
        byte[] expectedJson = assertDoesNotThrow(() -> mapper.writeValueAsBytes(headers));

        assertSame(encrypted, out);
        verify(crypto).encrypt(org.mockito.ArgumentMatchers.argThat(v -> Arrays.equals(v, expectedJson)));
    }

    @Test
    void encryptHeadersOrNull_wrapsSerializationErrors() throws Exception {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsBytes(any())).thenThrow(new RuntimeException("boom"));
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, mapper);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.encryptHeadersOrNull(Map.of("k", "v")));

        assertNotNull(ex.getMessage());
        assertEquals(true, ex.getMessage().contains("headers 加密失败"));
    }

    @Test
    void decryptHeadersOrEmpty_returnsEmptyForNullOrEmptyPayload() {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, new ObjectMapper());

        assertEquals(Map.of(), svc.decryptHeadersOrEmpty(null));
        assertEquals(Map.of(), svc.decryptHeadersOrEmpty(new byte[0]));
    }

    @Test
    void decryptHeadersOrEmpty_returnsParsedMapAndFallsBackWhenNull() throws Exception {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        byte[] payload = new byte[]{1};
        byte[] json = new byte[]{2};
        when(crypto.decrypt(payload)).thenReturn(json);
        Map<String, String> parsed = new LinkedHashMap<>();
        parsed.put("k", "v");
        when(mapper.readValue(any(byte[].class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(parsed)
                .thenReturn(null);
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, mapper);

        Map<String, String> out1 = svc.decryptHeadersOrEmpty(payload);
        Map<String, String> out2 = svc.decryptHeadersOrEmpty(payload);

        assertEquals(parsed, out1);
        assertEquals(Map.of(), out2);
    }

    @Test
    void decryptHeadersOrEmpty_wrapsParseErrors() throws Exception {
        TotpCryptoService crypto = mock(TotpCryptoService.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        byte[] payload = new byte[]{3};
        when(crypto.decrypt(payload)).thenReturn(new byte[]{4});
        when(mapper.readValue(any(byte[].class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("bad-json"));
        LlmSecretsCryptoService svc = new LlmSecretsCryptoService(crypto, mapper);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.decryptHeadersOrEmpty(payload));

        assertNotNull(ex.getMessage());
        assertEquals(true, ex.getMessage().contains("headers 解密失败"));
    }

}
