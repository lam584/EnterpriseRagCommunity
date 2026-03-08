package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.*;

@Data
public class PostAttachmentsCreateDTO {
    @ApiModelProperty(value = "帖子ID", required = true)
    @NotNull
    private Long postId;

    @ApiModelProperty(value = "文件资产ID", required = true)
    @NotNull
    private Long fileAssetId;

    @ApiModelProperty(value = "宽度")
    private Integer width;

    @ApiModelProperty(value = "高度")
    private Integer height;
}

