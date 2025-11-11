package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class NotificationsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    // 对应 SQL: user_id BIGINT NOT NULL
    @ApiModelProperty(value = "接收用户ID", example = "100")
    private Long userId;

    // 对应 SQL: type VARCHAR(64) NOT NULL
    @ApiModelProperty(value = "通知类型")
    private String type;

    // 对应 SQL: title VARCHAR(191) NOT NULL
    @ApiModelProperty(value = "标题")
    private String title;

    // 对应 SQL: content TEXT NULL
    @ApiModelProperty(value = "内容")
    private String content;

    // 对应 SQL: read_at DATETIME(3) NULL
    @ApiModelProperty(value = "阅读时间")
    private LocalDateTime readAt;

    // 对应 SQL: created_at DATETIME(3) NOT NULL
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    // 范围查询字段（规范：支持范围查询）
    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdAtFrom;
    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdAtTo;

    @ApiModelProperty(value = "阅读时间-起")
    private LocalDateTime readAtFrom;
    @ApiModelProperty(value = "阅读时间-止")
    private LocalDateTime readAtTo;

    public NotificationsQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
