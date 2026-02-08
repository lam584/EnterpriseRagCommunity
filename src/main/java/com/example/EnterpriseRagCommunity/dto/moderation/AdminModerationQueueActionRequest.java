package com.example.EnterpriseRagCommunity.dto.moderation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminModerationQueueActionRequest {
    /** 必填：操作理由/备注（用于审计追溯） */
    @NotBlank(message = "reason 不能为空")
    private String reason;
}

