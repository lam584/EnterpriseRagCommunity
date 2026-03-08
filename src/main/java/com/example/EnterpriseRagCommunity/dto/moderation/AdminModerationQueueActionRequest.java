package com.example.EnterpriseRagCommunity.dto.moderation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminModerationQueueActionRequest {
    /** 必填：操作理由/备注（用于审计追溯） */
    @NotBlank(message = "reason 不能为空")
    private String reason;

    /** 可选：重入自动审核时指定复审场景（default|reported|appeal） */
    private String reviewStage;
}

