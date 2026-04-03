package com.example.EnterpriseRagCommunity.controller;

//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.GetMapping;
//
//@Controller
//public class SpaController {
//
//    // 访问站点根路径时，转发到打包后的 index.html
//    @GetMapping({"/", "/index", "/index.html"})
//    public String index() {
//        return "forward:/index.html";
//    }
//
//    // 兼容 Spring 6 PathPatternParser：使用首段变量 + 结尾 **，且排除后端与静态资源前缀
//    // 说明：
//    // - {path:...} 仅匹配首个路径段，并使用负向前瞻排除 api、assets、static、public、resources、webjars 等常见静态与后端前缀
//    // - 结尾使用 /** 捕获其余多级段（PathPattern 要求 ** 必须位于末尾）
//    // - 避免包含点号的静态资源（如 favicon.ico），通过排除列表规避
//    @GetMapping("/{path:^(?!api|assets|static|public|resources|webjars|db|css|js|img|images|fonts|favicon\\.ico$).*$}/**")
//    public String any() {
//        return "forward:/index.html";
//    }
//}


import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            if (resourcePath.isBlank()) {
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
