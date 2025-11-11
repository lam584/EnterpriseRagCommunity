package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionsCreateDTO {
    @ApiModelProperty("资源标识")
    @NotNull
    @NotBlank
    @Size(max = 64)
    private String resource;

    @ApiModelProperty("操作标识")
    @NotNull
    @NotBlank
    @Size(max = 32)
    private String action;

    @ApiModelProperty("描述")
    @Size(max = 255)
    private String description;
}
