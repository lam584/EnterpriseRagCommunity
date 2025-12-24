package com.example.EnterpriseRagCommunity.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateRequest {
    @NotBlank(message = "content 不能为空")
    @Size(max = 5000, message = "content 过长")
    private String content;

    private Long parentId;
}

