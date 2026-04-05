package com.example.EnterpriseRagCommunity.service;

import com.example.EnterpriseRagCommunity.dto.access.response.TotpStatusResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.AdminUserTotpStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;

public final class AccountTotpSupport {

    private AccountTotpSupport() {
    }

    public static void applySecretFields(AdminUserTotpStatusDTO dto, TotpSecretsEntity row) {
        dto.setVerifiedAt(row.getVerifiedAt());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setAlgorithm(row.getAlgorithm());
        dto.setDigits(row.getDigits());
        dto.setPeriodSeconds(row.getPeriodSeconds());
        dto.setSkew(row.getSkew());
    }

    public static void applySecretFields(TotpStatusResponse resp, TotpSecretsEntity row) {
        resp.setVerifiedAt(row.getVerifiedAt());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setAlgorithm(row.getAlgorithm());
        resp.setDigits(row.getDigits());
        resp.setPeriodSeconds(row.getPeriodSeconds());
        resp.setSkew(row.getSkew());
    }
}
