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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class FileAssetExtractionAsyncService {

    private final FileAssetsRepository fileAssetsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final UploadFormatsConfigService uploadFormatsConfigService;
    private final ObjectMapper objectMapper;
    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService;
    private final TokenCountService tokenCountService;
    private final DerivedUploadStorageService derivedUploadStorageService;

    @Value("${app.file-extraction.pdf-render.max-pages:20}")
    private int pdfRenderMaxPages;

    @Value("${app.file-extraction.pdf-render.dpi:144}")
    private int pdfRenderDpi;

    @Value("${app.file-extraction.archive.max-depth:500}")
    private int archiveMaxDepth;

    @Value("${app.file-extraction.archive.max-entries:2000}")
    private int archiveMaxEntries;

    @Value("${app.file-extraction.archive.max-entry-bytes:10485760}")
    private long archiveMaxEntryBytes;

    @Value("${app.file-extraction.archive.max-total-bytes:104857600}")
    private long archiveMaxTotalBytes;

    @Value("${app.file-extraction.archive.max-total-millis:15000}")
    private long archiveMaxTotalMillis;

    @Async("fileExtractionExecutor")
    @Transactional
    public void extractAsync(Long fileAssetId) {
        if (fileAssetId == null) return;
        FileAssetsEntity fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
        if (fa == null) return;

        FileAssetExtractionsEntity e = fileAssetExtractionsRepository.findById(fileAssetId).orElse(null);
        if (e == null) {
            e = new FileAssetExtractionsEntity();
            e.setFileAssetId(fileAssetId);
        }

        String pathStr = fa.getPath();
        if (pathStr == null || pathStr.isBlank()) {
            e.setExtractStatus(FileAssetExtractionStatus.FAILED);
            e.setErrorMessage("缺少文件路径");
            fileAssetExtractionsRepository.save(e);
            return;
        }

        Path path = Path.of(pathStr);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            e.setExtractStatus(FileAssetExtractionStatus.FAILED);
            e.setErrorMessage("文件不存在");
            fileAssetExtractionsRepository.save(e);
            return;
        }

        UploadFormatsConfigDTO cfg = uploadFormatsConfigService.getConfig();
        long cfgMaxChars = cfg.getParseMaxChars() == null ? 200000L : cfg.getParseMaxChars();
        if (cfgMaxChars < 0L) cfgMaxChars = 0L;
        int maxChars = (int) Math.min((long) Integer.MAX_VALUE, cfgMaxChars);
        String ext = extLowerOrNull(fa.getOriginalName());
        if (ext == null) ext = extLowerOrNull(path.getFileName().toString());

        long startNs = System.nanoTime();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("ext", ext);
        meta.put("mimeType", fa.getMimeType());

        try {
            ImageBudget imageBudget = new ImageBudget(derivedUploadStorageService.getMaxCount(), derivedUploadStorageService.getMaxTotalBytes());
            String text = extractText(path, ext, maxChars, meta, fileAssetId, imageBudget);
            List<Map<String, Object>> extractedImages;
            if (isArchiveExt(ext)) {
                extractedImages = takeList(meta.get("archiveExtractedImages"));
                meta.remove("archiveExtractedImages");
            } else {
                extractedImages = extractImages(path, ext, meta, fileAssetId, text, imageBudget);
            }
            if (extractedImages != null && !extractedImages.isEmpty()) {
                meta.put("extractedImages", extractedImages);
                meta.put("imageCount", extractedImages.size());
            }
            if (meta.get("imagesExtractionMode") == null) {
                String eext = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
                if (extractedImages != null && !extractedImages.isEmpty()) {
                    if (eext.equals("epub")) meta.put("imagesExtractionMode", "EPUB_ZIP");
                    else if (eext.equals("mobi")) meta.put("imagesExtractionMode", "MOBI_TIKA_EMBEDDED");
                    else meta.put("imagesExtractionMode", "UNKNOWN");
                } else {
                    meta.put("imagesExtractionMode", "NONE");
                }
            }

            long durationMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
            meta.put("parseDurationMs", durationMs);

            String finalText = text == null ? "" : text;
            finalText = appendImagePlaceholders(finalText, extractedImages);
            meta.put("textCharCount", (long) finalText.length());
            Integer tokens = tokenCountService.countTextTokens(finalText);
            if (tokens != null) {
                meta.put("textTokenCount", (long) tokens);
                meta.put("tokenCountMode", "TOKENIZER");
            } else {
                long est = estimateTokens(finalText);
                meta.put("textTokenCount", est);
                meta.put("tokenCountMode", "ESTIMATED_CHARS_DIV4");
            }
            fillDerivedImageCount(meta, finalText);

            e.setExtractStatus(FileAssetExtractionStatus.READY);
            e.setExtractedText(finalText);
            e.setErrorMessage(null);
            e.setExtractedMetadataJson(objectMapper.writeValueAsString(meta));
        } catch (HardFailException hf) {
            meta.put("hardFailReason", hf.reason);
            meta.put("hardFail", true);
            meta.put("parseDurationMs", Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L));
            e.setExtractStatus(FileAssetExtractionStatus.FAILED);
            e.setExtractedText(null);
            e.setErrorMessage(hf.reason);
            try {
                e.setExtractedMetadataJson(objectMapper.writeValueAsString(meta));
            } catch (Exception ignore) {
                e.setExtractedMetadataJson(null);
            }
        } catch (Exception ex) {
            meta.put("parseDurationMs", Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L));
            e.setExtractStatus(FileAssetExtractionStatus.FAILED);
            e.setExtractedText(null);
            e.setErrorMessage(safeMsg(ex));
            try {
                e.setExtractedMetadataJson(objectMapper.writeValueAsString(meta));
            } catch (Exception ignore) {
                e.setExtractedMetadataJson(null);
            }
        }

        fileAssetExtractionsRepository.save(e);
        trySyncRagFileIndex(fileAssetId);
    }

    private void trySyncRagFileIndex(Long fileAssetId) {
        if (fileAssetId == null) return;
        try {
            var list = vectorIndicesRepository.findByCollectionName("rag_file_assets_v1");
            if (list == null || list.isEmpty()) return;
            Long vectorIndexId = list.get(0) == null ? null : list.get(0).getId();
            if (vectorIndexId == null) return;
            ragFileAssetIndexAsyncService.syncSingleFileAssetAsync(vectorIndexId, fileAssetId);
        } catch (Exception ignored) {
        }
    }

    private String extractText(Path path, String ext, int maxChars, Map<String, Object> meta, Long fileAssetId, ImageBudget imageBudget) throws Exception {
        String e = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        if (isImageExt(e)) {
            meta.put("imageCount", 1);
            meta.put("imagesExtractionMode", "DIRECT_IMAGE");
            return "";
        }
        if (isArchiveExt(e)) {
            return extractArchive(path, e, maxChars, meta, fileAssetId, imageBudget);
        }
        if (e.equals("pdf")) {
            return extractPdf(path, maxChars, meta);
        }
        if (e.equals("txt") || e.equals("md") || e.equals("markdown") || e.equals("csv") || e.equals("json")) {
            return extractPlain(path, maxChars);
        }
        if (e.equals("html") || e.equals("htm")) {
            return extractHtml(path, maxChars);
        }
        if (e.equals("epub") || e.equals("mobi")) {
            try {
                return extractWithTika(path, maxChars, meta);
            } catch (Exception ex) {
                meta.put("tikaParseError", safeMsg(ex));
                return "";
            }
        }
        return extractOffice(path, maxChars);
    }

    private String extractArchive(Path path, String ext, int maxChars, Map<String, Object> meta, Long fileAssetId, ImageBudget imageBudget) throws Exception {
        Map<String, Object> archiveMeta = new LinkedHashMap<>();
        meta.put("archive", archiveMeta);
        archiveMeta.put("enabled", true);
        archiveMeta.put("mode", "UNPACK_THEN_PARSE");

        long start = System.nanoTime();
        ArchiveCounters c = new ArchiveCounters();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("erc-archive-" + (fileAssetId == null ? "na" : String.valueOf(fileAssetId)) + "-");
            List<Map<String, Object>> files = new ArrayList<>();
            archiveMeta.put("files", files);

            expandArchiveToDisk(path, path.getFileName().toString(), ext, "", 0, workDir, archiveMeta, c, start, files);

            StringBuilder out = new StringBuilder();
            List<Map<String, Object>> images = new ArrayList<>();
            int parsed = 0;
            int skipped = 0;
            for (Map<String, Object> f : files) {
                if (exceededArchiveBudget(c, start)) {
                    c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
                    break;
                }
                if (f == null) continue;
                Object fp = f.get("localPath");
                if (!(fp instanceof String s) || s.isBlank()) continue;
                if (Boolean.TRUE.equals(f.get("extractionTruncated"))) {
                    f.put("parseEligible", false);
                    f.put("parseStatus", "SKIPPED_TRUNCATED");
                    skipped++;
                    continue;
                }
                Path p = Path.of(s);
                String vpath = String.valueOf(f.getOrDefault("path", ""));
                String fext = (f.get("ext") instanceof String se) ? se : "";
                boolean eligible = isSupportedInnerExtForExtraction(fext);
                f.put("parseEligible", eligible);
                if (!eligible) {
                    f.put("parseStatus", "SKIPPED_UNSUPPORTED");
                    skipped++;
                    continue;
                }
                int remaining = Math.max(0, maxChars - out.length());
                if (remaining <= 0) {
                    c.truncatedReason = c.truncatedReason == null ? "TEXT_CHAR_LIMIT" : c.truncatedReason;
                    break;
                }
                try {
                    Map<String, Object> perMeta = new LinkedHashMap<>();
                    String txt = extractText(p, fext, remaining, perMeta, fileAssetId, imageBudget);
                    List<Map<String, Object>> imgs = extractImages(p, fext, perMeta, fileAssetId, txt, imageBudget);
                    if (!imgs.isEmpty()) images.addAll(imgs);
                    if (txt != null && !txt.isBlank()) {
                        appendExtractedFileBlock(out, vpath, txt);
                    }
                    f.put("parseStatus", "PARSED");
                    f.put("textChars", txt == null ? 0 : txt.length());
                    f.put("imagesAdded", imgs.size());
                    parsed++;
                } catch (Exception ex) {
                    f.put("parseStatus", "FAILED");
                    f.put("parseError", safeMsg(ex));
                    skipped++;
                }
                if (out.length() >= maxChars) {
                    c.truncatedReason = c.truncatedReason == null ? "TEXT_CHAR_LIMIT" : c.truncatedReason;
                    break;
                }
            }

            for (Map<String, Object> f : files) {
                if (f == null) continue;
                f.remove("localPath");
            }

            c.filesParsed = parsed;
            c.filesSkipped = skipped;
            meta.put("archiveExtractedImages", images);
            meta.put("imagesExtractionMode", "ARCHIVE_INNER_FILES");

            archiveMeta.put("maxDepthSeen", c.maxDepthSeen);
            archiveMeta.put("entriesSeen", c.entriesSeen);
            archiveMeta.put("filesParsed", c.filesParsed);
            archiveMeta.put("filesSkipped", c.filesSkipped);
            archiveMeta.put("pathTraversalDroppedCount", c.pathTraversalDroppedCount);
            archiveMeta.put("totalBytesRead", c.totalBytesRead);
            if (c.truncatedReason != null) {
                archiveMeta.put("truncated", true);
                archiveMeta.put("truncatedReason", c.truncatedReason);
            } else {
                archiveMeta.put("truncated", false);
            }
            return truncate(out.toString().trim(), maxChars);
        } catch (ArchiveNestingTooDeepException deep) {
            archiveMeta.put("maxDepthSeen", c.maxDepthSeen);
            archiveMeta.put("entriesSeen", c.entriesSeen);
            archiveMeta.put("filesParsed", c.filesParsed);
            archiveMeta.put("filesSkipped", c.filesSkipped);
            archiveMeta.put("pathTraversalDroppedCount", c.pathTraversalDroppedCount);
            archiveMeta.put("totalBytesRead", c.totalBytesRead);
            archiveMeta.put("hardFailReason", "ARCHIVE_NESTING_TOO_DEEP");
            throw new HardFailException("ARCHIVE_NESTING_TOO_DEEP", deep);
        } finally {
            if (workDir != null) deleteDirQuietly(workDir);
        }
    }

    private void expandArchiveToDisk(
            Path archivePath,
            String containerName,
            String ext,
            String virtualPrefix,
            int depth,
            Path outDir,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs,
            List<Map<String, Object>> files
    ) throws Exception {
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        if (exceededArchiveBudget(c, startNs)) {
            c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
            return;
        }
        if (depth >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
        if ("7z".equalsIgnoreCase(ext)) {
            expand7zToDisk(archivePath, virtualPrefix, depth, outDir, archiveMeta, c, startNs, files);
            return;
        }
        try (InputStream is = Files.newInputStream(archivePath)) {
            expandArchiveStreamToDisk(is, containerName, virtualPrefix, depth, outDir, archiveMeta, c, startNs, files);
        }
    }

    private void expandArchiveStreamToDisk(
            InputStream raw,
            String containerName,
            String virtualPrefix,
            int depth,
            Path outDir,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs,
            List<Map<String, Object>> files
    ) throws Exception {
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        if (exceededArchiveBudget(c, startNs)) {
            c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
            return;
        }

        BufferedInputStream bis = new BufferedInputStream(raw);
        bis.mark(8192);
        InputStream decompressed = bis;
        String compression = null;
        try {
            compression = CompressorStreamFactory.detect(bis);
            bis.reset();
            decompressed = new CompressorStreamFactory().createCompressorInputStream(compression, bis, true);
        } catch (CompressorException ce) {
            try {
                bis.reset();
            } catch (Exception ignore) {
            }
            decompressed = bis;
        }

        BufferedInputStream aisBuf = new BufferedInputStream(decompressed);
        aisBuf.mark(8192);
        String archiveType = null;
        try {
            archiveType = ArchiveStreamFactory.detect(aisBuf);
            aisBuf.reset();
        } catch (ArchiveException ae) {
            try {
                aisBuf.reset();
            } catch (Exception ignore) {
            }
        }

        putIfAbsentWhenNonBlank(archiveMeta, "compression", compression);
        putIfAbsentWhenNonBlank(archiveMeta, "archiveType", archiveType);

        if (archiveType == null) {
            String name = fallbackContainerName(containerName);
            Path target = safeResolveUnder(outDir, name);
            if (target == null) return;
            Files.createDirectories(target.getParent());
            long kept = writeStreamLimited(aisBuf, target, c, startNs);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", virtualPrefix + name);
            item.put("depth", depth);
            String ext = extLowerOrNull(name);
            item.put("ext", ext);
            item.put("sizeBytes", kept);
            item.put("localPath", target.toString());
            item.put("extractionTruncated", isArchiveExtractionTruncated(kept, c));
            files.add(item);
            return;
        }

        if ("7z".equalsIgnoreCase(archiveType)) {
            byte[] bytes = readAllLimited(aisBuf, c, archiveMaxTotalBytes);
            if (bytes.length == 0) return;
            markTotalBytesLimitIfExceeded(c, archiveMaxTotalBytes);
            expand7zBytesToDisk(bytes, virtualPrefix, depth, outDir, archiveMeta, c, startNs, files);
            return;
        }

        ArchiveInputStream archiveIn = null;
        try {
            archiveIn = new ArchiveStreamFactory().createArchiveInputStream(archiveType, aisBuf);
            ArchiveEntry entry;
            while ((entry = archiveIn.getNextEntry()) != null) {
                if (handleArchiveStreamEntry(entry, archiveIn, virtualPrefix, depth, outDir, archiveMeta, c, startNs, files)) break;
            }
        } finally {
            closeArchiveInputStreamQuietly(archiveIn);
        }
    }

    private boolean handleArchiveStreamEntry(
            ArchiveEntry entry,
            ArchiveInputStream archiveIn,
            String virtualPrefix,
            int depth,
            Path outDir,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs,
            List<Map<String, Object>> files
    ) throws Exception {
        if (exceededArchiveBudget(c, startNs)) {
            c.truncatedReason = keepReasonOrDefault(c.truncatedReason, "TIME_LIMIT");
            return true;
        }
        c.entriesSeen++;
        if (c.entriesSeen > archiveMaxEntries) {
            c.truncatedReason = keepReasonOrDefault(c.truncatedReason, "ENTRY_COUNT_LIMIT");
            return true;
        }
        if (entry.isDirectory()) return false;
        String name = entry.getName();
        if (name == null || name.isBlank()) return false;
        String norm = name.trim();
        if (isPathTraversal(norm)) {
            c.pathTraversalDroppedCount++;
            drainEntry(archiveIn, c, startNs);
            return false;
        }
        Path target = safeResolveUnder(outDir, norm);
        if (target == null) {
            c.pathTraversalDroppedCount++;
            drainEntry(archiveIn, c, startNs);
            return false;
        }
        Files.createDirectories(target.getParent());
        long kept = writeStreamLimited(archiveIn, target, c, startNs);
        boolean truncated = isArchiveExtractionTruncated(kept, c);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", virtualPrefix + norm);
        item.put("depth", depth);
        String ext = extLowerOrNull(norm);
        item.put("ext", ext);
        item.put("sizeBytes", kept);
        item.put("localPath", target.toString());
        item.put("extractionTruncated", truncated);
        files.add(item);
        if (markTotalBytesLimitIfExceeded(c, archiveMaxTotalBytes)) return true;
        if (truncated) return false;
        if (ext != null && isArchiveExt(ext)) {
            if (depth + 1 >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
            Path subDir = safeNestedUnpackDir(target, outDir);
            Files.createDirectories(subDir);
            expandArchiveToDisk(target, norm, ext, virtualPrefix + norm + "!/", depth + 1, subDir, archiveMeta, c, startNs, files);
        }
        return false;
    }

    private static void putIfAbsentWhenNonBlank(Map<String, Object> archiveMeta, String key, String value) {
        if (archiveMeta == null || key == null || key.isBlank()) return;
        if (value != null && !value.isBlank()) archiveMeta.putIfAbsent(key, value);
    }

    private static String fallbackContainerName(String containerName) {
        return (containerName == null || containerName.isBlank()) ? ("unknown_" + UUID.randomUUID() + ".bin") : containerName;
    }

    private boolean isArchiveExtractionTruncated(long kept, ArchiveCounters c) {
        long total = c == null ? 0L : c.totalBytesRead;
        return kept >= archiveMaxEntryBytes || total > archiveMaxTotalBytes;
    }

    private boolean markTotalBytesLimitIfExceeded(ArchiveCounters c, long totalLimit) {
        if (c == null || c.totalBytesRead <= totalLimit) return false;
        c.truncatedReason = keepReasonOrDefault(c.truncatedReason, "TOTAL_BYTES_LIMIT");
        return true;
    }

    private static String keepReasonOrDefault(String existing, String fallback) {
        return existing == null ? fallback : existing;
    }

    private static Path safeNestedUnpackDir(Path target, Path outDir) {
        Path subDir = target.getParent().resolve(target.getFileName().toString() + "__unpacked").normalize();
        if (!subDir.startsWith(outDir)) subDir = outDir.resolve("__unpacked_" + UUID.randomUUID()).normalize();
        return subDir;
    }

    private static void closeArchiveInputStreamQuietly(ArchiveInputStream archiveIn) {
        if (archiveIn == null) return;
        try {
            archiveIn.close();
        } catch (Exception ignore) {
        }
    }

    private void expand7zToDisk(
            Path sevenZPath,
            String virtualPrefix,
            int depth,
            Path outDir,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs,
            List<Map<String, Object>> files
    ) throws Exception {
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        archiveMeta.putIfAbsent("archiveType", "7z");
        try (SeekableByteChannel ch = Files.newByteChannel(sevenZPath); SevenZFile sevenZ = new SevenZFile(ch)) {
            SevenZArchiveEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (exceededArchiveBudget(c, startNs)) {
                    c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
                    break;
                }
                c.entriesSeen++;
                if (c.entriesSeen > archiveMaxEntries) {
                    c.truncatedReason = c.truncatedReason == null ? "ENTRY_COUNT_LIMIT" : c.truncatedReason;
                    break;
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }
                String norm = name.trim();
                if (isPathTraversal(norm)) {
                    c.pathTraversalDroppedCount++;
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }
                Path target = safeResolveUnder(outDir, norm);
                if (target == null) {
                    c.pathTraversalDroppedCount++;
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }
                Files.createDirectories(target.getParent());
                long kept = 0L;
                boolean truncated = false;
                try (OutputStream os = Files.newOutputStream(target)) {
                    while (true) {
                        if (exceededArchiveBudget(c, startNs)) {
                            truncated = true;
                            break;
                        }
                        int n = sevenZ.read(buf);
                        if (n < 0) break;
                        c.totalBytesRead += n;
                        if (c.totalBytesRead > archiveMaxTotalBytes) {
                            truncated = true;
                            break;
                        }
                        if (kept < archiveMaxEntryBytes) {
                            long can = Math.min((long) n, archiveMaxEntryBytes - kept);
                            if (can > 0) {
                                os.write(buf, 0, (int) can);
                                kept += can;
                            }
                            if (can < n) truncated = true;
                        } else {
                            truncated = true;
                        }
                    }
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("path", virtualPrefix + norm);
                item.put("depth", depth);
                String ext = extLowerOrNull(norm);
                item.put("ext", ext);
                item.put("sizeBytes", kept);
                item.put("localPath", target.toString());
                item.put("extractionTruncated", truncated);
                files.add(item);

                if (c.totalBytesRead > archiveMaxTotalBytes) {
                    c.truncatedReason = c.truncatedReason == null ? "TOTAL_BYTES_LIMIT" : c.truncatedReason;
                    break;
                }

                if (truncated) continue;
                if (ext != null && isArchiveExt(ext)) {
                    if (depth + 1 >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
                    Path subDir = target.getParent().resolve(target.getFileName().toString() + "__unpacked").normalize();
                    if (!subDir.startsWith(outDir)) subDir = outDir.resolve("__unpacked_" + UUID.randomUUID()).normalize();
                    Files.createDirectories(subDir);
                    expandArchiveToDisk(target, norm, ext, virtualPrefix + norm + "!/", depth + 1, subDir, archiveMeta, c, startNs, files);
                }
            }
        }
    }

    private void expand7zBytesToDisk(
            byte[] bytes,
            String virtualPrefix,
            int depth,
            Path outDir,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs,
            List<Map<String, Object>> files
    ) throws Exception {
        if (bytes == null || bytes.length == 0) return;
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        archiveMeta.putIfAbsent("archiveType", "7z");
        try (SeekableInMemoryByteChannel ch = new SeekableInMemoryByteChannel(bytes); SevenZFile sevenZ = new SevenZFile(ch)) {
            SevenZArchiveEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (exceededArchiveBudget(c, startNs)) {
                    c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
                    break;
                }
                c.entriesSeen++;
                if (c.entriesSeen > archiveMaxEntries) {
                    c.truncatedReason = c.truncatedReason == null ? "ENTRY_COUNT_LIMIT" : c.truncatedReason;
                    break;
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }
                String norm = name.trim();
                if (isPathTraversal(norm)) {
                    c.pathTraversalDroppedCount++;
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }
                Path target = safeResolveUnder(outDir, norm);
                if (target == null) {
                    c.pathTraversalDroppedCount++;
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }
                Files.createDirectories(target.getParent());
                long kept = 0L;
                boolean truncated = false;
                try (OutputStream os = Files.newOutputStream(target)) {
                    while (true) {
                        if (exceededArchiveBudget(c, startNs)) {
                            truncated = true;
                            break;
                        }
                        int n = sevenZ.read(buf);
                        if (n < 0) break;
                        c.totalBytesRead += n;
                        if (c.totalBytesRead > archiveMaxTotalBytes) {
                            truncated = true;
                            break;
                        }
                        if (kept < archiveMaxEntryBytes) {
                            long can = Math.min((long) n, archiveMaxEntryBytes - kept);
                            if (can > 0) {
                                os.write(buf, 0, (int) can);
                                kept += can;
                            }
                            if (can < n) truncated = true;
                        } else {
                            truncated = true;
                        }
                    }
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("path", virtualPrefix + norm);
                item.put("depth", depth);
                String ext = extLowerOrNull(norm);
                item.put("ext", ext);
                item.put("sizeBytes", kept);
                item.put("localPath", target.toString());
                item.put("extractionTruncated", truncated);
                files.add(item);

                if (c.totalBytesRead > archiveMaxTotalBytes) {
                    c.truncatedReason = c.truncatedReason == null ? "TOTAL_BYTES_LIMIT" : c.truncatedReason;
                    break;
                }

                if (truncated) continue;
                if (ext != null && isArchiveExt(ext)) {
                    if (depth + 1 >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
                    Path subDir = target.getParent().resolve(target.getFileName().toString() + "__unpacked").normalize();
                    if (!subDir.startsWith(outDir)) subDir = outDir.resolve("__unpacked_" + UUID.randomUUID()).normalize();
                    Files.createDirectories(subDir);
                    expandArchiveToDisk(target, norm, ext, virtualPrefix + norm + "!/", depth + 1, subDir, archiveMeta, c, startNs, files);
                }
            }
        }
    }

    private static Path safeResolveUnder(Path root, String rel) {
        if (root == null) return null;
        if (rel == null || rel.isBlank()) return null;
        Path p;
        try {
            p = root.resolve(rel.replace('\\', '/')).normalize();
        } catch (Exception e) {
            return null;
        }
        return p.startsWith(root) ? p : null;
    }

    private long writeStreamLimited(InputStream in, Path target, ArchiveCounters c, long startNs) throws Exception {
        byte[] buf = new byte[8192];
        long kept = 0L;
        try (OutputStream os = Files.newOutputStream(target)) {
            while (true) {
                if (exceededArchiveBudget(c, startNs)) break;
                int n = in.read(buf);
                if (n < 0) break;
                c.totalBytesRead += n;
                if (c.totalBytesRead > archiveMaxTotalBytes) break;
                if (kept < archiveMaxEntryBytes) {
                    long can = Math.min((long) n, archiveMaxEntryBytes - kept);
                    if (can > 0) {
                        os.write(buf, 0, (int) can);
                        kept += can;
                    }
                }
            }
        }
        return kept;
    }

    private static void appendExtractedFileBlock(StringBuilder out, String virtualPath, String text) {
        if (out == null) return;
        String p = virtualPath == null ? "" : virtualPath.trim();
        String t = text == null ? "" : text.trim();
        if (t.isBlank()) return;
        out.append("FILE: ").append(p).append('\n');
        out.append(t).append('\n');
        out.append('\n');
    }

    private static boolean isSupportedInnerExtForExtraction(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String e = ext.trim().toLowerCase(Locale.ROOT);
        if (isArchiveExt(e)) return false;
        return e.equals("pdf")
                || e.equals("txt") || e.equals("md") || e.equals("markdown") || e.equals("csv") || e.equals("json")
                || e.equals("html") || e.equals("htm")
                || e.equals("doc") || e.equals("docx")
                || e.equals("xls") || e.equals("xlsx")
                || e.equals("ppt") || e.equals("pptx")
                || e.equals("epub") || e.equals("mobi");
    }

    private static void deleteDirQuietly(Path dir) {
        if (dir == null) return;
        try {
            if (!Files.exists(dir)) return;
            List<Path> all;
            try (var s = Files.walk(dir)) {
                all = s.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path p : all) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String extractArchiveFromStream(
            InputStream raw,
            String containerName,
            int depth,
            int maxChars,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs
    ) throws Exception {
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        if (exceededArchiveBudget(c, startNs)) {
            c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
            return "";
        }

        BufferedInputStream bis = new BufferedInputStream(raw);
        bis.mark(8192);
        InputStream decompressed = bis;
        String compression = null;
        try {
            compression = CompressorStreamFactory.detect(bis);
            bis.reset();
            decompressed = new CompressorStreamFactory().createCompressorInputStream(compression, bis, true);
        } catch (CompressorException ce) {
            try {
                bis.reset();
            } catch (Exception ignore) {
            }
            decompressed = bis;
        }

        BufferedInputStream aisBuf = new BufferedInputStream(decompressed);
        aisBuf.mark(8192);
        String archiveType = null;
        try {
            archiveType = ArchiveStreamFactory.detect(aisBuf);
            aisBuf.reset();
        } catch (ArchiveException ae) {
            try {
                aisBuf.reset();
            } catch (Exception ignore) {
            }
        }

        if (archiveType == null) {
            byte[] bytes = readAllLimited(aisBuf, c, archiveMaxTotalBytes);
            if (bytes.length == 0) return "";
            String txt = extractSingleBytesAsText(containerName, bytes, maxChars);
            return truncate(txt, maxChars);
        }
        if ("7z".equalsIgnoreCase(archiveType)) {
            byte[] bytes = readAllLimited(aisBuf, c, archiveMaxTotalBytes);
            if (bytes.length == 0) return "";
            if (c.totalBytesRead > archiveMaxTotalBytes) {
                c.truncatedReason = c.truncatedReason == null ? "TOTAL_BYTES_LIMIT" : c.truncatedReason;
                return "";
            }
            return extract7zFromBytes(bytes, containerName, depth, maxChars, archiveMeta, c, startNs);
        }

        StringBuilder out = new StringBuilder();
        if (compression != null && !compression.isBlank()) {
            archiveMeta.putIfAbsent("compression", compression);
        }
        ArchiveInputStream archiveIn = null;
        try {
            archiveIn = new ArchiveStreamFactory().createArchiveInputStream(archiveType, aisBuf);
            ArchiveEntry entry;
            while ((entry = archiveIn.getNextEntry()) != null) {
                if (exceededArchiveBudget(c, startNs)) {
                    c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
                    break;
                }
                c.entriesSeen++;
                if (c.entriesSeen > archiveMaxEntries) {
                    c.truncatedReason = c.truncatedReason == null ? "ENTRY_COUNT_LIMIT" : c.truncatedReason;
                    break;
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    c.filesSkipped++;
                    continue;
                }
                String norm = name.trim();
                if (isPathTraversal(norm)) {
                    c.pathTraversalDroppedCount++;
                    c.filesSkipped++;
                    drainEntry(archiveIn, c, startNs);
                    continue;
                }

                byte[] data = readEntryLimited(archiveIn, c, startNs);
                if (data.length == 0) {
                    c.filesSkipped++;
                    continue;
                }

                String ext = extLowerOrNull(norm);
                boolean nestedArchive = false;
                if (ext != null && !ext.isBlank()) {
                    nestedArchive = isArchiveExt(ext);
                } else {
                    nestedArchive = looksLikeArchiveBytes(data);
                }
                try {
                    if (nestedArchive) {
                        if (depth + 1 >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
                        String inner = extractArchiveFromStream(new ByteArrayInputStream(data), norm, depth + 1, maxChars, archiveMeta, c, startNs);
                        if (inner != null && !inner.isBlank()) {
                            c.filesParsed++;
                            appendArchiveEntryBlock(out, norm, inner);
                        } else {
                            c.filesSkipped++;
                        }
                    } else {
                        String txt = extractEntryBytesAsText(norm, ext, data, maxChars);
                        if (txt != null && !txt.isBlank()) {
                            c.filesParsed++;
                            appendArchiveEntryBlock(out, norm, txt);
                            recordArchiveParsedEntry(archiveMeta, norm, depth, txt);
                        } else {
                            c.filesSkipped++;
                        }
                    }
                } catch (Exception ex) {
                    if (ex instanceof ArchiveNestingTooDeepException deep) throw deep;
                    if (ex instanceof HardFailException hf) throw hf;
                    c.filesSkipped++;
                    recordArchiveEntryError(archiveMeta, norm, ex);
                }

                if (out.length() >= maxChars) {
                    c.truncatedReason = c.truncatedReason == null ? "TEXT_CHAR_LIMIT" : c.truncatedReason;
                    break;
                }
                if (c.totalBytesRead > archiveMaxTotalBytes) {
                    c.truncatedReason = c.truncatedReason == null ? "TOTAL_BYTES_LIMIT" : c.truncatedReason;
                    break;
                }
            }
        } finally {
            if (archiveIn != null) {
                try {
                    archiveIn.close();
                } catch (Exception ignore) {
                }
            }
        }

        String merged = out.toString().trim();
        return truncate(merged, maxChars);
    }

    private String extract7zFromPath(
            Path path,
            String containerName,
            int depth,
            int maxChars,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs
    ) throws Exception {
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        archiveMeta.putIfAbsent("archiveType", "7z");

        StringBuilder out = new StringBuilder();
        try (SeekableByteChannel ch = Files.newByteChannel(path); SevenZFile sevenZ = new SevenZFile(ch)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (exceededArchiveBudget(c, startNs)) {
                    c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
                    break;
                }
                c.entriesSeen++;
                if (c.entriesSeen > archiveMaxEntries) {
                    c.truncatedReason = c.truncatedReason == null ? "ENTRY_COUNT_LIMIT" : c.truncatedReason;
                    break;
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    c.filesSkipped++;
                    continue;
                }
                String norm = name.trim();
                if (isPathTraversal(norm)) {
                    c.pathTraversalDroppedCount++;
                    c.filesSkipped++;
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }

                byte[] data = read7zEntryLimited(sevenZ, c, startNs);
                if (data.length == 0) {
                    c.filesSkipped++;
                    continue;
                }

                String ext = extLowerOrNull(norm);
                boolean nestedArchive = false;
                if (ext != null && !ext.isBlank()) {
                    nestedArchive = isArchiveExt(ext);
                } else {
                    nestedArchive = looksLikeArchiveBytes(data);
                }
                try {
                    if (nestedArchive) {
                        if (depth + 1 >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
                        String inner;
                        if ("7z".equalsIgnoreCase(ext) || looksLike7zBytes(data)) {
                            inner = extract7zFromBytes(data, norm, depth + 1, maxChars, archiveMeta, c, startNs);
                        } else {
                            inner = extractArchiveFromStream(new ByteArrayInputStream(data), norm, depth + 1, maxChars, archiveMeta, c, startNs);
                        }
                        if (inner != null && !inner.isBlank()) {
                            c.filesParsed++;
                            appendArchiveEntryBlock(out, norm, inner);
                        } else {
                            c.filesSkipped++;
                        }
                    } else {
                        String txt = extractEntryBytesAsText(norm, ext, data, maxChars);
                        if (txt != null && !txt.isBlank()) {
                            c.filesParsed++;
                            appendArchiveEntryBlock(out, norm, txt);
                            recordArchiveParsedEntry(archiveMeta, norm, depth, txt);
                        } else {
                            c.filesSkipped++;
                        }
                    }
                } catch (Exception ex) {
                    if (ex instanceof ArchiveNestingTooDeepException deep) throw deep;
                    if (ex instanceof HardFailException hf) throw hf;
                    c.filesSkipped++;
                    recordArchiveEntryError(archiveMeta, norm, ex);
                }

                if (out.length() >= maxChars) {
                    c.truncatedReason = c.truncatedReason == null ? "TEXT_CHAR_LIMIT" : c.truncatedReason;
                    break;
                }
                if (c.totalBytesRead > archiveMaxTotalBytes) {
                    c.truncatedReason = c.truncatedReason == null ? "TOTAL_BYTES_LIMIT" : c.truncatedReason;
                    break;
                }
            }
        }
        String merged = out.toString().trim();
        return truncate(merged, maxChars);
    }

    private String extract7zFromBytes(
            byte[] bytes,
            String containerName,
            int depth,
            int maxChars,
            Map<String, Object> archiveMeta,
            ArchiveCounters c,
            long startNs
    ) throws Exception {
        if (bytes == null || bytes.length == 0) return "";
        c.maxDepthSeen = Math.max(c.maxDepthSeen, depth);
        archiveMeta.putIfAbsent("archiveType", "7z");

        StringBuilder out = new StringBuilder();
        try (SeekableInMemoryByteChannel ch = new SeekableInMemoryByteChannel(bytes); SevenZFile sevenZ = new SevenZFile(ch)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (exceededArchiveBudget(c, startNs)) {
                    c.truncatedReason = c.truncatedReason == null ? "TIME_LIMIT" : c.truncatedReason;
                    break;
                }
                c.entriesSeen++;
                if (c.entriesSeen > archiveMaxEntries) {
                    c.truncatedReason = c.truncatedReason == null ? "ENTRY_COUNT_LIMIT" : c.truncatedReason;
                    break;
                }
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    c.filesSkipped++;
                    continue;
                }
                String norm = name.trim();
                if (isPathTraversal(norm)) {
                    c.pathTraversalDroppedCount++;
                    c.filesSkipped++;
                    drain7zEntry(sevenZ, c, startNs);
                    continue;
                }

                byte[] data = read7zEntryLimited(sevenZ, c, startNs);
                if (data.length == 0) {
                    c.filesSkipped++;
                    continue;
                }

                String ext = extLowerOrNull(norm);
                boolean nestedArchive = false;
                if (ext != null && !ext.isBlank()) {
                    nestedArchive = isArchiveExt(ext);
                } else {
                    nestedArchive = looksLikeArchiveBytes(data);
                }
                try {
                    if (nestedArchive) {
                        if (depth + 1 >= archiveMaxDepth) throw new ArchiveNestingTooDeepException();
                        String inner;
                        if ("7z".equalsIgnoreCase(ext) || looksLike7zBytes(data)) {
                            inner = extract7zFromBytes(data, norm, depth + 1, maxChars, archiveMeta, c, startNs);
                        } else {
                            inner = extractArchiveFromStream(new ByteArrayInputStream(data), norm, depth + 1, maxChars, archiveMeta, c, startNs);
                        }
                        if (inner != null && !inner.isBlank()) {
                            c.filesParsed++;
                            appendArchiveEntryBlock(out, norm, inner);
                        } else {
                            c.filesSkipped++;
                        }
                    } else {
                        String txt = extractEntryBytesAsText(norm, ext, data, maxChars);
                        if (txt != null && !txt.isBlank()) {
                            c.filesParsed++;
                            appendArchiveEntryBlock(out, norm, txt);
                            recordArchiveParsedEntry(archiveMeta, norm, depth, txt);
                        } else {
                            c.filesSkipped++;
                        }
                    }
                } catch (Exception ex) {
                    if (ex instanceof ArchiveNestingTooDeepException deep) throw deep;
                    if (ex instanceof HardFailException hf) throw hf;
                    c.filesSkipped++;
                    recordArchiveEntryError(archiveMeta, norm, ex);
                }

                if (out.length() >= maxChars) {
                    c.truncatedReason = c.truncatedReason == null ? "TEXT_CHAR_LIMIT" : c.truncatedReason;
                    break;
                }
                if (c.totalBytesRead > archiveMaxTotalBytes) {
                    c.truncatedReason = c.truncatedReason == null ? "TOTAL_BYTES_LIMIT" : c.truncatedReason;
                    break;
                }
            }
        }
        String merged = out.toString().trim();
        return truncate(merged, maxChars);
    }

    private byte[] read7zEntryLimited(SevenZFile sevenZ, ArchiveCounters c, long startNs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long kept = 0L;
        while (true) {
            if (exceededArchiveBudget(c, startNs)) break;
            int n = sevenZ.read(buf);
            if (n < 0) break;
            c.totalBytesRead += n;
            if (c.totalBytesRead > archiveMaxTotalBytes) break;
            if (kept < archiveMaxEntryBytes) {
                long can = Math.min((long) n, archiveMaxEntryBytes - kept);
                if (can > 0) {
                    baos.write(buf, 0, (int) can);
                    kept += can;
                }
            }
        }
        return baos.toByteArray();
    }

    private void drain7zEntry(SevenZFile sevenZ, ArchiveCounters c, long startNs) throws Exception {
        byte[] buf = new byte[8192];
        while (true) {
            if (exceededArchiveBudget(c, startNs)) break;
            int n = sevenZ.read(buf);
            if (n < 0) break;
            c.totalBytesRead += n;
            if (c.totalBytesRead > archiveMaxTotalBytes) break;
        }
    }

    private static boolean looksLike7zBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 6) return false;
        return (bytes[0] == 0x37) && (bytes[1] == 0x7A) && ((bytes[2] & 0xFF) == 0xBC) && ((bytes[3] & 0xFF) == 0xAF) && (bytes[4] == 0x27) && (bytes[5] == 0x1C);
    }

    private void appendArchiveEntryBlock(StringBuilder out, String entryPath, String text) {
        if (out == null) return;
        String p = entryPath == null ? "" : entryPath.trim();
        String t = text == null ? "" : text.trim();
        if (t.isBlank()) return;
        out.append("FILE: ").append(p).append('\n');
        out.append(t).append('\n');
        out.append('\n');
    }

    private boolean exceededArchiveBudget(ArchiveCounters c, long startNs) {
        if (c == null) return false;
        if (archiveMaxTotalMillis <= 0) return false;
        long ms = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
        return ms >= archiveMaxTotalMillis;
    }

    private byte[] readEntryLimited(InputStream in, ArchiveCounters c, long startNs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long kept = 0L;
        while (true) {
            if (exceededArchiveBudget(c, startNs)) break;
            int n = in.read(buf);
            if (n < 0) break;
            c.totalBytesRead += n;
            if (c.totalBytesRead > archiveMaxTotalBytes) break;
            if (kept < archiveMaxEntryBytes) {
                long can = Math.min((long) n, archiveMaxEntryBytes - kept);
                if (can > 0) {
                    baos.write(buf, 0, (int) can);
                    kept += can;
                }
            }
        }
        return baos.toByteArray();
    }

    private void drainEntry(InputStream in, ArchiveCounters c, long startNs) throws Exception {
        byte[] buf = new byte[8192];
        while (true) {
            if (exceededArchiveBudget(c, startNs)) break;
            int n = in.read(buf);
            if (n < 0) break;
            c.totalBytesRead += n;
            if (c.totalBytesRead > archiveMaxTotalBytes) break;
        }
    }

    private byte[] readAllLimited(InputStream in, ArchiveCounters c, long totalLimit) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (true) {
            int n = in.read(buf);
            if (n < 0) break;
            c.totalBytesRead += n;
            if (c.totalBytesRead > totalLimit) break;
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static boolean looksLikeArchiveBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        if (b0 == 0x50 && b1 == 0x4B) return true;
        if (b0 == 0x37 && b1 == 0x7A && b2 == 0xBC && b3 == 0xAF) return true;
        if (b0 == 0x1F && b1 == 0x8B) return true;
        if (b0 == 0x42 && b1 == 0x5A) return true;
        if (b0 == 0xFD && b1 == 0x37 && b2 == 0x7A && b3 == 0x58) return true;
        return false;
    }

    private static boolean isPathTraversal(String name) {
        if (name == null) return true;
        String s = name.trim();
        if (s.isBlank()) return true;
        if (s.startsWith("/") || s.startsWith("\\")) return true;
        if (s.matches("^[a-zA-Z]:.*")) return true;
        return s.contains("..\\") || s.contains("../") || s.equals("..");
    }

    private String extractEntryBytesAsText(String entryName, String ext, byte[] bytes, int maxChars) throws Exception {
        if (bytes == null || bytes.length == 0) return "";
        String e = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        if (e.equals("pdf")) {
            return extractPdf(new ByteArrayInputStream(bytes), maxChars, null);
        }
        if (e.equals("txt") || e.equals("md") || e.equals("markdown") || e.equals("csv") || e.equals("json") || e.equals("xml") || e.equals("yaml") || e.equals("yml")) {
            return truncate(new String(bytes, StandardCharsets.UTF_8), maxChars);
        }
        if (e.equals("html") || e.equals("htm")) {
            String html = truncate(new String(bytes, StandardCharsets.UTF_8), maxChars * 2);
            return truncate(stripHtmlToText(html), maxChars);
        }
        if (e.equals("epub") || e.equals("mobi")) {
            return extractWithTika(new ByteArrayInputStream(bytes), maxChars, null);
        }
        if (isOfficeExt(e)) {
            return extractOffice(new ByteArrayInputStream(bytes), maxChars);
        }
        return extractWithTika(new ByteArrayInputStream(bytes), maxChars, null);
    }

    private String extractSingleBytesAsText(String name, byte[] bytes, int maxChars) throws Exception {
        return extractWithTika(new ByteArrayInputStream(bytes), maxChars, null);
    }

    private static String stripHtmlToText(String html) {
        if (html == null) return "";
        String txt = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return txt.replaceAll("\\s+", " ").trim();
    }

    private static String extractPdf(InputStream is, int maxChars, Map<String, Object> meta) throws Exception {
        try (PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
            if (meta != null) meta.put("pages", doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            String txt = stripper.getText(doc);
            return truncate(txt, maxChars);
        }
    }

    private static String extractWithTika(InputStream is, int maxChars, Map<String, Object> meta) throws Exception {
        Tika tika = new Tika();
        String txt = tika.parseToString(is);
        return truncate(txt, maxChars);
    }

    private static String extractOffice(InputStream is, int maxChars) throws Exception {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(is)) {
            String txt = extractor.getText();
            return truncate(txt, maxChars);
        }
    }

    private static boolean isArchiveExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String e = ext.trim().toLowerCase(Locale.ROOT);
        return e.equals("zip") || e.equals("jar") || e.equals("war") || e.equals("ear")
                || e.equals("7z") || e.equals("tar") || e.equals("tgz") || e.equals("gz")
                || e.equals("bz2") || e.equals("tbz2") || e.equals("xz") || e.equals("txz");
    }

    private static boolean isOfficeExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        String e = ext.trim().toLowerCase(Locale.ROOT);
        return e.equals("doc") || e.equals("docx") || e.equals("dot") || e.equals("dotx")
                || e.equals("ppt") || e.equals("pptx") || e.equals("pps") || e.equals("ppsx")
                || e.equals("xls") || e.equals("xlsx") || e.equals("xlt") || e.equals("xltx");
    }

    private static void recordArchiveEntryError(Map<String, Object> archiveMeta, String entryPath, Exception ex) {
        if (archiveMeta == null) return;
        if (entryPath == null || entryPath.isBlank()) return;
        Object cur = archiveMeta.get("entryErrors");
        List<Map<String, Object>> list;
        if (cur instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) l;
            list = cast;
        } else {
            list = new ArrayList<>();
            archiveMeta.put("entryErrors", list);
        }
        if (list.size() >= 5) {
            archiveMeta.put("entryErrorsTruncated", true);
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", entryPath);
        item.put("error", safeMsg(ex));
        list.add(item);
    }

    private static void recordArchiveParsedEntry(Map<String, Object> archiveMeta, String entryPath, int depth, String text) {
        if (archiveMeta == null) return;
        if (entryPath == null || entryPath.isBlank()) return;
        Object cur = archiveMeta.get("parsedEntries");
        List<Map<String, Object>> list;
        if (cur instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) l;
            list = cast;
        } else {
            list = new ArrayList<>();
            archiveMeta.put("parsedEntries", list);
        }
        if (list.size() >= 50) {
            archiveMeta.put("parsedEntriesTruncated", true);
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", entryPath);
        item.put("depth", depth);
        item.put("chars", text == null ? 0 : text.length());
        list.add(item);
    }

    private static class ArchiveCounters {
        int maxDepthSeen = 0;
        long entriesSeen = 0L;
        long filesParsed = 0L;
        long filesSkipped = 0L;
        long pathTraversalDroppedCount = 0L;
        long totalBytesRead = 0L;
        String truncatedReason = null;
    }

    private static class ImageBudget {
        final int maxCount;
        final long maxTotalBytes;
        int count = 0;
        long totalBytes = 0L;

        ImageBudget(int maxCount, long maxTotalBytes) {
            this.maxCount = Math.max(0, maxCount);
            this.maxTotalBytes = Math.max(0L, maxTotalBytes);
        }

        int peekNextIndex() {
            return count + 1;
        }

        boolean canAdd(long bytes) {
            long b = Math.max(0L, bytes);
            if (maxCount > 0 && count >= maxCount) return false;
            if (maxTotalBytes > 0 && totalBytes + b > maxTotalBytes) return false;
            return true;
        }

        int consume(long bytes) {
            long b = Math.max(0L, bytes);
            count += 1;
            if (maxTotalBytes > 0) totalBytes += b;
            return count;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> takeList(Object v) {
        if (v instanceof List<?> l) return (List<Map<String, Object>>) l;
        return List.of();
    }

    private static class ArchiveNestingTooDeepException extends RuntimeException {
    }

    private static class HardFailException extends RuntimeException {
        final String reason;

        HardFailException(String reason, Throwable cause) {
            super(reason, cause);
            this.reason = reason;
        }
    }

    private static String extractPdf(Path path, int maxChars, Map<String, Object> meta) throws Exception {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            meta.put("pages", doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            String txt = stripper.getText(doc);
            return truncate(txt, maxChars);
        }
    }

    private static String extractWithTika(Path path, int maxChars, Map<String, Object> meta) throws Exception {
        Tika tika = new Tika();
        String txt = tika.parseToString(path.toFile());
        String detected = tika.detect(path.toFile());
        if (detected != null && !detected.isBlank()) meta.put("tikaDetectedType", detected);
        return truncate(txt, maxChars);
    }

    private static String extractOffice(Path path, int maxChars) throws Exception {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(path.toFile())) {
            String txt = extractor.getText();
            return truncate(txt, maxChars);
        }
    }

    private static String extractPlain(Path path, int maxChars) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String txt = new String(bytes, StandardCharsets.UTF_8);
        return truncate(txt, maxChars);
    }

    private static String extractHtml(Path path, int maxChars) throws Exception {
        String html = extractPlain(path, maxChars * 2);
        String txt = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        txt = txt.replaceAll("\\s+", " ").trim();
        return truncate(txt, maxChars);
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return null;
        int m = Math.max(0, maxChars);
        if (m == 0) return "";
        if (s.length() <= m) return s;
        return s.substring(0, m);
    }

    private static long estimateTokens(String text) {
        if (text == null) return 0L;
        String t = text.trim();
        if (t.isEmpty()) return 0L;
        return Math.max(1L, (t.length() + 3L) / 4L);
    }

    private static void fillDerivedImageCount(Map<String, Object> meta, String finalText) {
        if (meta == null) return;
        if (meta.get("imageCount") != null) return;
        Integer pages = null;
        Object pv = meta.get("pages");
        if (pv instanceof Number n) pages = n.intValue();
        if (pv != null && pages == null) {
            try {
                pages = Integer.parseInt(pv.toString().trim());
            } catch (Exception ignored) {
            }
        }
        String t = finalText == null ? "" : finalText.trim();
        if (pages != null && pages > 0 && t.isEmpty()) {
            meta.put("imageCount", pages);
        } else {
            meta.put("imageCount", 0);
        }
    }

    private static boolean isImageExt(String ext) {
        if (ext == null || ext.isBlank()) return false;
        return ext.equals("bmp") || ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("webp");
    }

    private List<Map<String, Object>> extractImages(Path path, String ext, Map<String, Object> meta, Long fileAssetId, String extractedText, ImageBudget budget) {
        String e = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        if (e.isBlank()) return List.of();
        if (isImageExt(e)) return List.of();
        if (e.equals("txt") || e.equals("md") || e.equals("markdown") || e.equals("html") || e.equals("htm")) {
            meta.put("imagesExtractionMode", "NONE");
            return List.of();
        }
        if (e.equals("csv") || e.equals("json")) {
            meta.put("imagesExtractionMode", "NONE");
            return List.of();
        }
        if (e.equals("pdf")) {
            return extractPdfImages(path, meta, fileAssetId, extractedText, budget);
        }
        if (e.equals("docx")) {
            return extractDocxImages(path, meta, fileAssetId, budget);
        }
        if (e.equals("xlsx")) {
            return extractXlsxImages(path, meta, fileAssetId, budget);
        }
        if (e.equals("pptx")) {
            return extractPptxImages(path, meta, fileAssetId, budget);
        }
        if (e.equals("ppt")) {
            return extractPptImages(path, meta, fileAssetId, budget);
        }
        if (e.equals("epub")) {
            return extractEpubImages(path, meta, fileAssetId, budget);
        }
        if (e.equals("mobi")) {
            return extractMobiImagesWithTika(path, meta, fileAssetId, budget);
        }
        meta.put("imagesExtractionMode", "UNSUPPORTED");
        return List.of();
    }

    private List<Map<String, Object>> extractDocxImages(Path path, Map<String, Object> meta, Long fileAssetId, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream is = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(is)) {
            List<XWPFPictureData> pics = doc.getAllPictures();
            if (pics == null || pics.isEmpty()) {
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }
            meta.put("imagesExtractionMode", "DOCX_EMBEDDED");
            for (XWPFPictureData pic : pics) {
                if (pic == null) continue;
                byte[] bytes = pic.getData();
                if (bytes == null || bytes.length == 0) continue;
                if (budget != null && !budget.canAdd(bytes.length)) break;
                int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();
                String ext = pic.suggestFileExtension();
                String name = (pic.getFileName() == null || pic.getFileName().isBlank())
                        ? ("docx_image_" + idx + "." + (ext == null ? "png" : ext))
                        : pic.getFileName();
                String mime = null;
                try {
                    mime = pic.getPackagePart() == null ? null : pic.getPackagePart().getContentType();
                } catch (Exception ignored) {
                }
                Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, name, mime, fileAssetId);
                if (saved == null) continue;
                if (budget != null) idx = budget.consume(bytes.length);
                out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
            }
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
        return out;
    }

    private List<Map<String, Object>> extractXlsxImages(Path path, Map<String, Object> meta, Long fileAssetId, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(path.toFile())) {
            if (!(wb instanceof XSSFWorkbook xssf)) {
                meta.put("imagesExtractionMode", "UNSUPPORTED");
                return List.of();
            }
            List<XSSFPictureData> pics = xssf.getAllPictures();
            if (pics == null || pics.isEmpty()) {
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }
            meta.put("imagesExtractionMode", "XLSX_EMBEDDED");
            for (XSSFPictureData pic : pics) {
                if (pic == null) continue;
                byte[] bytes = pic.getData();
                if (bytes == null || bytes.length == 0) continue;
                if (budget != null && !budget.canAdd(bytes.length)) break;
                int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();
                String ext = pic.suggestFileExtension();
                String name = "xlsx_image_" + idx + "." + (ext == null ? "png" : ext);
                String mime = pic.getPackagePart() == null ? null : pic.getPackagePart().getContentType();
                Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, name, mime, fileAssetId);
                if (saved == null) continue;
                if (budget != null) idx = budget.consume(bytes.length);
                out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
            }
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
        return out;
    }

    private List<Map<String, Object>> extractPptxImages(Path path, Map<String, Object> meta, Long fileAssetId, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream is = Files.newInputStream(path); XMLSlideShow pptx = new XMLSlideShow(is)) {
            List<XSLFPictureData> pics = pptx.getPictureData();
            if (pics == null || pics.isEmpty()) {
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }
            meta.put("imagesExtractionMode", "PPTX_EMBEDDED");
            for (XSLFPictureData pic : pics) {
                if (pic == null) continue;
                byte[] bytes = pic.getData();
                if (bytes == null || bytes.length == 0) continue;
                if (budget != null && !budget.canAdd(bytes.length)) break;
                int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();

                String ext = null;
                try {
                    ext = pic.suggestFileExtension();
                } catch (Exception ignored) {
                }
                String name = "pptx_image_" + idx + "." + (ext == null ? "png" : ext);

                String mime = null;
                try {
                    mime = pic.getContentType();
                } catch (Exception ignored) {
                }
                if (mime == null) mime = guessMimeFromExt(ext);

                Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, name, mime, fileAssetId);
                if (saved == null) continue;
                if (budget != null) idx = budget.consume(bytes.length);
                out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
            }
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
        return out;
    }

    private List<Map<String, Object>> extractPptImages(Path path, Map<String, Object> meta, Long fileAssetId, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream is = Files.newInputStream(path); HSLFSlideShow ppt = new HSLFSlideShow(is)) {
            List<HSLFPictureData> pics = ppt.getPictureData();
            if (pics == null || pics.isEmpty()) {
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }
            meta.put("imagesExtractionMode", "PPT_EMBEDDED");
            for (HSLFPictureData pic : pics) {
                if (pic == null) continue;
                byte[] bytes = pic.getData();
                if (bytes == null || bytes.length == 0) continue;
                if (budget != null && !budget.canAdd(bytes.length)) break;
                int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();

                String ext = null;
                String mime = null;
                try {
                    var type = pic.getType();
                    if (type != null) {
                        ext = type.extension;
                        mime = type.contentType;
                    }
                } catch (Exception ignored) {
                }
                if (ext == null || ext.isBlank()) ext = "png";
                if (mime == null) mime = guessMimeFromExt(ext);

                String name = "ppt_image_" + idx + "." + ext;
                Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, name, mime, fileAssetId);
                if (saved == null) continue;
                if (budget != null) idx = budget.consume(bytes.length);
                out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
            }
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
        return out;
    }

    private List<Map<String, Object>> extractPdfImages(Path path, Map<String, Object> meta, Long fileAssetId, String extractedText, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            meta.put("pages", doc.getNumberOfPages());

            boolean found = false;
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (budget != null && !budget.canAdd(1)) break;
                PDPage page = doc.getPage(i);
                PDResources res = page == null ? null : page.getResources();
                if (res == null) continue;
                for (var name : res.getXObjectNames()) {
                    if (budget != null && !budget.canAdd(1)) break;
                    PDXObject xo = res.getXObject(name);
                    if (!(xo instanceof PDImageXObject img)) continue;
                    BufferedImage bi = img.getImage();
                    if (bi == null) continue;
                    byte[] bytes = bufferedImageToPng(bi);
                    if (bytes == null || bytes.length == 0) continue;
                    if (budget != null && !budget.canAdd(bytes.length)) break;
                    int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();
                    Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, "pdf_image_" + idx + ".png", "image/png", fileAssetId);
                    if (saved == null) continue;
                    if (budget != null) idx = budget.consume(bytes.length);
                    out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
                    found = true;
                }
            }

            String t = extractedText == null ? "" : extractedText.trim();
            if (found) {
                meta.put("imagesExtractionMode", "PDF_XOBJECT");
                return out;
            }

            if (!t.isEmpty()) {
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }

            int pages = doc.getNumberOfPages();
            int maxPages = Math.max(0, pdfRenderMaxPages);
            int toRender = maxPages == 0 ? 0 : Math.min(pages, maxPages);
            if (toRender <= 0) {
                meta.put("imagesExtractionMode", "PDF_RENDER_SKIPPED");
                return List.of();
            }

            meta.put("imagesExtractionMode", "PDF_RENDER");
            PDFRenderer renderer = new PDFRenderer(doc);
            int dpi = Math.max(36, Math.min(600, pdfRenderDpi));
            for (int i = 0; i < toRender; i++) {
                if (budget != null && !budget.canAdd(1)) break;
                BufferedImage bi = renderer.renderImageWithDPI(i, dpi);
                byte[] bytes = bufferedImageToPng(bi);
                if (bytes == null || bytes.length == 0) continue;
                if (budget != null && !budget.canAdd(bytes.length)) break;
                int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();
                Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, "pdf_page_" + (i + 1) + ".png", "image/png", fileAssetId);
                if (saved == null) continue;
                if (budget != null) idx = budget.consume(bytes.length);
                out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
            }
            return out;
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
    }

    private List<Map<String, Object>> extractEpubImages(Path path, Map<String, Object> meta, Long fileAssetId, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (ZipFile zf = new ZipFile(path.toFile())) {
            List<? extends ZipEntry> entries = zf.stream()
                    .filter(en -> en != null && !en.isDirectory())
                    .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (entries.isEmpty()) {
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }
            meta.put("imagesExtractionMode", "EPUB_ZIP");
            for (ZipEntry en : entries) {
                if (budget != null && !budget.canAdd(0)) break;
                String name = en.getName();
                String ext = extLowerOrNull(name);
                if (!isImageExt(ext == null ? "" : ext)) continue;
                long size = en.getSize();
                if (size > 0 && derivedUploadStorageService.getMaxImageBytes() > 0 && size > derivedUploadStorageService.getMaxImageBytes()) continue;
                if (size > 0 && budget != null && !budget.canAdd(size)) break;
                try (InputStream is = zf.getInputStream(en)) {
                    byte[] bytes = is.readAllBytes();
                    if (bytes.length == 0) continue;
                    if (budget != null && !budget.canAdd(bytes.length)) break;
                    int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();
                    String mime = guessMimeFromExt(ext);
                    Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, "epub_" + Path.of(name).getFileName(), mime, fileAssetId);
                    if (saved == null) continue;
                    if (budget != null) idx = budget.consume(bytes.length);
                    out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
                }
            }
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
        return out;
    }

    private List<Map<String, Object>> extractMobiImagesWithTika(Path path, Map<String, Object> meta, Long fileAssetId, ImageBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            AutoDetectParser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();
            context.set(org.apache.tika.extractor.EmbeddedDocumentExtractor.class, new org.apache.tika.extractor.EmbeddedDocumentExtractor() {
                @Override
                public boolean shouldParseEmbedded(Metadata metadata) {
                    if (metadata == null) return false;
                    String ct = metadata.get(Metadata.CONTENT_TYPE);
                    return ct != null && ct.toLowerCase(Locale.ROOT).startsWith("image/");
                }

                @Override
                public void parseEmbedded(InputStream stream, org.xml.sax.ContentHandler handler, Metadata metadata, boolean outputHtml) {
                    if (stream == null) return;
                    if (isBudgetExceeded(budget, 0)) return;
                    try {
                        byte[] bytes = stream.readAllBytes();
                        if (bytes.length == 0) return;
                        if (isBudgetExceeded(budget, bytes.length)) return;
                        String ct = metadata == null ? null : metadata.get(Metadata.CONTENT_TYPE);
                        String name = metadata == null ? null : metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                        int idx = budget == null ? (out.size() + 1) : budget.peekNextIndex();
                        String fallback = "mobi_image_" + idx + ".png";
                        Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, String.valueOf(name == null ? fallback : name), ct, fileAssetId);
                        if (saved == null) return;
                        if (budget != null) idx = budget.consume(bytes.length);
                        out.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
                    } catch (Exception ignored) {
                    }
                }
            });

            try (InputStream is = Files.newInputStream(path)) {
                parser.parse(is, new DefaultHandler(), new Metadata(), context);
            }
            if (out.isEmpty()) {
                try (InputStream is = Files.newInputStream(path)) {
                    byte[] head = is.readNBytes(4);
                    boolean isZip = head.length >= 2 && head[0] == 'P' && head[1] == 'K';
                    if (isZip) {
                        List<Map<String, Object>> zipOut = new ArrayList<>();
                        try (ZipFile zf = new ZipFile(path.toFile())) {
                            List<? extends ZipEntry> entries = zf.stream()
                                    .filter(en -> en != null && !en.isDirectory())
                                    .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                                    .toList();
                            for (ZipEntry en : entries) {
                                if (budget != null && !budget.canAdd(0)) break;
                                String name = en.getName();
                                String ext = extLowerOrNull(name);
                                if (shouldSkipMobiZipEntry(en, ext)) continue;
                                long size = en.getSize();
                                if (size > 0 && budget != null && !budget.canAdd(size)) break;
                                if (processMobiZipEntry(zf, en, ext, budget, fileAssetId, zipOut)) break;
                            }
                        }
                        if (!zipOut.isEmpty()) {
                            meta.put("imagesExtractionMode", "MOBI_ZIP");
                            return zipOut;
                        }
                    }
                } catch (Exception ignored) {
                }
                meta.put("imagesExtractionMode", "EMBEDDED_NONE");
                return List.of();
            }
            meta.put("imagesExtractionMode", "MOBI_TIKA_EMBEDDED");
            return out;
        } catch (Exception ex) {
            meta.put("imagesExtractionMode", "FAILED");
            meta.put("imagesExtractionError", safeMsg(ex));
            return List.of();
        }
    }

    private static boolean isBudgetExceeded(ImageBudget budget, long bytes) {
        return budget != null && !budget.canAdd(bytes);
    }

    private boolean shouldSkipMobiZipEntry(ZipEntry en, String ext) {
        if (!isImageExt(ext == null ? "" : ext)) return true;
        long size = en.getSize();
        long max = derivedUploadStorageService.getMaxImageBytes();
        return size > 0 && max > 0 && size > max;
    }

    private boolean processMobiZipEntry(ZipFile zf, ZipEntry en, String ext, ImageBudget budget, Long fileAssetId, List<Map<String, Object>> zipOut) throws Exception {
        String name = en.getName();
        try (InputStream zis = zf.getInputStream(en)) {
            byte[] bytes = zis.readAllBytes();
            if (bytes.length == 0) return false;
            if (isBudgetExceeded(budget, bytes.length)) return true;
            int idx = budget == null ? (zipOut.size() + 1) : budget.peekNextIndex();
            String mime = guessMimeFromExt(ext);
            Map<String, Object> saved = derivedUploadStorageService.saveDerivedImage(bytes, "mobi_" + Path.of(name).getFileName(), mime, fileAssetId);
            if (saved == null) return false;
            if (budget != null) idx = budget.consume(bytes.length);
            zipOut.add(derivedUploadStorageService.buildPlaceholder(idx, saved));
            return false;
        }
    }

    private static byte[] bufferedImageToPng(BufferedImage bi) {
        if (bi == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            return baos.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String appendImagePlaceholders(String text, List<Map<String, Object>> extractedImages) {
        String base = text == null ? "" : text;
        if (extractedImages == null || extractedImages.isEmpty()) return base;

        List<String> placeholders = new ArrayList<>();
        for (Map<String, Object> it : extractedImages) {
            if (it == null) continue;
            Object p = it.get("placeholder");
            if (p == null) continue;
            String s = String.valueOf(p).trim();
            if (s.isEmpty()) continue;
            if (!placeholders.contains(s)) placeholders.add(s);
        }
        if (placeholders.isEmpty()) return base;

        if (base.contains("[[IMAGE_")) return base;

        if (base.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder(base);
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            sb.append('\n');
            for (String p : placeholders) {
                sb.append(p).append('\n');
            }
            return sb.toString();
        }

        int len = base.length();
        int n = placeholders.size();
        int maxScan = 1200;

        StringBuilder sb = new StringBuilder(len + n * 32);
        sb.append(base);

        int insertedChars = 0;
        int minBasePos = 0;
        for (int i = 0; i < n; i++) {
            int target = (int) Math.round(((double) (i + 1) / (double) (n + 1)) * (double) len);
            if (target < minBasePos) target = minBasePos;
            if (target > len) target = len;

            int pos = -1;
            for (int d = 0; d <= maxScan; d++) {
                int left = target - d;
                if (left >= minBasePos && left >= 0 && left <= len) {
                    if (left != 0 && (left >= len || Character.isWhitespace(base.charAt(left - 1)) || Character.isWhitespace(base.charAt(left)))) {
                        pos = left;
                        break;
                    }
                }
                int right = target + d;
                if (right >= minBasePos && right >= 0 && right <= len) {
                    if (right <= 0 || right >= len || Character.isWhitespace(base.charAt(right - 1)) || Character.isWhitespace(base.charAt(right))) {
                        pos = right;
                        break;
                    }
                }
            }

            if (pos < 0) pos = Math.min(len, Math.max(minBasePos, target));

            String token = "\n\n" + placeholders.get(i) + "\n\n";
            sb.insert(pos + insertedChars, token);
            insertedChars += token.length();
            minBasePos = Math.min(len, pos + 1);
        }

        return sb.toString();
    }

    private static String guessMimeFromExt(String ext) {
        if (ext == null) return null;
        String e = ext.toLowerCase(Locale.ROOT);
        if (e.equals("png")) return "image/png";
        if (e.equals("jpg") || e.equals("jpeg")) return "image/jpeg";
        if (e.equals("gif")) return "image/gif";
        if (e.equals("bmp")) return "image/bmp";
        if (e.equals("webp")) return "image/webp";
        return null;
    }

    private static String extLowerOrNull(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        String ext = name.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
        if (ext.isBlank()) return null;
        if (!ext.matches("[a-z0-9]+")) return null;
        if (ext.length() > 16) return null;
        return ext;
    }

    private static String safeMsg(Throwable t) {
        if (t == null) return "解析失败";
        String m = t.getMessage();
        if (m == null || m.isBlank()) return t.getClass().getSimpleName();
        String s = m.trim();
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
