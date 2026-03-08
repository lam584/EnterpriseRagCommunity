package com.example.EnterpriseRagCommunity.dto.access;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserBanActionRequest {
    @NotBlank
    private String reason;
}

