package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFPictureShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

class FileAssetExtractionAsyncServiceOfficeImageCoverageTest {
    @Mock private FileAssetsRepository fileAssetsRepository;
    @Mock private FileAssetExtractionsRepository fileAssetExtractionsRepository;
    @Mock private UploadFormatsConfigService uploadFormatsConfigService;
    @Mock private VectorIndicesRepository vectorIndicesRepository;
    @Mock private RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService;
    @Mock private TokenCountService tokenCountService;
    @Mock private DerivedUploadStorageService derivedUploadStorageService;

    private FileAssetExtractionAsyncService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FileAssetExtractionAsyncService(
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                uploadFormatsConfigService,
                new ObjectMapper(),
                vectorIndicesRepository,
                ragFileAssetIndexAsyncService,
                tokenCountService,
                derivedUploadStorageService
        );
        when(derivedUploadStorageService.getMaxImageBytes()).thenReturn(1024 * 1024L);
        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(Map.of("id", "1"));
        when(derivedUploadStorageService.buildPlaceholder(anyInt(), anyMap())).thenReturn(Map.of("placeholder", "[[IMAGE_1]]"));
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = FileAssetExtractionAsyncService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    private Object newBudget(int maxCount, long maxBytes) throws Exception {
        Class<?> c = Class.forName(FileAssetExtractionAsyncService.class.getName() + "$ImageBudget");
        Constructor<?> ctor = c.getDeclaredConstructor(int.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(maxCount, maxBytes);
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 4, 4);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void extractDocxImages_shouldCoverImageAndNullSavePaths() throws Exception {
        byte[] png = pngBytes();
        Path docx = tempDir.resolve("a.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("hello");
            doc.addPictureData(png, Document.PICTURE_TYPE_PNG);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                doc.write(baos);
                Files.write(docx, baos.toByteArray());
            }
        }

        Object budget = newBudget(10, 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out1 = invoke("extractDocxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                docx, meta, 1L, budget);
        assertNotNull(out1);

        when(derivedUploadStorageService.saveDerivedImage(any(), any(), any(), any())).thenReturn(null);
        meta.clear();
        Object out2 = invoke("extractDocxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                docx, meta, 1L, budget);
        assertNotNull(out2);
    }

    @Test
    void extractXlsxImages_shouldCoverPictureLoop() throws Exception {
        byte[] png = pngBytes();
        Path xlsx = tempDir.resolve("a.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("s");
            int idx = wb.addPicture(png, Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(1);
            anchor.setRow1(1);
            drawing.createPicture(anchor, idx);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                wb.write(baos);
                Files.write(xlsx, baos.toByteArray());
            }
        }

        Object budget = newBudget(10, 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invoke("extractXlsxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                xlsx, meta, 1L, budget);
        assertNotNull(out);
        assertTrue("XLSX_EMBEDDED".equals(String.valueOf(meta.get("imagesExtractionMode")))
                || "EMBEDDED_NONE".equals(String.valueOf(meta.get("imagesExtractionMode"))));
    }

    @Test
    void extractPptxImages_shouldCoverPictureLoop() throws Exception {
        byte[] png = pngBytes();
        Path pptx = tempDir.resolve("a.pptx");
        try (XMLSlideShow ss = new XMLSlideShow()) {
            XSLFSlide slide = ss.createSlide();
            XSLFPictureData pd = ss.addPicture(png, PictureData.PictureType.PNG);
            XSLFPictureShape ps = slide.createPicture(pd);
            ps.setAnchor(new Rectangle(20, 20, 50, 50));
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ss.write(baos);
                Files.write(pptx, baos.toByteArray());
            }
        }

        Object budget = newBudget(10, 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invoke("extractPptxImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                pptx, meta, 1L, budget);
        assertNotNull(out);
    }

    @Test
    void extractPptImages_shouldCoverPictureLoop() throws Exception {
        byte[] png = pngBytes();
        Path ppt = tempDir.resolve("a.ppt");
        try (HSLFSlideShow ss = new HSLFSlideShow()) {
            HSLFSlide slide = ss.createSlide();
            HSLFPictureData pd = ss.addPicture(png, PictureData.PictureType.PNG);
            HSLFPictureShape ps = new HSLFPictureShape(pd);
            ps.setAnchor(new Rectangle(10, 10, 40, 40));
            slide.addShape(ps);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ss.write(baos);
                Files.write(ppt, baos.toByteArray());
            }
        }

        Object budget = newBudget(10, 1024 * 1024L);
        Map<String, Object> meta = new LinkedHashMap<>();
        Object out = invoke("extractPptImages",
                new Class<?>[]{Path.class, Map.class, Long.class, budget.getClass()},
                ppt, meta, 1L, budget);
        assertNotNull(out);
    }
}
