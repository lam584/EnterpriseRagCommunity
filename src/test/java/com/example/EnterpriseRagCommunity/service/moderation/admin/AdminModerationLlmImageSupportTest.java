package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmImageUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmImageSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void clampVisionMaxImages_shouldClampBoundaries() {
        AdminModerationLlmImageSupport support = newSupport(mock(ModerationQueueRepository.class), mock(PostAttachmentsRepository.class), mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class), null);
        assertEquals(10, support.clampVisionMaxImages(null));
        assertEquals(1, support.clampVisionMaxImages(0));
        assertEquals(1, support.clampVisionMaxImages(-5));
        assertEquals(50, support.clampVisionMaxImages(99));
        assertEquals(12, support.clampVisionMaxImages(12));
    }

    @Test
    void isLikelyImageUrl_shouldMatchExpectedForms() {
        assertFalse(AdminModerationLlmImageSupport.isLikelyImageUrl(null));
        assertTrue(AdminModerationLlmImageSupport.isLikelyImageUrl("/uploads/a.bin"));
        assertTrue(AdminModerationLlmImageSupport.isLikelyImageUrl("A.PNG"));
        assertTrue(AdminModerationLlmImageSupport.isLikelyImageUrl("a.bmp"));
        assertTrue(AdminModerationLlmImageSupport.isLikelyImageUrl("a.svg"));
        assertFalse(AdminModerationLlmImageSupport.isLikelyImageUrl("a.txt"));
    }

    @Test
    void resolveImages_shouldPreferRequestInputsWithFilteringDedupAndCap() {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        LlmModerationTestRequest.ImageInput valid1 = imageInput(1L, "/uploads/x.png", "image/png");
        LlmModerationTestRequest.ImageInput duplicate = imageInput(2L, "/uploads/x.png", "image/png");
        LlmModerationTestRequest.ImageInput valid2 = imageInput(3L, "https://a/b.jpg", "");
        LlmModerationTestRequest.ImageInput invalid = imageInput(4L, "https://a/b.txt", "text/plain");

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setImages(java.util.Arrays.asList(null, imageInput(null, " ", "image/png"), invalid, valid1, duplicate, valid2));

        List<ImageRef> out = support.resolveImages(req, 1);
        assertEquals(1, out.size());
        assertEquals("/uploads/x.png", out.get(0).url());
        assertEquals(1L, out.get(0).fileAssetId());
        assertEquals(List.of(), support.resolveImages(null, 5));
    }

    @Test
    void resolveImages_shouldFallbackToAttachmentsAndDerivedImages() {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(11L);
        queue.setContentType(ContentType.POST);
        queue.setContentId(99L);
        when(queueRepository.findById(11L)).thenReturn(Optional.of(queue));

        PostAttachmentsEntity nullAttachment = null;
        PostAttachmentsEntity noAsset = new PostAttachmentsEntity();
        noAsset.setFileAssetId(100L);

        PostAttachmentsEntity nonImageAttachment = new PostAttachmentsEntity();
        nonImageAttachment.setFileAssetId(101L);
        FileAssetsEntity nonImage = new FileAssetsEntity();
        nonImage.setUrl("/uploads/a.pdf");
        nonImage.setMimeType("application/pdf");
        nonImageAttachment.setFileAsset(nonImage);

        PostAttachmentsEntity imageAttachment = new PostAttachmentsEntity();
        imageAttachment.setFileAssetId(102L);
        FileAssetsEntity image = new FileAssetsEntity();
        image.setUrl("/uploads/img-1.png");
        image.setMimeType("image/png");
        imageAttachment.setFileAsset(image);

        when(postAttachmentsRepository.findByPostId(eq(99L), any())).thenReturn(
                new PageImpl<>(java.util.Arrays.asList(nullAttachment, noAsset, nonImageAttachment, imageAttachment))
        );

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(100L);
        ex.setExtractedMetadataJson("{\"extractedImages\":[{\"url\":\"/uploads/derived-1.png\",\"mimeType\":\"image/png\"},{\"url\":\"/uploads/derived-1.png\",\"mimeType\":\"image/png\"},{\"url\":\"/uploads/skip.txt\",\"mimeType\":\"text/plain\"}]}");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(ex, null));

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(11L);
        List<ImageRef> out = support.resolveImages(req, 3);

        assertEquals(3, out.size());
        assertEquals("/uploads/img-1.png", out.get(0).url());
        assertEquals("/uploads/derived-1.png", out.get(1).url());
        assertEquals("/uploads/skip.txt", out.get(2).url());
    }

    @Test
    void resolveImages_shouldReturnEmptyForInvalidQueueAndExceptions() {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(8L);
        assertEquals(List.of(), support.resolveImages(req, 10));

        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(8L);
        queue.setContentType(ContentType.COMMENT);
        queue.setContentId(66L);
        when(queueRepository.findById(8L)).thenReturn(Optional.of(queue));
        assertEquals(List.of(), support.resolveImages(req, 10));

        queue.setContentType(ContentType.POST);
        when(postAttachmentsRepository.findByPostId(eq(66L), any())).thenThrow(new RuntimeException("boom"));
        assertEquals(List.of(), support.resolveImages(req, 10));
    }

    @Test
    void resolveImages_shouldReturnEmptyWhenAttachmentPageIsNullOrContentNull() {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(66L);
        queue.setContentType(ContentType.POST);
        queue.setContentId(101L);
        when(queueRepository.findById(66L)).thenReturn(Optional.of(queue));

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(66L);

        when(postAttachmentsRepository.findByPostId(eq(101L), any())).thenReturn(null);
        assertEquals(List.of(), support.resolveImages(req, 10));

        Page<PostAttachmentsEntity> nullContentPage = mock(Page.class);
        when(nullContentPage.getContent()).thenReturn(null);
        when(postAttachmentsRepository.findByPostId(eq(101L), any())).thenReturn(nullContentPage);
        assertEquals(List.of(), support.resolveImages(req, 10));
    }

    @Test
    void tryExtractDerivedImages_shouldHandleEdgeCasesAndLimits() {
        AdminModerationLlmImageSupport support = newSupport(mock(ModerationQueueRepository.class), mock(PostAttachmentsRepository.class), mock(FileAssetsRepository.class), mock(FileAssetExtractionsRepository.class), null);
        assertEquals(List.of(), support.tryExtractDerivedImages(1L, null, 5));
        assertEquals(List.of(), support.tryExtractDerivedImages(1L, " ", 5));
        assertEquals(List.of(), support.tryExtractDerivedImages(1L, "null", 5));
        assertEquals(List.of(), support.tryExtractDerivedImages(1L, "{}", 5));
        assertEquals(List.of(), support.tryExtractDerivedImages(1L, "{\"extractedImages\":[]}", 5));
        assertEquals(List.of(), support.tryExtractDerivedImages(1L, "{", 5));

        String json = "{\"extractedImages\":[{\"url\":\"  /uploads/a.png  \",\"mime\":123},{\"url\":\"/uploads/bad.txt\",\"mimeType\":\"text/plain\"},{\"url\":\"/uploads/b.jpg\"}]}";
        List<ImageRef> out = support.tryExtractDerivedImages(7L, json, 1);
        assertEquals(1, out.size());
        assertEquals(7L, out.get(0).fileAssetId());
        assertEquals("/uploads/a.png", out.get(0).url());
        assertEquals("123", out.get(0).mimeType());
    }

    @Test
    void encodeImageUrlForUpstream_shouldHandlePassThroughAndUploaderAndFallbacks() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        LlmImageUploadService uploader = mock(LlmImageUploadService.class);

        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, uploader);
        Path uploadRoot = tempDir.resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("x"));
        Path localImage = uploadRoot.resolve("x/a.png");
        Files.write(localImage, tinyPngBytes());
        setUploadConfig(support, uploadRoot.toString(), "/uploads");

        assertNull(support.encodeImageUrlForUpstream(null));
        assertNull(support.encodeImageUrlForUpstream(new ImageRef(null, " ", "image/png")));
        assertEquals("http://a/b.png", support.encodeImageUrlForUpstream(new ImageRef(null, "http://a/b.png", "image/png")));
        assertEquals("https://a/b.png", support.encodeImageUrlForUpstream(new ImageRef(null, "https://a/b.png", "image/png")));
        assertEquals("oss://bucket/a.png", support.encodeImageUrlForUpstream(new ImageRef(null, "oss://bucket/a.png", "image/png")));

        String shortData = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertEquals(shortData, support.encodeImageUrlForUpstream(new ImageRef(null, shortData, "image/png")));
        String veryLongData = "data:image/png;base64," + "A".repeat(250_100);
        assertNull(support.encodeImageUrlForUpstream(new ImageRef(null, veryLongData, "image/png")));

        when(uploader.resolveImageUrl(eq("/uploads/x/a.png"), eq("image/png"), eq("m1"))).thenReturn("https://cdn/u/a.png");
        assertEquals("https://cdn/u/a.png", support.encodeImageUrlForUpstream(new ImageRef(null, "/uploads/x/a.png", "image/png"), "m1"));

        when(uploader.resolveImageUrl(eq("/uploads/x/a.png"), eq("image/png"), eq("m2"))).thenThrow(new RuntimeException("upstream-fail"));
        String dataUrl = support.encodeImageUrlForUpstream(new ImageRef(null, "/uploads/x/a.png", "image/png"), "m2");
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));

        assertEquals("/uploads/not-found.png", support.encodeImageUrlForUpstream(new ImageRef(null, "/uploads/not-found.png", "image/png")));

        Path nonImagePath = uploadRoot.resolve("x/t.bin");
        Files.write(nonImagePath, new byte[]{8, 9, 10});
        assertEquals("/uploads/x/t.bin", support.encodeImageUrlForUpstream(new ImageRef(null, "/uploads/x/t.bin", " ")));

        Path hugePath = uploadRoot.resolve("x/huge.bin");
        Files.write(hugePath, new byte[4_100_000]);
        assertEquals("/uploads/x/huge.bin", support.encodeImageUrlForUpstream(new ImageRef(null, "/uploads/x/huge.bin", "image/png")));

        Long fileAssetId = 77L;
        FileAssetsEntity fileAsset = new FileAssetsEntity();
        fileAsset.setId(fileAssetId);
        fileAsset.setMimeType("image/png");
        fileAsset.setPath(localImage.toString());
        when(fileAssetsRepository.findById(fileAssetId)).thenReturn(Optional.of(fileAsset));
        when(uploader.resolveImageUrl(eq("/outside/a.png"), eq("image/png"), eq("m3"))).thenThrow(new RuntimeException("x"));
        String byAsset = support.encodeImageUrlForUpstream(new ImageRef(fileAssetId, "/outside/a.png", null), "m3");
        assertTrue(byAsset.startsWith("data:image/png;base64,"));
    }

    @Test
    void encodeImageUrlForUpstream_shouldFallbackToOriginalWhenDataUrlStillTooLong() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        LlmImageUploadService uploader = mock(LlmImageUploadService.class);
        when(uploader.resolveImageUrl(org.mockito.ArgumentMatchers.<String>any(), any(), any()))
                .thenThrow(new RuntimeException("x"));

        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, uploader);
        Path uploadRoot = tempDir.resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("x"));
        Path localImage = uploadRoot.resolve("x/big.png");
        Files.write(localImage, tinyPngBytes());
        setUploadConfig(support, uploadRoot.toString(), "/uploads");

        String hugeMime = "image/" + "x".repeat(260_000);
        String encoded = support.encodeImageUrlForUpstream(new ImageRef(null, "/uploads/x/big.png", hugeMime), "m4");
        assertTrue(encoded.startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void encodeImageUrlForUpstream_shouldDegradeToBase64WhenUploaderUnavailable() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        Path uploadRoot = tempDir.resolve("uploads-null-uploader").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("x"));
        Path localImage = uploadRoot.resolve("x/a.png");
        Files.write(localImage, tinyPngBytes());
        setUploadConfig(support, uploadRoot.toString(), "/uploads");

        Long fileAssetId = 123L;
        FileAssetsEntity fileAsset = new FileAssetsEntity();
        fileAsset.setId(fileAssetId);
        fileAsset.setMimeType("image/png");
        when(fileAssetsRepository.findById(fileAssetId)).thenReturn(Optional.of(fileAsset));

        String out = support.encodeImageUrlForUpstream(new ImageRef(fileAssetId, "/uploads/x/a.png", null), "qwen");
        assertNotNull(out);
        assertTrue(out.startsWith("data:image/png;base64,"));
    }

    @Test
    void tokenAndSizeHelpers_shouldCoverModelAndImageBranches() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        assertEquals(32, AdminModerationLlmImageSupport.tokenSideForModel(null));
        assertEquals(32, AdminModerationLlmImageSupport.tokenSideForModel(" "));
        assertEquals(28, AdminModerationLlmImageSupport.tokenSideForModel("qvq-max"));
        assertEquals(28, AdminModerationLlmImageSupport.tokenSideForModel("QWEN2.5-VL-7B"));
        assertEquals(32, AdminModerationLlmImageSupport.tokenSideForModel("other"));

        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(tinyPngBytes());
        ImageSize size = support.tryResolveImageSize(new ImageRef(null, data, "image/png"));
        assertNotNull(size);
        assertTrue(size.width() > 0);
        assertTrue(size.height() > 0);
        assertNull(support.tryResolveImageSize(new ImageRef(null, "data:image/png;base64,", "image/png")));
        assertNull(support.tryResolveImageSize(new ImageRef(null, "data:image/png;base64,@@@", "image/png")));
        assertNull(support.tryResolveImageSize(new ImageRef(null, "https://a/b.png", "image/png")));

        Path uploadRoot = tempDir.resolve("uploads2").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("d"));
        Path localImage = uploadRoot.resolve("d/c.png");
        Files.write(localImage, tinyPngBytes());
        setUploadConfig(support, uploadRoot.toString(), "/uploads");
        ImageSize local = support.tryResolveImageSize(new ImageRef(null, "/uploads/d/c.png", "image/png"));
        assertNotNull(local);

        int noSize = support.estimateVisionImageTokens(new ImageRef(null, "https://a/b.png", "image/png"), "qvq", true, null);
        assertEquals(16386, noSize);

        int lowPixels = support.estimateVisionImageTokens(new ImageRef(null, data, "image/png"), "other", false, 100);
        assertTrue(lowPixels >= 4);

        int maxPixelsBranch = support.estimateVisionImageTokens(new ImageRef(null, data, "image/png"), "other", false, 2048);
        assertTrue(maxPixelsBranch >= 4);
    }

    @Test
    void privateHelpers_shouldCoverStaticBranchesAndErrors() throws Exception {
        byte[] png = tinyPngBytes();

        Method toDataUrl = AdminModerationLlmImageSupport.class.getDeclaredMethod("toDataUrl", byte[].class, String.class);
        toDataUrl.setAccessible(true);
        assertNull(toDataUrl.invoke(null, null, "image/png"));
        String octData = (String) toDataUrl.invoke(null, new byte[]{1}, " ");
        assertTrue(octData.startsWith("data:application/octet-stream;base64,"));

        Method trimBytes = AdminModerationLlmImageSupport.class.getDeclaredMethod("trimBytes", byte[].class, int.class);
        trimBytes.setAccessible(true);
        assertNull(trimBytes.invoke(null, null, 1));
        byte[] src = new byte[]{1, 2, 3, 4};
        assertArrayEquals(src, (byte[]) trimBytes.invoke(null, src, 10));
        assertArrayEquals(new byte[]{1}, (byte[]) trimBytes.invoke(null, src, -1));

        Method toRgb = AdminModerationLlmImageSupport.class.getDeclaredMethod("toRgb", BufferedImage.class);
        toRgb.setAccessible(true);
        assertNull(toRgb.invoke(null, new Object[]{null}));
        BufferedImage rgb = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        assertEquals(rgb, toRgb.invoke(null, rgb));
        BufferedImage argb = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage converted = (BufferedImage) toRgb.invoke(null, argb);
        assertNotNull(converted);
        assertEquals(BufferedImage.TYPE_INT_RGB, converted.getType());

        Method resize = AdminModerationLlmImageSupport.class.getDeclaredMethod("resizeToMaxSide", BufferedImage.class, int.class);
        resize.setAccessible(true);
        assertNull(resize.invoke(null, new Object[]{null, 10}));
        assertEquals(rgb, resize.invoke(null, rgb, 100));
        BufferedImage resized = (BufferedImage) resize.invoke(null, new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB), 20);
        assertEquals(20, Math.max(resized.getWidth(), resized.getHeight()));

        Method writeJpeg = AdminModerationLlmImageSupport.class.getDeclaredMethod("writeJpeg", BufferedImage.class, float.class);
        writeJpeg.setAccessible(true);
        assertNull(writeJpeg.invoke(null, new Object[]{null, 0.9f}));
        byte[] out = (byte[]) writeJpeg.invoke(null, new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), 0.9f);
        assertNotNull(out);
        assertTrue(out.length > 0);
        byte[] failed = (byte[]) writeJpeg.invoke(null, new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), Float.NaN);
        assertNotNull(failed);
        assertTrue(failed.length > 0);

        Method compress = AdminModerationLlmImageSupport.class.getDeclaredMethod("tryResizeAndCompressToJpeg", byte[].class, int.class);
        compress.setAccessible(true);
        assertNull(compress.invoke(null, new Object[]{null, 1000}));
        assertNull(compress.invoke(null, new byte[]{1, 2, 3}, 1000));
        byte[] compressed = (byte[]) compress.invoke(null, png, 1024);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);
    }

    @Test
    void readLocalUploadBytes_shouldHandlePathEscapeFileAssetFallbackAndCatch() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        AdminModerationLlmImageSupport support = newSupport(queueRepository, postAttachmentsRepository, fileAssetsRepository, fileAssetExtractionsRepository, null);

        Path uploadRoot = tempDir.resolve("uploads3").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("ok"));
        Path localImage = uploadRoot.resolve("ok/x.png");
        Files.write(localImage, tinyPngBytes());
        setUploadConfig(support, uploadRoot.toString(), "/uploads");

        Method readLocal = AdminModerationLlmImageSupport.class.getDeclaredMethod("readLocalUploadBytes", Long.class, String.class);
        readLocal.setAccessible(true);

        byte[] ok = (byte[]) readLocal.invoke(support, null, "/uploads/ok/x.png?version=1");
        assertNotNull(ok);
        assertTrue(ok.length > 0);

        assertNull(readLocal.invoke(support, null, "/uploads/../evil.png"));

        Long fileAssetId = 912L;
        FileAssetsEntity fileAsset = new FileAssetsEntity();
        fileAsset.setId(fileAssetId);
        fileAsset.setPath(localImage.toString());
        when(fileAssetsRepository.findById(fileAssetId)).thenReturn(Optional.of(fileAsset));
        byte[] byAsset = (byte[]) readLocal.invoke(support, fileAssetId, "/not/uploads/x.png");
        assertNotNull(byAsset);

        setUploadConfig(support, "\0", "/uploads");
        assertNull(readLocal.invoke(support, null, "/uploads/ok/x.png"));
    }

    private static LlmModerationTestRequest.ImageInput imageInput(Long fileAssetId, String url, String mimeType) {
        LlmModerationTestRequest.ImageInput input = new LlmModerationTestRequest.ImageInput();
        input.setFileAssetId(fileAssetId);
        input.setUrl(url);
        input.setMimeType(mimeType);
        return input;
    }

    private static AdminModerationLlmImageSupport newSupport(
            ModerationQueueRepository queueRepository,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetsRepository fileAssetsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository,
            LlmImageUploadService llmImageUploadService
    ) {
        AdminModerationLlmImageSupport support = new AdminModerationLlmImageSupport(
                queueRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                llmImageUploadService
        );
        try {
            setUploadConfig(support, "uploads", "/uploads");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return support;
    }

    private static void setUploadConfig(AdminModerationLlmImageSupport support, String uploadRoot, String urlPrefix) throws Exception {
        Field uploadRootField = AdminModerationLlmImageSupport.class.getDeclaredField("uploadRoot");
        uploadRootField.setAccessible(true);
        uploadRootField.set(support, uploadRoot);
        Field urlPrefixField = AdminModerationLlmImageSupport.class.getDeclaredField("urlPrefix");
        urlPrefixField.setAccessible(true);
        urlPrefixField.set(support, urlPrefix);
    }

    private static byte[] tinyPngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "png", bos));
            return bos.toByteArray();
        }
    }
}
