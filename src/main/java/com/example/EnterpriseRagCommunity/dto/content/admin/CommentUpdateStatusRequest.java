package com.example.EnterpriseRagCommunity.dto.content.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentUpdateStatusRequest {
    @NotBlank(message = "status 不能为空")
    private String status;
}
