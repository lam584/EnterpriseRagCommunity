package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class ResumableUploadStatusDTO {
    @ApiModelProperty(value = "上传任务 ID")
    private String uploadId;

    @ApiModelProperty(value = "原始文件名")
    private String fileName;

    @ApiModelProperty(value = "文件大小(字节)")
    private Long fileSize;

    @ApiModelProperty(value = "已上传字节数")
    private Long uploadedBytes;

    @ApiModelProperty(value = "分片大小(字节)")
    private Integer chunkSizeBytes;

    @ApiModelProperty(value = "状态: UPLOADING|COMPLETED|VERIFYING|FINALIZING|DONE|ERROR")
    private String status;

    @ApiModelProperty(value = "校验阶段已处理字节数")
    private Long verifyBytes;

    @ApiModelProperty(value = "校验阶段总字节数")
    private Long verifyTotalBytes;

    @ApiModelProperty(value = "状态更新时间(毫秒)")
    private Long updatedAtEpochMs;

    @ApiModelProperty(value = "错误信息(如有)")
    private String errorMessage;
}
