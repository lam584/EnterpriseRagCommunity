package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileAssetExtractionPdfImagesTest {

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

    @Test
    void pdfWithTextNoImages_shouldBeEmbeddedNone() throws Exception {
        Path pdfPath = tempDir.resolve("text.pdf");
        Files.write(pdfPath, pdfTextBytes("hello pdf"));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(pdfPath.toString());
        fa.setUrl("/uploads/text.pdf");
        fa.setOriginalName("text.pdf");
        fa.setSizeBytes(Files.size(pdfPath));
        fa.setMimeType("application/pdf");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertTrue(e.getExtractedText().contains("hello pdf"));
        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
        assertEquals("EMBEDDED_NONE", String.valueOf(meta.get("imagesExtractionMode")));
        assertFalse(e.getExtractedText().contains("[[IMAGE_"));
    }

    @Test
    void blankPdf_shouldRenderAndAppendPlaceholders() throws Exception {
        Path pdfPath = tempDir.resolve("blank.pdf");
        Files.write(pdfPath, pdfBlankBytes());

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(pdfPath.toString());
        fa.setUrl("/uploads/blank.pdf");
        fa.setOriginalName("blank.pdf");
        fa.setSizeBytes(Files.size(pdfPath));
        fa.setMimeType("application/pdf");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
        assertEquals("PDF_RENDER", String.valueOf(meta.get("imagesExtractionMode")));
        assertTrue(e.getExtractedText().contains("[[IMAGE_1]]"));
        assertTrue(meta.get("extractedImages") instanceof List<?>);
        assertTrue(((List<?>) meta.get("extractedImages")).size() >= 1);
    }

    @Test
    void pdfWithXObjectImage_shouldUsePdfXObjectMode() throws Exception {
        byte[] png = tinyPngBytes();
        Path pdfPath = tempDir.resolve("img.pdf");
        Files.write(pdfPath, pdfWithImageBytes(png));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(pdfPath.toString());
        fa.setUrl("/uploads/img.pdf");
        fa.setOriginalName("img.pdf");
        fa.setSizeBytes(Files.size(pdfPath));
        fa.setMimeType("application/pdf");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
        assertEquals("PDF_XOBJECT", String.valueOf(meta.get("imagesExtractionMode")));
        assertTrue(e.getExtractedText().contains("[[IMAGE_1]]"));
        assertTrue(meta.get("extractedImages") instanceof List<?>);
        assertEquals(1, ((List<?>) meta.get("extractedImages")).size());
    }

    private FileAssetExtractionsEntity waitExtraction(Long id, FileAssetExtractionStatus status) throws Exception {
        FileAssetExtractionsEntity e = null;
        for (int i = 0; i < 80; i++) {
            e = fileAssetExtractionsRepository.findById(id).orElse(null);
            if (e != null && e.getExtractStatus() == status) return e;
            Thread.sleep(100);
        }
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

    private static byte[] pdfBlankBytes() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] pdfTextBytes(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(40, 700);
                cs.showText(text == null ? "" : text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] pdfWithImageBytes(byte[] pngBytes) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDImageXObject img = PDImageXObject.createFromByteArray(doc, pngBytes, "x.png");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, 40, 600, 64, 64);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
