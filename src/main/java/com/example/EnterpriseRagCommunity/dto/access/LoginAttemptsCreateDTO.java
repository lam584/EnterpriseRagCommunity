package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginAttemptsCreateDTO {
    @ApiModelProperty("用户ID（若可识别）")
    private Long userId; // nullable

    @ApiModelProperty("来源IP")
    @Size(max = 64)
    private String ip; // nullable

    @ApiModelProperty("是否成功")
    @NotNull
    private Boolean success; // NOT NULL

    @ApiModelProperty("失败原因/备注")
    @Size(max = 64)
    private String reason; // nullable

    @ApiModelProperty("发生时间（数据库默认生成）")
    @JsonIgnore // 不由客户端提供
    private LocalDateTime occurredAt;
}

