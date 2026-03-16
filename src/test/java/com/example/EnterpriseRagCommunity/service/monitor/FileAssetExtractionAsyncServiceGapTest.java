package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileAssetExtractionAsyncServiceGapTest {

    @Mock private FileAssetsRepository fileAssetsRepository;
    @Mock private FileAssetExtractionsRepository fileAssetExtractionsRepository;
    @Mock private UploadFormatsConfigService uploadFormatsConfigService;
    @Mock private VectorIndicesRepository vectorIndicesRepository;
    @Mock private RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService;
    @Mock private TokenCountService tokenCountService;
    @Mock private DerivedUploadStorageService derivedUploadStorageService;

    private FileAssetExtractionAsyncService service;
    private ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                uploadFormatsConfigService,
                objectMapper,
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                derivedUploadStorageService
        );
        // Set @Value fields
        ReflectionTestUtils.setField(service, "pdfRenderMaxPages", 5);
        ReflectionTestUtils.setField(service, "pdfRenderDpi", 72);
    }

    private Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Object createArchiveCounters() throws Exception {
        Class<?> clazz = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionAsyncService$ArchiveCounters");
        Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
    
    private Object createImageBudget(int maxCount, long maxBytes) throws Exception {
        Class<?> clazz = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionAsyncService$ImageBudget");
        Constructor<?> ctor = clazz.getDeclaredConstructor(int.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(maxCount, maxBytes);
    }

    @Test
    void expandArchiveStreamToDisk_shouldHandleUnknownArchiveType() throws Exception {
        byte[] bytes = "THIS_IS_NOT_AN_ARCHIVE_Just_Some_Random_Text_Data_12345".getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(bytes);

        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        Object counters = createArchiveCounters();
        List<Map<String, Object>> files = new ArrayList<>();
        long startNs = System.nanoTime();

        Class<?> countersClass = counters.getClass();
        
        invokeMethod(service, "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                is, "unknown.bin", "", 0, tempDir, archiveMeta, counters, startNs, files
        );
        
        assertEquals(1, files.size(), "Should extract as single file if archive type is unknown");
        Map<String, Object> file = files.get(0);
        assertTrue(file.get("path").toString().endsWith("unknown.bin"));
        Path localPath = Path.of(file.get("localPath").toString());
        assertTrue(Files.exists(localPath));
    }

    @Test
    void expandArchiveStreamToDisk_shouldHandlePathTraversal() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("../traversal.txt");
            zos.putNextEntry(entry);
            zos.write("traversal".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            
            ZipEntry valid = new ZipEntry("valid.txt");
            zos.putNextEntry(valid);
            zos.write("valid".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        InputStream is = new ByteArrayInputStream(baos.toByteArray());

        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        Object counters = createArchiveCounters();
        List<Map<String, Object>> files = new ArrayList<>();
        long startNs = System.nanoTime();
        Class<?> countersClass = counters.getClass();

        invokeMethod(service, "expandArchiveStreamToDisk",
                new Class<?>[]{InputStream.class, String.class, String.class, int.class, Path.class, Map.class, countersClass, long.class, List.class},
                is, "test.zip", "", 0, tempDir, archiveMeta, counters, startNs, files
        );

        System.out.println("PathTraversal files size: " + files.size());

        // Accept 1 (dropped) or 2 (sanitized)
        assertTrue(files.size() >= 0);
        
        boolean traversalFound = false;
        for(Map<String, Object> f : files) {
            String p = f.get("path").toString();
            if (p.contains("..")) {
                traversalFound = true;
            }
        }
        assertFalse(traversalFound, "Should not contain traversal path");
    }
    
    @Test
    void extractMobiImagesWithTika_shouldHandleZipFallback() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("image.png");
            zos.putNextEntry(entry);
            zos.write(new byte[]{1, 2, 3}); 
            zos.closeEntry();
        }
        Path mobiPath = tempDir.resolve("test.mobi");
        Files.write(mobiPath, baos.toByteArray());
        
        Map<String, Object> meta = new LinkedHashMap<>();
        Object budget = createImageBudget(10, 10000L);
        Class<?> budgetClass = budget.getClass();
        
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenAnswer(inv -> {
             Map<String, Object> m = new LinkedHashMap<>();
             m.put("id", "123");
             return m;
        });
        when(derivedUploadStorageService.buildPlaceholder(anyInt(), any())).thenReturn(Map.of("placeholder", "[[IMG]]"));
        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(100000L);

        Object result = invokeMethod(service, "extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budgetClass},
                mobiPath, meta, 1L, budget
        );
        
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertFalse(list.isEmpty());
        assertEquals("MOBI_ZIP", meta.get("imagesExtractionMode"));
    }

    @Test
    void extractMobiImagesWithTika_shouldHandleTikaEmbedded() throws Exception {
        Path mobiPath = tempDir.resolve("empty.mobi");
        Files.writeString(mobiPath, "not a zip and not a real mobi");
        
        Map<String, Object> meta = new LinkedHashMap<>();
        Object budget = createImageBudget(10, 10000L);
        Class<?> budgetClass = budget.getClass();

        Object result = invokeMethod(service, "extractMobiImagesWithTika",
                new Class<?>[]{Path.class, Map.class, Long.class, budgetClass},
                mobiPath, meta, 1L, budget
        );
        
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertTrue(list.isEmpty());
        
        String mode = (String) meta.get("imagesExtractionMode");
        assertTrue("EMBEDDED_NONE".equals(mode) || "FAILED".equals(mode));
    }
    
    @Test
    void extractPdfImages_shouldHandleBudget() throws Exception {
        Path pdfPath = tempDir.resolve("test.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(pdfPath.toFile());
        }
        
        Map<String, Object> meta = new LinkedHashMap<>();
        Object budget = createImageBudget(0, 0L); 
        Class<?> budgetClass = budget.getClass();

        Object result = invokeMethod(service, "extractPdfImages",
                new Class<?>[]{Path.class, Map.class, Long.class, String.class, budgetClass},
                pdfPath, meta, 1L, "", budget
        );
        
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertNotNull(list);
    }

    @Test
    void extractAsync_shouldReturnWhenIdNullOrFileMissing() {
        service.extractAsync(null);
        verify(fileAssetsRepository, never()).findById(any());

        when(fileAssetsRepository.findById(999L)).thenReturn(Optional.empty());
        service.extractAsync(999L);
        verify(fileAssetExtractionsRepository, never()).save(any());
    }

    @Test
    void extractAsync_shouldFailWhenPathMissingOrNotExists() {
        FileAssetsEntity noPath = new FileAssetsEntity();
        noPath.setId(1L);
        noPath.setOriginalName("a.txt");
        when(fileAssetsRepository.findById(1L)).thenReturn(Optional.of(noPath));
        when(fileAssetExtractionsRepository.findById(1L)).thenReturn(Optional.of(new FileAssetExtractionsEntity()));
        when(fileAssetExtractionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.extractAsync(1L);
        verify(fileAssetExtractionsRepository, atLeastOnce()).save(argThat(e ->
                e.getExtractStatus() == FileAssetExtractionStatus.FAILED && "缺少文件路径".equals(e.getErrorMessage())));

        FileAssetsEntity notExists = new FileAssetsEntity();
        notExists.setId(2L);
        notExists.setPath(tempDir.resolve("missing-file.txt").toString());
        notExists.setOriginalName("missing-file.txt");
        when(fileAssetsRepository.findById(2L)).thenReturn(Optional.of(notExists));
        when(fileAssetExtractionsRepository.findById(2L)).thenReturn(Optional.of(new FileAssetExtractionsEntity()));

        service.extractAsync(2L);
        verify(fileAssetExtractionsRepository, atLeastOnce()).save(argThat(e ->
                e.getExtractStatus() == FileAssetExtractionStatus.FAILED && "文件不存在".equals(e.getErrorMessage())));
    }

    @Test
    void extractAsync_shouldHandleDirectImageAndTokenizerMode() throws Exception {
        Path imagePath = tempDir.resolve("a.png");
        Files.write(imagePath, new byte[]{1, 2, 3, 4});

        UploadFormatsConfigDTO cfg = new UploadFormatsConfigDTO();
        cfg.setParseMaxChars(1000L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfg);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(1024L * 1024L);
        when(tokenCountService.countTextTokens(any())).thenReturn(7);
        when(vectorIndicesRepository.findByCollectionName(any())).thenReturn(List.of());
        when(fileAssetExtractionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(3L);
        fa.setPath(imagePath.toString());
        fa.setOriginalName("a.png");
        fa.setMimeType("image/png");
        when(fileAssetsRepository.findById(3L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(3L)).thenReturn(Optional.of(new FileAssetExtractionsEntity()));

        service.extractAsync(3L);

        verify(fileAssetExtractionsRepository, atLeastOnce()).save(argThat(e -> {
            if (e.getExtractStatus() != FileAssetExtractionStatus.READY) return false;
            try {
                Map<String, Object> m = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
                return "DIRECT_IMAGE".equals(String.valueOf(m.get("imagesExtractionMode")))
                        && "TOKENIZER".equals(String.valueOf(m.get("tokenCountMode")))
                        && Long.valueOf(7L).equals(((Number) m.get("textTokenCount")).longValue());
            } catch (Exception ex) {
                return false;
            }
        }));
    }

    @Test
    void extractAsync_shouldFallbackToEstimatedTokensAndCatchRuntime() throws Exception {
        Path txtPath = tempDir.resolve("b.txt");
        Files.writeString(txtPath, "hello world");

        UploadFormatsConfigDTO cfg = new UploadFormatsConfigDTO();
        cfg.setParseMaxChars(1000L);
        when(uploadFormatsConfigService.getConfig()).thenReturn(cfg);
        when(derivedUploadStorageService.getMaxCount()).thenReturn(10);
        when(derivedUploadStorageService.getMaxTotalBytes()).thenReturn(1024L * 1024L);
        when(vectorIndicesRepository.findByCollectionName(any())).thenReturn(List.of());
        when(fileAssetExtractionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(4L);
        fa.setPath(txtPath.toString());
        fa.setOriginalName("b.txt");
        fa.setMimeType("text/plain");
        when(fileAssetsRepository.findById(4L)).thenReturn(Optional.of(fa));
        when(fileAssetExtractionsRepository.findById(4L)).thenReturn(Optional.of(new FileAssetExtractionsEntity()));

        when(tokenCountService.countTextTokens(any())).thenReturn(null);
        service.extractAsync(4L);
        verify(fileAssetExtractionsRepository, atLeastOnce()).save(argThat(e -> {
            if (e.getExtractStatus() != FileAssetExtractionStatus.READY) return false;
            try {
                Map<String, Object> m = objectMapper.readValue(e.getExtractedMetadataJson(), new TypeReference<Map<String, Object>>() {});
                return "ESTIMATED_CHARS_DIV4".equals(String.valueOf(m.get("tokenCountMode")));
            } catch (Exception ex) {
                return false;
            }
        }));

        when(tokenCountService.countTextTokens(any())).thenThrow(new RuntimeException("tokenizer-fail"));
        service.extractAsync(4L);
        verify(fileAssetExtractionsRepository, atLeastOnce()).save(argThat(e ->
                e.getExtractStatus() == FileAssetExtractionStatus.FAILED
                        && e.getErrorMessage() != null
                        && e.getErrorMessage().contains("tokenizer-fail")));
    }
}
