package com.example.EnterpriseRagCommunity.dto.access;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateLogin2faPreferenceRequest {
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}

