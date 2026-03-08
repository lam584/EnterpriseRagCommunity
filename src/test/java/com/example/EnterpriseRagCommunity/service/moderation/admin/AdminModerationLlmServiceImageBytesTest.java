package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceImageBytesTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadDerivedImageBytesByUrlEvenWhenFileAssetIdIsNonImage() throws Exception {
        Path uploadRoot = tempDir.resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("derived-images/2026/02"));

        byte[] png = tinyPngBytes();
        Path derivedPng = uploadRoot.resolve("derived-images/2026/02/test.png");
        Files.write(derivedPng, png);

        Path pptx = tempDir.resolve("sample.pptx");
        Files.write(pptx, new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});

        Long fileAssetId = 5L;
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(pptx.toString());
        fa.setUrl("/uploads/2026/02/sample.pptx");
        fa.setOriginalName("sample.pptx");
        fa.setSizeBytes(Files.size(pptx));
        fa.setMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        fa.setSha256("3333333333333333333333333333333333333333333333333333333333333333");
        fa.setStatus(FileAssetStatus.READY);
        fa.setId(fileAssetId);

        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository attRepo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        when(fileAssetsRepo.findById(fileAssetId)).thenReturn(Optional.of(fa));

        AdminModerationLlmImageSupport imageSupport = new AdminModerationLlmImageSupport(
                queueRepo,
                attRepo,
                fileAssetsRepo,
                fileAssetExtractionsRepository,
                null
        );

        Field uploadRootField = AdminModerationLlmImageSupport.class.getDeclaredField("uploadRoot");
        uploadRootField.setAccessible(true);
        uploadRootField.set(imageSupport, uploadRoot.toString());

        Field urlPrefixField = AdminModerationLlmImageSupport.class.getDeclaredField("urlPrefix");
        urlPrefixField.setAccessible(true);
        urlPrefixField.set(imageSupport, "/uploads");

        ImageRef img = new ImageRef(fileAssetId, "/uploads/derived-images/2026/02/test.png", "image/png");
        String dataUrl = imageSupport.encodeImageUrlForUpstream(img);
        assertNotNull(dataUrl);
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));

        String b64 = dataUrl.substring("data:image/png;base64,".length());
        byte[] out = Base64.getDecoder().decode(b64);
        assertNotNull(out);
        assertTrue(out.length > 8);
        assertEquals((byte) 0x89, out[0]);
        assertEquals((byte) 0x50, out[1]);
        assertEquals((byte) 0x4E, out[2]);
        assertEquals((byte) 0x47, out[3]);
        assertFalse(out[0] == 'P' && out[1] == 'K');
    }

    private static byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(img, "png", bos));
            return bos.toByteArray();
        }
    }
}

