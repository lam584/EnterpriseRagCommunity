package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileAssetsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "文件ID", required = true, example = "1")
    private Long id;

    @Size(max = 512)
    @ApiModelProperty(value = "访问URL")
    private String url;

    @Size(max = 16)
    @ApiModelProperty(value = "状态", example = "READY")
    private String status;
}

