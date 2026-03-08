package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class ResumableUploadInitResponseDTO {
    @ApiModelProperty(value = "上传任务 ID")
    private String uploadId;

    @ApiModelProperty(value = "分片大小(字节)")
    private Integer chunkSizeBytes;

    @ApiModelProperty(value = "已上传字节数")
    private Long uploadedBytes;
}

