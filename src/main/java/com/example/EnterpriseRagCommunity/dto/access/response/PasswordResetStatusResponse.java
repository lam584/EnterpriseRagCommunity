package com.example.EnterpriseRagCommunity.dto.access.response;

import lombok.Data;

@Data
public class PasswordResetStatusResponse {
    private boolean allowed;
    private boolean totpEnabled;
    private boolean emailEnabled;
    private String message;
}
