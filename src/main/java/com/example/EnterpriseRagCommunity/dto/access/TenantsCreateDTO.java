package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class TenantsCreateDTO {
    @ApiModelProperty("租户编码，唯一标识")
    @NotBlank
    @Size(max = 64)
    private String code;

    @ApiModelProperty("租户名称")
    @NotBlank
    @Size(max = 128)
    private String name;

    @ApiModelProperty("扩展元数据(JSON)")
    private Map<String, Object> metadata;
}
