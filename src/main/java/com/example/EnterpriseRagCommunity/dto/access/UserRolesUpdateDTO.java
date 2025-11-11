package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserRolesUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("所属租户ID")
    private Long tenantId;

    @ApiModelProperty("角色名称")
    @Size(max = 64)
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
    @Size(max = 255)
    private String notes;

    // 审计字段：显式映射，createdAt 不允许被更新，updatedAt 可由系统刷新
    @JsonIgnore
    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
