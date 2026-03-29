package com.example.EnterpriseRagCommunity.service.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;

final class AiChatInputSupport {

    private static final Pattern FILE_ASSET_ID_PATTERN = Pattern.compile("file_asset_id\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private AiChatInputSupport() {
    }

    static List<AiChatStreamRequest.ImageInput> resolveImages(AiChatStreamRequest req) {
        if (req == null || req.getImages() == null || req.getImages().isEmpty()) return List.of();
        List<AiChatStreamRequest.ImageInput> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiChatStreamRequest.ImageInput img : req.getImages()) {
            if (img == null) continue;
            if (out.size() >= 5) break;
            String url = toNonBlank(img.getUrl());
            if (url == null) continue;
            if (seen.contains(url)) continue;
            String mt = toNonBlank(img.getMimeType());
            boolean isImg = mt != null && mt.toLowerCase().startsWith("image/");
            if (!isImg && !isLikelyImageUrl(url)) continue;
            out.add(img);
            seen.add(url);
        }
        return out;
    }

    static List<AiChatStreamRequest.FileInput> resolveFiles(AiChatStreamRequest req) {
        if (req == null || req.getFiles() == null || req.getFiles().isEmpty()) return List.of();
        List<AiChatStreamRequest.FileInput> out = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        Set<String> seenUrls = new HashSet<>();
        for (AiChatStreamRequest.FileInput f : req.getFiles()) {
            if (f == null) continue;
            if (out.size() >= 20) break;
            Long id = f.getFileAssetId();
            String url = toNonBlank(f.getUrl());
            if (id == null && url == null) continue;
            if (id != null) {
                if (seenIds.contains(id)) continue;
                seenIds.add(id);
            } else {
                if (seenUrls.contains(url)) continue;
                seenUrls.add(url);
            }
            out.add(f);
        }
        return out;
    }

    static List<AiChatStreamRequest.FileInput> extractFilesFromHistoryText(String text) {
        String t = text == null ? "" : text;
        Matcher m = FILE_ASSET_ID_PATTERN.matcher(t);
        List<AiChatStreamRequest.FileInput> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        while (m.find()) {
            if (out.size() >= 20) break;
            String raw = m.group(1);
            if (raw == null || raw.isBlank()) continue;
            try {
                long id = Long.parseLong(raw.trim());
                if (id <= 0) continue;
                if (seen.contains(id)) continue;
                seen.add(id);
                AiChatStreamRequest.FileInput fi = new AiChatStreamRequest.FileInput();
                fi.setFileAssetId(id);
                out.add(fi);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static boolean isLikelyImageUrl(String url) {
        String u = toNonBlank(url);
        if (u == null) return false;
        String lower = u.toLowerCase();
        if (lower.startsWith("/uploads/")) return true;
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".svg");
    }

    static String appendImagesAsText(String userMsg, List<AiChatStreamRequest.ImageInput> images) {
        String base = userMsg == null ? "" : userMsg;
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n[IMAGES]\n");
        int take = 0;
        for (AiChatStreamRequest.ImageInput img : images) {
            if (img == null) continue;
            String url = toNonBlank(img.getUrl());
            if (url == null) continue;
            sb.append("- ").append(url).append("\n");
            take += 1;
            if (take >= 5) break;
        }
        return sb.toString();
    }

    static String appendFilesAsText(String userMsg, List<AiChatStreamRequest.FileInput> files) {
        String base = userMsg == null ? "" : userMsg;
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n[FILES]\n");
        int take = 0;
        for (AiChatStreamRequest.FileInput f : files) {
            if (f == null) continue;
            if (take >= 20) break;
            String url = toNonBlank(f.getUrl());
            Long id = f.getFileAssetId();
            String name = toNonBlank(f.getFileName());
            String mt = toNonBlank(f.getMimeType());
            sb.append("- file_asset_id=").append(id == null ? "null" : id);
            if (name != null) sb.append(" name=").append(name);
            if (mt != null) sb.append(" mime=").append(mt);
            if (url != null) sb.append(" url=").append(url);
            sb.append("\n");
            take += 1;
        }
        return sb.toString();
    }

    private static String toNonBlank(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }
}
