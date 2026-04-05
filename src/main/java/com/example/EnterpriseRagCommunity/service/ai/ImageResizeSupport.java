package com.example.EnterpriseRagCommunity.service.ai;

import java.awt.image.BufferedImage;

public final class ImageResizeSupport {

    private ImageResizeSupport() {
    }

    public static BufferedImage drawResized(BufferedImage image, int width, int height) {
        if (image == null) return null;
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        BufferedImage resized = new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(image, 0, 0, safeWidth, safeHeight, null);
        return resized;
    }
}
