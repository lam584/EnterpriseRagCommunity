package com.example.EnterpriseRagCommunity.service.init;

final class TotpBootstrapResultSupport {

    private TotpBootstrapResultSupport() {
    }

    static TotpMasterKeyBootstrapService.Result buildBaseResult(
            String keyBase64,
            String envVarName,
            String preferredCommand,
            String fallbackCommand,
            boolean succeeded,
            String scope
    ) {
        TotpMasterKeyBootstrapService.Result result = new TotpMasterKeyBootstrapService.Result();
        result.setKeyBase64(keyBase64);
        result.setEnvVarName(envVarName);
        result.setAttempted(true);
        result.setSucceeded(succeeded);
        result.setScope(scope);
        result.setCommand(preferredCommand);
        result.setFallbackCommand(fallbackCommand);
        return result;
    }
}
