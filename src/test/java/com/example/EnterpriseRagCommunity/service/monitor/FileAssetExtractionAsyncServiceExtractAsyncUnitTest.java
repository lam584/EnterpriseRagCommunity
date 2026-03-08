package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileAssetExtractionAsyncServiceExtractAsyncUnitTest {

    @TempDir
    Path tempDir;

    @Mock
    FileAssetsRepository fileAssetsRepository;

    @Mock
    FileAssetExtractionsRepository fileAssetExtractionsRepository;

    @Mock
    UploadFormatsConfigService uploadFormatsConfigService;

    @Mock
    VectorIndicesRepository vectorIndicesRepository;

    @Mock
    RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService;

    @Mock
    TokenCountService tokenCountService;

    @Mock
    DerivedUploadStorageService derivedUploadStorageService;

    @Captor
    ArgumentCaptor<FileAssetExtractionsEntity> extractionCaptor;

    private FileAssetExtractionAsyncService newService(ObjectMapper objectMapper) {
        FileAssetExtractionAsyncService s = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                uploadFormatsConfigService,
                objectMapper,
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                derivedUploadStorageService
        );
        setField(s, "pdfRenderMaxPages", 2);
        setField(s, "pdfRenderDpi", 96);
        setField(s, "archiveMaxDepth", 5);
        setField(s, "archiveMaxEntries", 100);
        setField(s, "archiveMaxEntryBytes", 2 * 1024 * 1024L);
        setField(s, "archiveMaxTotalBytes", 10 * 1024 * 1024L);
        setField(s, "archiveMaxTotalMillis", 15000L);
        return s;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static UploadFormatsConfigDTO cfgWithParseMaxChars(Long maxChars) {
        UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
        dto.setParseMaxChars(maxChars);
        return dto;
    }

    private static byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText(text);
            try (var baos = new java.io.ByteArrayOutputStream()) {
                doc.write(baos);
                return baos.toByteArray();
            }
        }
    }

    private static Map<String, Object> readMeta(ObjectMapper om, FileAssetExtractionsEntity e) throws Exception {
        return om.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
    }

    private static byte[] zipBytes(String name, byte[] bytes) throws Exception {
        try (var baos = new java.io.ByteArrayOutputStream(); ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            ZipEntry ze = new ZipEntry(name);
            zos.putNextEntry(ze);
            zos.write(bytes);
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        }
    }

    @Test
    void extractAsync_whenIdNull_shouldDoNothing() {
        FileAssetExtractionAsyncService s = newService(new ObjectMapper());
        s.extractAsync(null);
        verifyNoInteractions(fileAssetsRepository, fileAssetExtractionsRepository, uploadFormatsConfigService, vectorIndicesRepository, ragFileAssetIndexAsyncService, tokenCountService, derivedUploadStorageService);
    }

    @Test
    void extractAsync_whenAssetMissing_shouldReturn() {
        FileAssetExtractionAsyncService s = newService(new ObjectMapper());
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.empty());
        s.extractAsync(1L);
        verify(fileAssetsRepository).findById(1L);
        verifyNoMoreInteractions(fileAssetsRepository);
        verifyNoInteractions(fileAssetExtractionsRepository, uploadFormatsConfigService, vectorIndicesRepository, ragFileAssetIndexAsyncService, tokenCountService, derivedUploadStorageService);
    }

    @Test
    void extractAsync_whenPathBlank_shouldFailAndSave() {
        FileAssetExtractionAsyncService s = newService(new ObjectMapper());
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath("   ");
        fa.setMimeType("text/plain");
        fa.setOriginalName("a.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(1L, e.getFileAssetId());
        assertEquals(FileAssetExtractionStatus.FAILED, e.getExtractStatus());
        assertEquals("缺少文件路径", e.getErrorMessage());
        verifyNoInteractions(vectorIndicesRepository, ragFileAssetIndexAsyncService, uploadFormatsConfigService, tokenCountService, derivedUploadStorageService);
    }

    @Test
    void extractAsync_whenFileMissing_shouldFailAndSave() throws Exception {
        FileAssetExtractionAsyncService s = newService(new ObjectMapper());
        Path missing = tempDir.resolve("missing.txt");

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(missing.toString());
        fa.setMimeType("text/plain");
        fa.setOriginalName("missing.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(FileAssetExtractionStatus.FAILED, e.getExtractStatus());
        assertEquals("文件不存在", e.getErrorMessage());
    }

    @Test
    void extractAsync_whenExtNullAndNoImagesMode_shouldWriteImagesExtractionModeNone() throws Exception {
        ObjectMapper om = new ObjectMapper();
        FileAssetExtractionAsyncService s = newService(om);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(10 * 1024 * 1024L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfgWithParseMaxChars(null));
        when(tokenCountService.countTextTokens(anyString())).thenReturn(3);
        when(vectorIndicesRepository.findByCollectionName(anyString())).thenReturn(List.of());

        Path p = tempDir.resolve("noext");
        Files.write(p, docxBytes("docx-no-ext"));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(p.toString());
        fa.setMimeType("application/octet-stream");
        fa.setOriginalName("noext");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertTrue(e.getExtractedText().contains("docx-no-ext"));
        Map<String, Object> meta = readMeta(om, e);
        assertEquals("NONE", String.valueOf(meta.get("imagesExtractionMode")));
    }

    @Test
    void extractAsync_whenTokenizerReturnsNull_shouldEstimateTokens() throws Exception {
        ObjectMapper om = new ObjectMapper();
        FileAssetExtractionAsyncService s = newService(om);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(10 * 1024 * 1024L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfgWithParseMaxChars(200000L));
        when(tokenCountService.countTextTokens(anyString())).thenReturn(null);
        when(vectorIndicesRepository.findByCollectionName(anyString())).thenReturn(List.of());

        Path p = tempDir.resolve("a.txt");
        Files.writeString(p, "hello world", StandardCharsets.UTF_8);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(p.toString());
        fa.setMimeType("text/plain");
        fa.setOriginalName("a.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        Map<String, Object> meta = readMeta(om, e);
        assertEquals("ESTIMATED_CHARS_DIV4", String.valueOf(meta.get("tokenCountMode")));
        assertTrue(((Number) meta.get("textTokenCount")).longValue() > 0);
    }

    @Test
    void extractAsync_whenTokenizerReturnsValue_shouldUseTokenizer() throws Exception {
        ObjectMapper om = new ObjectMapper();
        FileAssetExtractionAsyncService s = newService(om);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(10 * 1024 * 1024L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfgWithParseMaxChars(200000L));
        when(tokenCountService.countTextTokens(anyString())).thenReturn(7);
        when(vectorIndicesRepository.findByCollectionName(anyString())).thenReturn(List.of());

        Path p = tempDir.resolve("a.txt");
        Files.writeString(p, "hello", StandardCharsets.UTF_8);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(p.toString());
        fa.setMimeType("text/plain");
        fa.setOriginalName("a.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        Map<String, Object> meta = readMeta(om, e);
        assertEquals("TOKENIZER", String.valueOf(meta.get("tokenCountMode")));
        assertEquals(7L, ((Number) meta.get("textTokenCount")).longValue());
    }

    @Test
    void extractAsync_whenParseMaxCharsNegative_shouldClampToZero() throws Exception {
        ObjectMapper om = new ObjectMapper();
        FileAssetExtractionAsyncService s = newService(om);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(10 * 1024 * 1024L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfgWithParseMaxChars(-1L));
        when(tokenCountService.countTextTokens(anyString())).thenReturn(null);
        when(vectorIndicesRepository.findByCollectionName(anyString())).thenReturn(List.of());

        Path p = tempDir.resolve("a.txt");
        Files.writeString(p, "hello", StandardCharsets.UTF_8);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(p.toString());
        fa.setMimeType("text/plain");
        fa.setOriginalName("a.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(FileAssetExtractionStatus.READY, e.getExtractStatus());
        assertEquals("", e.getExtractedText());
        Map<String, Object> meta = readMeta(om, e);
        assertEquals(0L, ((Number) meta.get("textCharCount")).longValue());
        assertEquals(0L, ((Number) meta.get("textTokenCount")).longValue());
    }

    @Test
    void extractAsync_whenRagSyncEligible_shouldInvokeSync() throws Exception {
        ObjectMapper om = new ObjectMapper();
        FileAssetExtractionAsyncService s = newService(om);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(10 * 1024 * 1024L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfgWithParseMaxChars(200000L));
        when(tokenCountService.countTextTokens(anyString())).thenReturn(1);

        var vi = new com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity();
        vi.setId(9L);
        when(vectorIndicesRepository.findByCollectionName("rag_file_assets_v1")).thenReturn(List.of(vi));

        Path p = tempDir.resolve("a.txt");
        Files.writeString(p, "hello", StandardCharsets.UTF_8);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(p.toString());
        fa.setMimeType("text/plain");
        fa.setOriginalName("a.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(ragFileAssetIndexAsyncService).syncSingleFileAssetAsync(9L, 1L);
    }

    @Test
    void extractAsync_whenHardFailAndObjectMapperFails_shouldSetMetadataNull() throws Exception {
        ObjectMapper om = mock(ObjectMapper.class);
        FileAssetExtractionAsyncService s = newService(om);
        setField(s, "archiveMaxDepth", 0);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(10 * 1024 * 1024L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfgWithParseMaxChars(200000L));
        when(om.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));

        byte[] zip = zipBytes("a.txt", "hello".getBytes(StandardCharsets.UTF_8));

        Path zipPath = tempDir.resolve("a.zip");
        Files.write(zipPath, zip);

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(1L);
        fa.setPath(zipPath.toString());
        fa.setMimeType("application/zip");
        fa.setOriginalName("a.zip");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.empty());

        s.extractAsync(1L);

        verify(fileAssetExtractionsRepository).save(extractionCaptor.capture());
        FileAssetExtractionsEntity e = extractionCaptor.getValue();
        assertEquals(FileAssetExtractionStatus.FAILED, e.getExtractStatus());
        assertEquals("ARCHIVE_NESTING_TOO_DEEP", e.getErrorMessage());
        assertNull(e.getExtractedMetadataJson());
    }
}
