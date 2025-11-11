package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class LoginAttemptsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("IP 地址（模糊匹配）")
    private String ip;

    @ApiModelProperty("是否成功")
    private Boolean success;

    @ApiModelProperty("原因（模糊匹配）")
    private String reason;

    @ApiModelProperty("发生时间")
    private LocalDateTime occurredAt;

    @ApiModelProperty("发生时间-起")
    private LocalDateTime occurredAtFrom;

    @ApiModelProperty("发生时间-止")
    private LocalDateTime occurredAtTo;
}
