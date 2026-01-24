package com.example.EnterpriseRagCommunity.dto.access.response;

import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import lombok.Data;

@Data
public class InitialAdminRegisterResponse {
    private UsersDTO user;
    private TotpMasterKeySetup totpMasterKeySetup;

    @Data
    public static class TotpMasterKeySetup {
        private String envVarName;
        private String keyBase64;
        private Boolean attempted;
        private Boolean succeeded;
        private String scope;
        private String command;
        private String fallbackCommand;
        private String message;
        private String error;
        private Boolean restartRequired;
    }
}

