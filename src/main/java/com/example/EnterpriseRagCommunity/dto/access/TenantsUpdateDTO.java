package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class TenantsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("租户编码，唯一标识")
    @Size(max = 64)
    private String code;

    @ApiModelProperty("租户名称")
    @Size(max = 128)
    private String name;

    @ApiModelProperty("扩展元数据(JSON)")
    private Map<String, Object> metadata;
}
