package com.example.EnterpriseRagCommunity.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpaFallbackResourceResolverTest {

    @Test
    void getResource_shouldReturnIndex_whenPathBlank_andIndexExists(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("index.html"), "ok", StandardCharsets.UTF_8);
        Resource location = new FileSystemResource(tmp.toFile());
        SpaController.SpaFallbackResourceResolver r = new SpaController.SpaFallbackResourceResolver();

        Resource out = r.getResource("", location);

        assertThat(out).isNotNull();
        assertThat(out.exists()).isTrue();
        assertThat(out.getFilename()).isEqualTo("index.html");
    }

    @Test
    void getResource_shouldReturnNull_whenBlankPath_andIndexMissing(@TempDir Path tmp) throws Exception {
        Resource location = new FileSystemResource(tmp.toFile());
        SpaController.SpaFallbackResourceResolver r = new SpaController.SpaFallbackResourceResolver();

        Resource out = r.getResource("", location);

        assertThat(out).isNull();
    }

    @Test
    void getResource_shouldReturnNull_whenApiPath(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("index.html"), "ok", StandardCharsets.UTF_8);
        Resource location = new FileSystemResource(tmp.toFile());
        SpaController.SpaFallbackResourceResolver r = new SpaController.SpaFallbackResourceResolver();

        Resource out = r.getResource("api/test", location);

        assertThat(out).isNull();
    }

    @Test
    void getResource_shouldReturnRequestedFile_whenExists(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("assets.js"), "x", StandardCharsets.UTF_8);
        Resource location = new FileSystemResource(tmp.toFile());
        SpaController.SpaFallbackResourceResolver r = new SpaController.SpaFallbackResourceResolver();

        Resource out = r.getResource("assets.js", location);

        assertThat(out).isNotNull();
        assertThat(out.exists()).isTrue();
        assertThat(out.getFilename()).isEqualTo("assets.js");
    }

    @Test
    void getResource_shouldFallbackToIndex_whenRouteLikePath(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("index.html"), "ok", StandardCharsets.UTF_8);
        Resource location = new FileSystemResource(tmp.toFile());
        SpaController.SpaFallbackResourceResolver r = new SpaController.SpaFallbackResourceResolver();

        Resource out = r.getResource("portal/discover/home", location);

        assertThat(out).isNotNull();
        assertThat(out.exists()).isTrue();
        assertThat(out.getFilename()).isEqualTo("index.html");
    }

    @Test
    void getResource_shouldReturnNull_whenMissingResourceWithDot(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("index.html"), "ok", StandardCharsets.UTF_8);
        Resource location = new FileSystemResource(tmp.toFile());
        SpaController.SpaFallbackResourceResolver r = new SpaController.SpaFallbackResourceResolver();

        Resource out = r.getResource("missing.png", location);

        assertThat(out).isNull();
    }
}
