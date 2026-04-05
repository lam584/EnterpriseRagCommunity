package com.example.EnterpriseRagCommunity.controller;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class SpaController implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path viteDist = Paths.get("my-vite-app", "dist").toAbsolutePath().normalize();
        String viteDistLocation = viteDist.toUri().toString();
        if (!viteDistLocation.endsWith("/")) {
            viteDistLocation = viteDistLocation + "/";
        }

        // 只用一个 /**，既能匹配所有静态文件，也能做 SPA 路由回退
        registry
                .addResourceHandler("/**")
                .addResourceLocations(
                        viteDistLocation,
                        "classpath:/static/"
                )
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver());
    }

    static class SpaFallbackResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, @NonNull Resource location) throws IOException {
            if (resourcePath == null || resourcePath.isBlank()) {
                return resolveReadableResource(location, "index.html");
            }
            String normalized = resourcePath;
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (normalized.isBlank()) {
                return resolveReadableResource(location, "index.html");
            }

            if (normalized.startsWith("api/")) {
                return null;
            }

            Resource requested = resolveReadableResource(location, normalized);
            if (requested != null) {
                return requested;
            }
            if (!normalized.contains(".")) {
                return resolveReadableResource(location, "index.html");
            }
            return null;
        }

        private Resource resolveReadableResource(Resource location, String relativePath) throws IOException {
            Resource resource = location.createRelative(relativePath);
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }

            try {
                Path basePath = location.getFile().toPath();
                if (Files.isDirectory(basePath)) {
                    Resource fallback = new org.springframework.core.io.FileSystemResource(
                            basePath.resolve(relativePath).normalize().toFile());
                    if (fallback.exists() && fallback.isReadable()) {
                        return fallback;
                    }
                }
            } catch (IOException ignored) {
                // ignore and return null when location cannot be represented as a local file
            }

            return null;
        }
    }
}
