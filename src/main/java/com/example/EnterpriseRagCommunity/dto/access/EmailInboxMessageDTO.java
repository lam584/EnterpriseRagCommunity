package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class EmailInboxMessageDTO {
    @ApiModelProperty("消息唯一标识（优先使用 IMAP UID）")
    private String id;

    @ApiModelProperty("主题")
    private String subject;

    @ApiModelProperty("发件人（展示文本）")
    private String from;

    @ApiModelProperty("收件人（展示文本）")
    private String to;

    @ApiModelProperty("发送时间（epoch ms）")
    private Long sentAt;

    @ApiModelProperty("正文内容（纯文本/或降级字符串）")
    private String content;
}
