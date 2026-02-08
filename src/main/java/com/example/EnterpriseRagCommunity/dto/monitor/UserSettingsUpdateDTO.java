package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public class UserSettingsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "用户ID")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty(value = "设置键")
    private Optional<@Size(max = 64) String> k = Optional.empty();

    @ApiModelProperty(value = "设置值(JSON)")
    private Optional<Map<String, Object>> v = Optional.empty();
}
