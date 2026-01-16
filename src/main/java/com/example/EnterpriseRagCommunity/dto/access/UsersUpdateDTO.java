package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UsersUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("所属租户ID(可空)")
    private Long tenantId; // 允许为空

    @ApiModelProperty("邮箱")
    @Email
    @Size(max = 191)
    private String email; // 可选更新字段，若必须保持非空由业务层校验

    @ApiModelProperty("用户名")
    @Size(max = 64)
    private String username; // 同上

    @ApiModelProperty("密码哈希（修改密码时填写）")
    @Size(max = 191)
    private String passwordHash; // 可选

    @ApiModelProperty("账户状态")
    private AccountStatus status; // 可选

    @ApiModelProperty("是否删除（软删除）")
    private Boolean isDeleted; // 可选：若需要严格非空可加@NotNull

    @ApiModelProperty("扩展元数据(JSON)")
    private Map<String, Object> metadata;

    @ApiModelProperty("上次登录时间")
    private LocalDateTime lastLoginAt;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
