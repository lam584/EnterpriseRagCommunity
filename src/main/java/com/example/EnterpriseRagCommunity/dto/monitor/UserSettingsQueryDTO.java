package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class UserSettingsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "100")
    private Long userId;

    @ApiModelProperty(value = "设置键")
    private String k;

    @ApiModelProperty(value = "设置键(模糊)")
    private String kLike;

    @ApiModelProperty(value = "设置值(JSON)")
    private Map<String, Object> v;

    public UserSettingsQueryDTO() {
        this.setOrderBy("id");
        this.setSort("desc");
    }
}
