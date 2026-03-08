package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String root = "uploads";
    private String urlPrefix = "/uploads";

    public Path rootPath() {
        return Paths.get(root).toAbsolutePath().normalize();
    }

    public String normalizedUrlPrefix() {
        String p = (urlPrefix == null || urlPrefix.isBlank()) ? "/uploads" : urlPrefix.trim();
        if (p.endsWith("/")) return p.substring(0, p.length() - 1);
        return p;
    }
}

