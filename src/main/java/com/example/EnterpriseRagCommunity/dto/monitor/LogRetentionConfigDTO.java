package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.service.monitor.LogRetentionMode;

public record LogRetentionConfigDTO(
        boolean enabled,
        long keepDays,
        LogRetentionMode mode
) {
}

