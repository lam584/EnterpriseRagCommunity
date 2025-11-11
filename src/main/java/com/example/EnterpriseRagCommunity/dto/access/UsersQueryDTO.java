package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
@EqualsAndHashCode(callSuper = true)
@Data
public class UsersQueryDTO extends PageRequestDTO {
    @ApiModelProperty("所属租户ID")
    private Long tenantId;

    @ApiModelProperty("邮箱（模糊匹配）")
    private String email;

    @ApiModelProperty("用户名（模糊匹配）")
    private String username;

    @ApiModelProperty("账户状态（可多选）")
    private List<AccountStatus> status;

    @ApiModelProperty("上次登录-起")
    private LocalDateTime lastLoginFrom;

    @ApiModelProperty("上次登录-止")
    private LocalDateTime lastLoginTo;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;

    @ApiModelProperty("更新时间-起")
    private LocalDateTime updatedFrom;

    @ApiModelProperty("更新时间-止")
    private LocalDateTime updatedTo;

    @ApiModelProperty("是否包含已软删除数据")
    private Boolean includeDeleted;
}
