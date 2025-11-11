package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class UserRolesQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("所属租户ID")
    private Long tenantId;

    @ApiModelProperty("角色名称（模糊匹配）")
    private String roles;

    @ApiModelProperty("可登录")
    private Boolean canLogin;

    @ApiModelProperty("可查看公告")
    private Boolean canViewAnnouncement;

    @ApiModelProperty("可查看帮助文章")
    private Boolean canViewHelpArticles;

    @ApiModelProperty("可重置自己的密码")
    private Boolean canResetOwnPassword;

    @ApiModelProperty("可评论")
    private Boolean canComment;

    @ApiModelProperty("备注")
    private String notes;

    // 审计字段等值查询
    @ApiModelProperty("创建时间（等值查询）")
    private LocalDateTime createdAt;

    @ApiModelProperty("更新时间（等值查询）")
    private LocalDateTime updatedAt;

    // 范围查询
    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;

    @ApiModelProperty("更新时间-起")
    private LocalDateTime updatedFrom;

    @ApiModelProperty("更新时间-止")
    private LocalDateTime updatedTo;
}
