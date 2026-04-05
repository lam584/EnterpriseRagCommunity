package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LocalUploadFileSupport {

    private LocalUploadFileSupport() {
    }

    public static byte[] readLocalUploadBytes(FileAssetsRepository fileAssetsRepository,
                                              String uploadRoot,
                                              String urlPrefix,
                                              Long fileAssetId,
                                              String url) {
        try {
            String prefix = urlPrefix == null ? "/uploads" : urlPrefix.trim();
            String u = blankToNull(url);
            if (u != null && !prefix.isEmpty() && u.startsWith(prefix + "/")) {
                int q = u.indexOf('?');
                if (q >= 0) u = u.substring(0, q);
                String rel = u.substring(prefix.length());
                while (rel.startsWith("/")) rel = rel.substring(1);

                Path root = Paths.get(uploadRoot == null ? "uploads" : uploadRoot).toAbsolutePath().normalize();
                Path p = root.resolve(rel).normalize();
                if (p.startsWith(root) && Files.exists(p) && Files.isRegularFile(p)) {
                    return Files.readAllBytes(p);
                }
            }

            if (fileAssetId != null) {
                var fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
                if (fa != null && fa.getPath() != null && !fa.getPath().isBlank()) {
                    Path p = Paths.get(fa.getPath()).toAbsolutePath().normalize();
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return Files.readAllBytes(p);
                    }
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
