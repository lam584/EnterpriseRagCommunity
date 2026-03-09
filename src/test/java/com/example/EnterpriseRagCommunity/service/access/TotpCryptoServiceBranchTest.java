package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.config.TotpSecurityProperties;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotpCryptoServiceBranchTest {

    @Test
    void crypto_should_encrypt_decrypt_and_handle_invalid_payload() {
        TotpSecurityProperties props = new TotpSecurityProperties();
        props.setMasterKey(Base64.getEncoder().encodeToString("0123456789abcdef".getBytes()));
        TotpCryptoService s = new TotpCryptoService(props, null);

        assertTrue(s.isConfigured());
        byte[] pt = "hello".getBytes();
        byte[] enc = s.encrypt(pt);
        assertTrue(enc.length > 3);
        assertArrayEquals(pt, s.decrypt(enc));

        byte[] badVersion = enc.clone();
        badVersion[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> s.decrypt(badVersion));

        assertThrows(IllegalArgumentException.class, () -> s.decrypt(new byte[]{1, 20, 1}));
        assertThrows(IllegalArgumentException.class, () -> s.encrypt(null));
        assertThrows(IllegalArgumentException.class, () -> s.decrypt(null));
    }

    @Test
    void crypto_should_validate_master_key_source_and_format() {
        TotpSecurityProperties props = new TotpSecurityProperties();
        props.setMasterKey(" ");
        SystemConfigurationService cfg = mock(SystemConfigurationService.class);
        when(cfg.getConfig("app.security.totp.master-key")).thenReturn(" ");
        when(cfg.getConfig("APP_TOTP_MASTER_KEY")).thenReturn("not-base64");
        TotpCryptoService s = new TotpCryptoService(props, cfg);
        assertTrue(s.isConfigured());
        assertThrows(IllegalArgumentException.class, () -> s.encrypt("x".getBytes()));

        when(cfg.getConfig("APP_TOTP_MASTER_KEY")).thenReturn(Base64.getEncoder().encodeToString("short".getBytes()));
        TotpCryptoService s2 = new TotpCryptoService(props, cfg);
        assertTrue(s2.isConfigured());
        assertThrows(IllegalArgumentException.class, () -> s2.decrypt(new byte[]{1, 1, 1, 1}));
    }
}
