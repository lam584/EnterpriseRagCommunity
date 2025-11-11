package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("资源标识")
    @Size(max = 64)
    private String resource;

    @ApiModelProperty("操作标识")
    @Size(max = 32)
    private String action;

    @ApiModelProperty("描述")
    @Size(max = 255)
    private String description;
}
