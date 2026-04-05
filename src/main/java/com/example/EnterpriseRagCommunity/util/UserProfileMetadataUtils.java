package com.example.EnterpriseRagCommunity.util;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;

import java.util.Map;

public final class UserProfileMetadataUtils {

    private UserProfileMetadataUtils() {
    }

    public static String readProfileString(UsersEntity user, String key) {
        if (user == null) return null;
        Map<String, Object> metadata = user.getMetadata();
        if (metadata == null) return null;
        Object profileObj = metadata.get("profile");
        if (!(profileObj instanceof Map<?, ?> profile)) return null;
        Object value = profile.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
