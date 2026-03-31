package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class PostAttachmentsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty("帖子ID")
    private Long postId;

    @ApiModelProperty("附件URL")
    private @Size(max = 512) String url;

    @ApiModelProperty("文件名")
    private @Size(max = 512) String fileName;

    @ApiModelProperty("MIME类型")
    private @Size(max = 64) String mimeType;

    @ApiModelProperty("文件大小字节")
    private Long sizeBytes;

    @ApiModelProperty("宽度")
    private Integer width;

    @ApiModelProperty("高度")
    private Integer height;

    // 审计字段定义但不可修改
    @ApiModelProperty("创建时间（不可修改）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

