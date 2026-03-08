package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileAssetExtractionEmbeddedImagesTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.upload.root", () -> tempDir.resolve("uploads").toString());
        r.add("app.upload.url-prefix", () -> "/uploads");
        r.add("app.file-extraction.images.max-count", () -> "10");
        r.add("app.file-extraction.images.max-image-bytes", () -> String.valueOf(2 * 1024 * 1024));
        r.add("app.file-extraction.images.max-total-bytes", () -> String.valueOf(10 * 1024 * 1024));
        r.add("app.file-extraction.pdf-render.max-pages", () -> "5");
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

    @Test
    void docxShouldExtractEmbeddedImagesAndPlaceholders() throws Exception {
        byte[] png = tinyPngBytes();

        Path docxPath = tempDir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            var p = doc.createParagraph();
            var r = p.createRun();
            r.setText("hello");
            try (ByteArrayInputStream bis = new ByteArrayInputStream(png)) {
                r.addPicture(bis, Document.PICTURE_TYPE_PNG, "img.png", Units.toEMU(64), Units.toEMU(64));
            }
            try (var os = Files.newOutputStream(docxPath)) {
                doc.write(os);
            }
        }

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(docxPath.toString());
        fa.setUrl("/uploads/sample.docx");
        fa.setOriginalName("sample.docx");
        fa.setSizeBytes(Files.size(docxPath));
        fa.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = null;
        for (int i = 0; i < 50; i++) {
            e = fileAssetExtractionsRepository.findById(fa.getId()).orElse(null);
            if (e != null && e.getExtractStatus() == FileAssetExtractionStatus.READY) break;
            Thread.sleep(100);
        }

        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedMetadataJson());

        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
        Object imgs = meta.get("extractedImages");
        assertTrue(imgs instanceof List<?>);
        assertEquals(1, ((List<?>) imgs).size());

        Map<?, ?> img0 = (Map<?, ?>) ((List<?>) imgs).get(0);
        assertNotNull(img0.get("url"));
        assertTrue(String.valueOf(img0.get("url")).startsWith("/uploads/derived-images/"));
        assertEquals(1, Number.class.isInstance(meta.get("imageCount")) ? ((Number) meta.get("imageCount")).intValue() : Integer.parseInt(String.valueOf(meta.get("imageCount"))));

        assertNotNull(e.getExtractedText());
        assertTrue(e.getExtractedText().contains("[[IMAGE_1]]"));
    }

    @Test
    void docxShouldRespectMaxCountLimit() throws Exception {
        byte[] png = tinyPngBytes();

        Path docxPath = tempDir.resolve("many.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            var p = doc.createParagraph();
            var r = p.createRun();
            r.setText("many");
            for (int i = 0; i < 20; i++) {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(png)) {
                    r.addPicture(bis, Document.PICTURE_TYPE_PNG, "img_" + i + ".png", Units.toEMU(16), Units.toEMU(16));
                }
            }
            try (var os = Files.newOutputStream(docxPath)) {
                doc.write(os);
            }
        }

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(docxPath.toString());
        fa.setUrl("/uploads/many.docx");
        fa.setOriginalName("many.docx");
        fa.setSizeBytes(Files.size(docxPath));
        fa.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = null;
        for (int i = 0; i < 50; i++) {
            e = fileAssetExtractionsRepository.findById(fa.getId()).orElse(null);
            if (e != null && e.getExtractStatus() == FileAssetExtractionStatus.READY) break;
            Thread.sleep(100);
        }
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());

        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
        List<?> images = (List<?>) meta.get("extractedImages");
        assertNotNull(images);
        assertTrue(images.size() <= 10);
        assertEquals(images.size(), Number.class.isInstance(meta.get("imageCount")) ? ((Number) meta.get("imageCount")).intValue() : Integer.parseInt(String.valueOf(meta.get("imageCount"))));
    }

    @Test
    void pptxShouldExtractEmbeddedImages() throws Exception {
        byte[] png = tinyPngBytes();

        Path pptxPath = tempDir.resolve("sample.pptx");
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            var slide = pptx.createSlide();
            var pd = pptx.addPicture(png, PictureData.PictureType.PNG);
            var pic = slide.createPicture(pd);
            pic.setAnchor(new java.awt.Rectangle(0, 0, 64, 64));
            try (var os = Files.newOutputStream(pptxPath)) {
                pptx.write(os);
            }
        }

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(pptxPath.toString());
        fa.setUrl("/uploads/sample.pptx");
        fa.setOriginalName("sample.pptx");
        fa.setSizeBytes(Files.size(pptxPath));
        fa.setMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = null;
        for (int i = 0; i < 50; i++) {
            e = fileAssetExtractionsRepository.findById(fa.getId()).orElse(null);
            if (e != null && e.getExtractStatus() == FileAssetExtractionStatus.READY) break;
            Thread.sleep(100);
        }

        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());

        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
        List<?> images = (List<?>) meta.get("extractedImages");
        assertNotNull(images);
        assertEquals(1, images.size());
        assertEquals(1, Number.class.isInstance(meta.get("imageCount")) ? ((Number) meta.get("imageCount")).intValue() : Integer.parseInt(String.valueOf(meta.get("imageCount"))));
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
}
