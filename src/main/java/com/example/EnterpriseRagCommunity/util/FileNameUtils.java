package com.example.EnterpriseRagCommunity.util;

import java.util.Locale;

public final class FileNameUtils {

    private FileNameUtils() {
    }

    public static String extLowerOrNull(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) name = name.substring(slash + 1);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        String ext = name.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
        if (ext.isBlank()) return null;
        if (!ext.matches("[a-z0-9]+")) return null;
        if (ext.length() > 16) return null;
        return ext;
    }
}
