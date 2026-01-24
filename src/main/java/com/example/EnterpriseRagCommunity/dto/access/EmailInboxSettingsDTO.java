package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class EmailInboxSettingsDTO {
    @ApiModelProperty("协议（固定 IMAP，用于展示）")
    private String protocol;

    @ApiModelProperty("IMAP 服务器地址")
    private String host;

    @ApiModelProperty("IMAP 端口号（常规）")
    private Integer portPlain;

    @ApiModelProperty("IMAP 端口号（加密）")
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

    @ApiModelProperty("SSL trust 配置（例如 imap.qiye.aliyun.com 或 *）")
    private String sslTrust;

    @ApiModelProperty("收件箱文件夹（默认 INBOX）")
    private String folder;

    @ApiModelProperty("发件箱文件夹（默认 Sent）")
    private String sentFolder;
}
