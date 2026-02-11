package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class EmailAdminSettingsDTO {
    @ApiModelProperty("是否启用邮箱验证码发送")
    private Boolean enabled;

    @ApiModelProperty("邮箱验证码有效期（秒）")
    private Integer otpTtlSeconds;

    @ApiModelProperty("邮箱验证码重发等待时间（秒）")
    private Integer otpResendWaitSeconds;

    @ApiModelProperty("邮箱验证码验证成功后减少的重发等待时间（秒）")
    private Integer otpResendWaitReductionSecondsAfterVerified;

    @ApiModelProperty("协议（用于展示：SMTP/IMAP/POP3）")
    private String protocol;

    @ApiModelProperty("服务器地址")
    private String host;

    @ApiModelProperty("服务器端口号（常规）")
    private Integer portPlain;

    @ApiModelProperty("服务器端口号（加密）")
    private Integer portEncrypted;

    @ApiModelProperty("加密方式：NONE/SSL/STARTTLS")
    private String encryption;

    @ApiModelProperty("连接超时(ms)")
    private Integer connectTimeoutMs;

    @ApiModelProperty("读取超时(ms)")
    private Integer timeoutMs;

    @ApiModelProperty("写入超时(ms)")
    private Integer writeTimeoutMs;

    @ApiModelProperty("JavaMail debug")
    private Boolean debug;

    @ApiModelProperty("SSL trust 配置（例如 smtp.qiye.aliyun.com 或 *）")
    private String sslTrust;

    @ApiModelProperty("邮件主题前缀（可选）")
    private String subjectPrefix;

    @ApiModelProperty("SMTP 用户名")
    private String username;

    @ApiModelProperty("SMTP 密码")
    private String password;

    @ApiModelProperty("发件人邮箱")
    private String from;

    @ApiModelProperty("发件人名称")
    private String fromName;
}
