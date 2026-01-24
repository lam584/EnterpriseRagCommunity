package com.example.EnterpriseRagCommunity.dto.access;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationSettingsDTO {
    @NotNull
    private Long defaultRegisterRoleId;
}

