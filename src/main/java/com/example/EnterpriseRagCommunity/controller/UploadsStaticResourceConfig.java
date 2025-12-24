package com.example.EnterpriseRagCommunity.controller;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

@Configuration
public class UploadsStaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();

        // Ensure pattern ends with "/**" so Spring can match all nested paths.
        String prefix = (urlPrefix == null || urlPrefix.isBlank()) ? "/uploads" : urlPrefix;
        if (!prefix.endsWith("/**")) {
            if (prefix.endsWith("/")) {
                prefix = prefix + "**";
            } else {
                prefix = prefix + "/**";
            }
        }

        // Important: use trailing slash so relative paths resolve correctly.
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler(prefix)
                .addResourceLocations(location)
                .setCachePeriod(0);
    }
}
