package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
@EqualsAndHashCode(callSuper = true)
@Data
public class EmailVerificationsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("验证码（精确匹配或模糊）")
    private String code;

    // 单值枚举查询
    @ApiModelProperty("用途（单值）")
    private EmailVerificationPurpose purpose;

    // 多选枚举查询（可选增强）
    @ApiModelProperty("用途（可多选）")
    private List<EmailVerificationPurpose> purposes;

    // 精确时间字段（等值匹配）
    @ApiModelProperty("过期时间（精确匹配）")
    private LocalDateTime expiresAt;

    @ApiModelProperty("使用时间（精确匹配）")
    private LocalDateTime consumedAt;

    @ApiModelProperty("创建时间（精确匹配）")
    private LocalDateTime createdAt;

    // 范围查询统一使用 After/Before 命名
    @ApiModelProperty("过期时间-起")
    private LocalDateTime expiresAfter;

    @ApiModelProperty("过期时间-止")
    private LocalDateTime expiresBefore;

    @ApiModelProperty("使用时间-起")
    private LocalDateTime consumedAfter;

    @ApiModelProperty("使用时间-止")
    private LocalDateTime consumedBefore;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;
}
