package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginAttemptsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID（若可识别）")
    private Long userId;
    @JsonIgnore
    private boolean hasUserId;

    @ApiModelProperty("来源IP")
    @Size(max = 64)
    private String ip;
    @JsonIgnore
    private boolean hasIp;

    @ApiModelProperty("是否成功")
    private Boolean success;
    @JsonIgnore
    private boolean hasSuccess;

    @ApiModelProperty("失败原因/备注")
    @Size(max = 64)
    private String reason;
    @JsonIgnore
    private boolean hasReason;

    @ApiModelProperty("发生时间（不可修改，数据库维护）")
    @JsonIgnore
    private LocalDateTime occurredAt;

    public void setUserId(Long userId) {
        this.userId = userId;
        this.hasUserId = true;
    }

    public void setIp(String ip) {
        this.ip = ip;
        this.hasIp = true;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
        this.hasSuccess = true;
    }

    public void setReason(String reason) {
        this.reason = reason;
        this.hasReason = true;
    }
}

