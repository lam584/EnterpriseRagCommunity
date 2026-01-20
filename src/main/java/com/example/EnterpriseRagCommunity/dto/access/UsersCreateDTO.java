package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
public class UsersCreateDTO {
    @ApiModelProperty("所属租户ID(可空，系统可默认租户)")
    private Long tenantId; // 移除 @NotNull 以与 SQL NULL 一致

    @ApiModelProperty("邮箱")
    @NotBlank
    @Email
    @Size(max = 191)
    private String email;

    @ApiModelProperty("用户名")
    @NotBlank
    @Size(max = 64)
    private String username;

    @ApiModelProperty("密码哈希")
    @NotBlank
    @Size(max = 191)
    private String passwordHash;

    @ApiModelProperty("账户状态")
    @NotNull
    private AccountStatus status;

    @ApiModelProperty("是否删除（软删除）默认false")
    @NotNull // 与 NOT NULL 列保持一致
    private Boolean isDeleted;

    @ApiModelProperty("扩展元数据(JSON)")
    private Map<String, Object> metadata;

    @ApiModelProperty("创建用户时分配的角色ID列表(可选)")
    private List<Long> roleIds;
}
