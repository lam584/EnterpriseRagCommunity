package com.example.EnterpriseRagCommunity.service.monitor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileAssetExtractionSupportTest {

    @Test
    void probeArchiveStream_detectsCompressionAndArchiveType() throws Exception {
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(zipBytes, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("a.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        FileAssetExtractionSupport.ArchiveProbeResult zipProbe =
                FileAssetExtractionSupport.probeArchiveStream(new ByteArrayInputStream(zipBytes.toByteArray()));
        assertEquals("zip", zipProbe.archiveType());
        assertNull(zipProbe.compression());
        assertNotNull(zipProbe.stream());

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(gz)) {
            gos.write("plain text".getBytes(StandardCharsets.UTF_8));
        }
        FileAssetExtractionSupport.ArchiveProbeResult gzProbe =
                FileAssetExtractionSupport.probeArchiveStream(new ByteArrayInputStream(gz.toByteArray()));
        assertEquals("gz", gzProbe.compression());
        assertNull(gzProbe.archiveType());
        assertNotNull(gzProbe.stream());
    }
}
