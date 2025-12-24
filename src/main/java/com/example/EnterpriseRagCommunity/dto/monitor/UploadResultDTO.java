package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class UploadResultDTO {
    @ApiModelProperty(value = "file_assets.id")
    private Long id;

    @ApiModelProperty(value = "原始文件名")
    private String fileName;

    @ApiModelProperty(value = "可访问 URL")
    private String fileUrl;

    @ApiModelProperty(value = "文件大小(字节)")
    private Long fileSize;

    @ApiModelProperty(value = "MIME 类型")
    private String mimeType;
}

