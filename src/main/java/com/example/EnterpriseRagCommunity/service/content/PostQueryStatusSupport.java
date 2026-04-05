package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;

public final class PostQueryStatusSupport {

    private PostQueryStatusSupport() {
    }

    public static PostStatus parseEffectiveStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return PostStatus.valueOf(status);
    }
}
