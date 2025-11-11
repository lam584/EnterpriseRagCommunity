package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class PostVersionsCreateDTO {
    @ApiModelProperty(value = "帖子ID", required = true, example = "1000")
    @NotNull
    private Long postId;

    @ApiModelProperty(value = "版本号（从1递增）", required = true, example = "1")
    @NotNull
    private Integer version;

    @ApiModelProperty(value = "编辑人ID", example = "2")
    private Long editorId; // 可为空

    @ApiModelProperty(value = "标题", required = true, example = "初始版本标题")
    @NotBlank
    private String title;

    @ApiModelProperty(value = "内容", required = true, example = "正文内容")
    @NotBlank
    private String content;

    @ApiModelProperty(value = "编辑原因", example = "修复错误")
    private String reason;

    @ApiModelProperty(value = "创建时间（数据库默认）", example = "2025-01-01T12:00:00")
    @JsonIgnore // 由数据库生成，仍需映射字段
    private LocalDateTime createdAt;
}

