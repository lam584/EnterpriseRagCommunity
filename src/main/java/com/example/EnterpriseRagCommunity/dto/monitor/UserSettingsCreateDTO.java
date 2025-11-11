package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UserSettingsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "用户ID", required = true, example = "100")
    private Long userId;

    @NotNull
    @Size(max = 64)
    @ApiModelProperty(value = "设置键", required = true, example = "ui.theme")
    private String k;

    @ApiModelProperty(value = "设置值(JSON)")
    private Map<String, Object> v;
}

