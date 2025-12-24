package com.example.EnterpriseRagCommunity.dto.content.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentSetDeletedRequest {
    @NotNull(message = "isDeleted 不能为空")
    private Boolean isDeleted;
}

