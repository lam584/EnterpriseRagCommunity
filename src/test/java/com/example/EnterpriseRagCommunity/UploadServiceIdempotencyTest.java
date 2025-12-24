package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadResultDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UploadServiceIdempotencyTest {

    @Autowired
    private UploadService uploadService;

    /**
     * Repro/guard: uploading the same bytes twice should NOT throw and should return
     * the existing FileAssets record (same id/url).
     */
    @Test
    void uploadSameContentTwice_returnsSameAsset() {
        // Assumption: admin user exists in test DB with this email (mirrors other tests / logs).
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "privacy@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile f1 = new MockMultipartFile("file", "a.txt", "text/plain", content);
        MockMultipartFile f2 = new MockMultipartFile("file", "b.txt", "text/plain", content);

        UploadResultDTO r1 = uploadService.upload(f1);
        UploadResultDTO r2 = uploadService.upload(f2);

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r1.getId());
        assertNotNull(r2.getId());
        assertEquals(r1.getId(), r2.getId(), "Duplicate upload should return same asset id");
        assertEquals(r1.getFileUrl(), r2.getFileUrl(), "Duplicate upload should return same url");

        // The response may keep the caller's original filename; we only assert non-empty.
        assertTrue(r2.getFileName() != null && !r2.getFileName().isBlank());
        assertEquals(r1.getFileSize(), r2.getFileSize());
    }
}

