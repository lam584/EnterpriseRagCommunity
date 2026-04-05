package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.retrieval.RagSearchSupport;

final class LikelyImageUrlSupport {

    private LikelyImageUrlSupport() {
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

    private static String toNonBlank(Object value) {
        return RagSearchSupport.toNonBlank(value);
    }
}
