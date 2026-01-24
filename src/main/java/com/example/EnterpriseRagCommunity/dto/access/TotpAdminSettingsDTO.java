package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class TotpAdminSettingsDTO {
    @ApiModelProperty("issuer（显示在认证器应用中）")
    private String issuer;

    @ApiModelProperty("允许算法列表（SHA1/SHA256/SHA512）")
    private List<String> allowedAlgorithms;

    @ApiModelProperty("允许验证码位数列表（6/8）")
    private List<Integer> allowedDigits;

    @ApiModelProperty("允许时间步长列表（秒）")
    private List<Integer> allowedPeriodSeconds;

    @ApiModelProperty("允许最大时间偏移窗口（步数）")
    private Integer maxSkew;

    @ApiModelProperty("默认算法")
    private String defaultAlgorithm;

    @ApiModelProperty("默认验证码位数")
    private Integer defaultDigits;

    @ApiModelProperty("默认时间步长（秒）")
    private Integer defaultPeriodSeconds;

    @ApiModelProperty("默认时间偏移窗口（步数）")
    private Integer defaultSkew;
}

