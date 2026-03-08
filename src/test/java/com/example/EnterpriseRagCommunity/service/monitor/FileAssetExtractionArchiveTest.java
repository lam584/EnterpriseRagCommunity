package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileAssetExtractionArchiveTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.upload.root", () -> tempDir.resolve("uploads").toString());
        r.add("app.upload.url-prefix", () -> "/uploads");
        r.add("app.file-extraction.images.max-count", () -> "10");
        r.add("app.file-extraction.images.max-image-bytes", () -> String.valueOf(2 * 1024 * 1024));
        r.add("app.file-extraction.images.max-total-bytes", () -> String.valueOf(10 * 1024 * 1024));
        r.add("app.file-extraction.archive.max-depth", () -> "5");
        r.add("app.file-extraction.archive.max-entries", () -> "100");
        r.add("app.file-extraction.archive.max-entry-bytes", () -> String.valueOf(2 * 1024 * 1024));
        r.add("app.file-extraction.archive.max-total-bytes", () -> String.valueOf(10 * 1024 * 1024));
        r.add("app.file-extraction.archive.max-total-millis", () -> "15000");
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
    void zipShouldExtractInnerTextEntries() throws Exception {
        byte[] zip = zipBytes(List.of(
                Map.entry("a.txt", "hello"),
                Map.entry("b.md", "world")
        ));

        Path zipPath = tempDir.resolve("sample.zip");
        Files.write(zipPath, zip);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(zipPath.toString());
        fa.setUrl("/uploads/sample.zip");
        fa.setOriginalName("sample.zip");
        fa.setSizeBytes(Files.size(zipPath));
        fa.setMimeType("application/zip");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedText());
        assertTrue(e.getExtractedText().contains("FILE: a.txt"));
        assertTrue(e.getExtractedText().contains("hello"));
        assertTrue(e.getExtractedText().contains("FILE: b.md"));
        assertTrue(e.getExtractedText().contains("world"));

        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
        Object archive = meta.get("archive");
        assertTrue(archive instanceof Map<?, ?>);
    }

    @Test
    void nestedZipDepthShouldHardFail() throws Exception {
        byte[] inner = zipBytes(List.of(Map.entry("a.txt", "hello")));
        byte[] nested = inner;
        for (int i = 0; i < 5; i++) {
            nested = zipBytes(List.of(Map.entry("inner.zip", nested)));
        }

        Path zipPath = tempDir.resolve("deep.zip");
        Files.write(zipPath, nested);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(zipPath.toString());
        fa.setUrl("/uploads/deep.zip");
        fa.setOriginalName("deep.zip");
        fa.setSizeBytes(Files.size(zipPath));
        fa.setMimeType("application/zip");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.FAILED);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.FAILED, e.getExtractStatus());
        assertEquals("ARCHIVE_NESTING_TOO_DEEP", e.getErrorMessage());
        assertNotNull(e.getExtractedMetadataJson());

        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
        assertEquals("ARCHIVE_NESTING_TOO_DEEP", String.valueOf(meta.get("hardFailReason")));
    }

    @Test
    void sevenZShouldExtractInnerTextEntries() throws Exception {
        Path sevenZPath = tempDir.resolve("sample.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(sevenZPath.toFile())) {
            SevenZArchiveEntry a = new SevenZArchiveEntry();
            a.setName("a.txt");
            a.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(a);
            out.write("hello7z".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();

            SevenZArchiveEntry b = new SevenZArchiveEntry();
            b.setName("b.md");
            b.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(b);
            out.write("world7z".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(sevenZPath.toString());
        fa.setUrl("/uploads/sample.7z");
        fa.setOriginalName("sample.7z");
        fa.setSizeBytes(Files.size(sevenZPath));
        fa.setMimeType("application/x-7z-compressed");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedText());
        assertTrue(e.getExtractedText().contains("hello7z"));
        assertTrue(e.getExtractedText().contains("world7z"));
    }

    @Test
    void sevenZWithXmlEntriesShouldNotFail() throws Exception {
        Path sevenZPath = tempDir.resolve("xml.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(sevenZPath.toFile())) {
            SevenZArchiveEntry a = new SevenZArchiveEntry();
            a.setName("a.xml");
            a.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(a);
            out.write("<root>xml7z</root>".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();

            SevenZArchiveEntry b = new SevenZArchiveEntry();
            b.setName("b.txt");
            b.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(b);
            out.write("helloText".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(sevenZPath.toString());
        fa.setUrl("/uploads/xml.7z");
        fa.setOriginalName("xml.7z");
        fa.setSizeBytes(Files.size(sevenZPath));
        fa.setMimeType("application/x-7z-compressed");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedText());
        assertFalse(e.getExtractedText().contains("xml7z"));
        assertTrue(e.getExtractedText().contains("helloText"));
    }

    @Test
    void zipWithDocxShouldExtractDocxTextNotOoxmlXml() throws Exception {
        byte[] docx = docxBytes("docx-hello");
        byte[] zip = zipBytes(List.of(
                Map.entry("TEST.docx", docx)
        ));
        Path zipPath = tempDir.resolve("docx.zip");
        Files.write(zipPath, zip);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(zipPath.toString());
        fa.setUrl("/uploads/docx.zip");
        fa.setOriginalName("docx.zip");
        fa.setSizeBytes(Files.size(zipPath));
        fa.setMimeType("application/zip");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedText());
        assertTrue(e.getExtractedText().contains("docx-hello"));
        assertFalse(e.getExtractedText().contains("[Content_Types].xml"));
        assertFalse(e.getExtractedText().contains("<Types"));
        assertFalse(e.getExtractedText().contains("word/document.xml"));
    }

    @Test
    void zipWithDocxImageShouldExtractImagesFromInnerFile() throws Exception {
        byte[] png = tinyPngBytes();
        byte[] docx = docxBytesWithImage("docx-with-image", png);
        byte[] zip = zipBytes(List.of(
                Map.entry("TEST.docx", docx)
        ));
        Path zipPath = tempDir.resolve("docx_img.zip");
        Files.write(zipPath, zip);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(zipPath.toString());
        fa.setUrl("/uploads/docx_img.zip");
        fa.setOriginalName("docx_img.zip");
        fa.setSizeBytes(Files.size(zipPath));
        fa.setMimeType("application/zip");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedText());
        assertTrue(e.getExtractedText().contains("docx-with-image"));
        assertTrue(e.getExtractedText().contains("[[IMAGE_1]]"));

        Map<String, Object> meta = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
        Object imgs = meta.get("extractedImages");
        assertTrue(imgs instanceof List<?>);
        assertEquals(1, ((List<?>) imgs).size());
    }

    @Test
    void sevenZWithDocxShouldExtractDocxTextNotOoxmlXml() throws Exception {
        byte[] docx = docxBytes("docx-hello-7z");
        Path sevenZPath = tempDir.resolve("docx.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(sevenZPath.toFile())) {
            SevenZArchiveEntry a = new SevenZArchiveEntry();
            a.setName("TEST.docx");
            a.setContentMethods(List.of(new SevenZMethodConfiguration(SevenZMethod.COPY)));
            out.putArchiveEntry(a);
            out.write(docx);
            out.closeArchiveEntry();
        }

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOwner(null);
        fa.setPath(sevenZPath.toString());
        fa.setUrl("/uploads/docx.7z");
        fa.setOriginalName("docx.7z");
        fa.setSizeBytes(Files.size(sevenZPath));
        fa.setMimeType("application/x-7z-compressed");
        fa.setSha256(randomSha256());
        fa.setStatus(FileAssetStatus.READY);
        fa = fileAssetsRepository.save(fa);

        fileAssetExtractionAsyncService.extractAsync(fa.getId());

        FileAssetExtractionsEntity e = waitExtraction(fa.getId(), FileAssetExtractionStatus.READY);
        assertNotNull(e);
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertNotNull(e.getExtractedText());
        assertTrue(e.getExtractedText().contains("docx-hello-7z"));
        assertFalse(e.getExtractedText().contains("[Content_Types].xml"));
        assertFalse(e.getExtractedText().contains("<Types"));
        assertFalse(e.getExtractedText().contains("word/document.xml"));
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

    private static byte[] zipBytes(List<Map.Entry<String, Object>> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Object> it : entries) {
                String name = it.getKey();
                Object v = it.getValue();
                ZipEntry ze = new ZipEntry(name);
                zos.putNextEntry(ze);
                if (v instanceof String s) {
                    zos.write(s.getBytes(StandardCharsets.UTF_8));
                } else if (v instanceof byte[] b) {
                    zos.write(b);
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText(text);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] docxBytesWithImage(String text, byte[] png) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            var p = doc.createParagraph();
            var r = p.createRun();
            r.setText(text);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(png)) {
                r.addPicture(bis, Document.PICTURE_TYPE_PNG, "img.png", Units.toEMU(16), Units.toEMU(16));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] tinyPngBytes() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
