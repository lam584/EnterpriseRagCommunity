package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileAssetExtractionEpubMobiImagesTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.upload.root", () -> tempDir.resolve("uploads").toString());
        r.add("app.upload.url-prefix", () -> "/uploads");
        r.add("app.file-extraction.pdf-render.max-pages", () -> "2");
        r.add("app.file-extraction.pdf-render.dpi", () -> "96");
    }

    @Autowired
    FileAssetsRepository fileAssetsRepository;

    @Autowired
    FileAssetExtractionsRepository fileAssetExtractionsRepository;

    @Autowired
    FileAssetExtractionAsyncService fileAssetExtractionAsyncService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppSettingsService appSettingsService;

    @Test
    void epubWithPngEntry_shouldExtractImageAndPlaceholders() throws Exception {
        appSettingsService.upsertString(
                DerivedUploadStorageService.KEY_DERIVED_IMAGES_BUDGET_JSON,
                "{\"maxCount\":200,\"maxImageBytes\":10485760,\"maxTotalBytes\":104857600}"
        );
        byte[] epub = zipBytes(List.of(
                Map.entry("OEBPS/images/a.png", tinyPngBytes()),
                Map.entry("OEBPS/text/ch1.xhtml", "<html><body>hi</body></html>")
        ));

        Path epubPath = tempDir.resolve("sample.epub");
        Files.write(epubPath, epub);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(epubPath.toString());
        fa.setUrl("/uploads/sample.epub");
        fa.setOriginalName("sample.epub");
        fa.setSizeBytes(Files.size(epubPath));
        fa.setMimeType("application/epub+zip");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
        assertEquals("EPUB_ZIP", String.valueOf(meta.get("imagesExtractionMode")), e.getExtractedMetadataJson());
        assertTrue(meta.get("extractedImages") instanceof List<?>);
        assertEquals(1, ((List<?>) meta.get("extractedImages")).size());
        assertTrue(e.getExtractedText().contains("[[IMAGE_1]]"));
    }

    @Test
    void mobiWithEmbeddedPng_shouldExtractImageAndPlaceholders() throws Exception {
        appSettingsService.upsertString(
                DerivedUploadStorageService.KEY_DERIVED_IMAGES_BUDGET_JSON,
                "{\"maxCount\":200,\"maxImageBytes\":10485760,\"maxTotalBytes\":104857600}"
        );
        byte[] mobi = zipBytes(List.of(
                Map.entry("img.png", tinyPngBytes()),
                Map.entry("readme.txt", "hello")
        ));

        Path mobiPath = tempDir.resolve("sample.mobi");
        Files.write(mobiPath, mobi);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(mobiPath.toString());
        fa.setUrl("/uploads/sample.mobi");
        fa.setOriginalName("sample.mobi");
        fa.setSizeBytes(Files.size(mobiPath));
        fa.setMimeType("application/x-mobipocket-ebook");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
        String mode = String.valueOf(meta.get("imagesExtractionMode"));
        assertTrue(List.of("MOBI_TIKA_EMBEDDED", "MOBI_ZIP").contains(mode), e.getExtractedMetadataJson());
        assertTrue(meta.get("extractedImages") instanceof List<?>);
        assertEquals(1, ((List<?>) meta.get("extractedImages")).size());
        assertTrue(e.getExtractedText().contains("[[IMAGE_1]]"));
    }

    private FileAssetExtractionsEntity waitExtraction(Long id, FileAssetExtractionStatus status) throws Exception {
        FileAssetExtractionsEntity e = null;
        for (int i = 0; i < 80; i++) {
            e = fileAssetExtractionsRepository.findById(id).orElse(null);
            if (e != null && e.getExtractStatus() == status) return e;
            Thread.sleep(100);
        }
        if (e == null) {
            fail("extraction missing. fileAssetId=" + id);
            return null;
        }
        fail(
                "extraction not ready. fileAssetId=" + id
                        + " status=" + e.getExtractStatus()
                        + " err=" + e.getErrorMessage()
                        + " meta=" + e.getExtractedMetadataJson()
        );
        return e;
    }

    private String randomSha256() {
        return (UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")).substring(0, 64);
    }

    private static byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static byte[] zipBytes(List<Map.Entry<String, Object>> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Object> it : entries) {
                ZipEntry ze = new ZipEntry(it.getKey());
                zos.putNextEntry(ze);
                Object v = it.getValue();
                if (v instanceof byte[] b) zos.write(b);
                else if (v instanceof String s) zos.write(s.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
        }
        return baos.toByteArray();
    }
}
