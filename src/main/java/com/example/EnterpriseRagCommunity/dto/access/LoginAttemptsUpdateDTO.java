package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class LoginAttemptsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID（若可识别）")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty("来源IP")
    @Size(max = 64)
    private Optional<String> ip = Optional.empty();

    @ApiModelProperty("是否成功")
    private Optional<Boolean> success = Optional.empty();

    @ApiModelProperty("失败原因/备注")
    @Size(max = 64)
    private Optional<String> reason = Optional.empty();

    @ApiModelProperty("发生时间（不可修改，数据库维护）")
    @JsonIgnore
    private LocalDateTime occurredAt;
}

