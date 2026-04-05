package com.example.EnterpriseRagCommunity.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.access.request.TotpEnrollRequest;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpEnrollResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpStatusResponse;
import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.example.EnterpriseRagCommunity.service.access.TotpPolicyService;
import com.example.EnterpriseRagCommunity.service.access.TotpService;
import com.example.EnterpriseRagCommunity.utils.Base32Codec;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTotpService {
    private static final int DEFAULT_SECRET_BYTES = 20;

    private final UsersRepository usersRepository;
    private final TotpSecretsRepository totpSecretsRepository;
    private final TotpService totpService;
    private final TotpCryptoService totpCryptoService;
    private final TotpPolicyService totpPolicyService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public TotpStatusResponse getStatusByEmail(String email) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<TotpSecretsEntity> enabled = totpSecretsRepository.findByUserIdAndEnabledTrue(user.getId());
        Optional<TotpSecretsEntity> current = enabled.stream()
                .max(Comparator.comparing(TotpSecretsEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (current.isPresent()) return toStatus(current.get());

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setMasterKeyConfigured(totpCryptoService.isConfigured());
        resp.setEnabled(false);
        return resp;
    }

    @Transactional(readOnly = true)
    public boolean isEnabledByEmail(String email) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return totpSecretsRepository.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(user.getId()).isPresent();
    }

    @Transactional(readOnly = true)
    public Integer getEnabledDigitsByEmail(String email) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return totpSecretsRepository.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(user.getId())
                .map(TotpSecretsEntity::getDigits)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public void requireValidEnabledCodeByEmail(String email, String code) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!totpCryptoService.isConfigured()) {
            throw new IllegalStateException("TOTP 主密钥未配置，请联系管理员");
        }

        TotpSecretsEntity current = totpSecretsRepository.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalStateException("当前未启用 TOTP"));

        String normalized = code == null ? "" : code.trim();
        int digits = requireValidTotpDigits(current.getDigits());
        if (normalized.length() != digits) {
            throw new IllegalArgumentException("验证码格式不正确，应为 " + digits + " 位数字");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("验证码格式不正确，应为 " + digits + " 位数字");
            }
        }

        byte[] secret = totpCryptoService.decrypt(current.getSecretEncrypted());
        boolean ok = totpService.verifyCode(secret, normalized, current.getAlgorithm(), digits, current.getPeriodSeconds(), current.getSkew());
        if (!ok) throw new IllegalArgumentException("验证码不正确");
    }

    @Transactional
    public TotpEnrollResponse enrollByEmail(String email, TotpEnrollRequest req) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!totpCryptoService.isConfigured()) {
            throw new IllegalStateException("TOTP 主密钥未配置，请联系管理员设置 APP_TOTP_MASTER_KEY（Base64）或 app.security.totp.master-key");
        }

        TotpPolicyService.ResolvedTotpConfig params = totpPolicyService.resolveForEnroll(req);
        byte[] secretBytes = totpService.generateSecretBytes(DEFAULT_SECRET_BYTES);
        String secretBase32 = Base32Codec.encode(secretBytes);
        byte[] encrypted = totpCryptoService.encrypt(secretBytes);

        TotpSecretsEntity entity = new TotpSecretsEntity();
        entity.setUserId(user.getId());
        entity.setSecretEncrypted(encrypted);
        entity.setAlgorithm(params.algorithm());
        entity.setDigits(params.digits());
        entity.setPeriodSeconds(params.periodSeconds());
        entity.setSkew(params.skew());
        entity.setEnabled(false);
        entity.setVerifiedAt(null);
        entity.setCreatedAt(LocalDateTime.now());
        totpSecretsRepository.save(entity);

        String accountLabel = user.getEmail() != null ? user.getEmail() : email;
        String otpauth = buildOtpauthUri(params.issuer(), accountLabel, secretBase32, params);

        TotpEnrollResponse resp = new TotpEnrollResponse();
        resp.setOtpauthUri(otpauth);
        resp.setSecretBase32(secretBase32);
        resp.setAlgorithm(params.algorithm());
        resp.setDigits(params.digits());
        resp.setPeriodSeconds(params.periodSeconds());
        resp.setSkew(params.skew());
        return resp;
    }

    @Transactional
    public TotpStatusResponse verifyByEmail(String email, String password, String code) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("请输入密码");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("密码不正确");
        }
        return verifyByUserWithoutPassword(user, code);
    }

    @Transactional
    public TotpStatusResponse verifyByEmailWithoutPassword(String email, String code) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return verifyByUserWithoutPassword(user, code);
    }

    private TotpStatusResponse verifyByUserWithoutPassword(UsersEntity user, String code) {
        if (!totpCryptoService.isConfigured()) {
            throw new IllegalStateException("TOTP 主密钥未配置，请联系管理员设置 APP_TOTP_MASTER_KEY（Base64）或 app.security.totp.master-key");
        }

        String normalized = code == null ? "" : code.trim();
        if (normalized.length() != 6 && normalized.length() != 8) {
            throw new IllegalArgumentException("验证码格式不正确，应为 6 或 8 位数字");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("验证码格式不正确，应为 6 或 8 位数字");
            }
        }

        List<TotpSecretsEntity> candidates = totpSecretsRepository.findTop5ByUserIdAndEnabledFalseOrderByCreatedAtDesc(user.getId());
        if (candidates == null || candidates.isEmpty()) {
            TotpSecretsEntity latest = totpSecretsRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                    .orElseThrow(() -> new IllegalStateException("请先生成密钥"));
            candidates = List.of(latest);
        }

        TotpSecretsEntity matched = null;
        for (TotpSecretsEntity candidate : candidates) {
            byte[] secret = totpCryptoService.decrypt(candidate.getSecretEncrypted());
            boolean ok = totpService.verifyCode(secret, normalized, candidate.getAlgorithm(), candidate.getDigits(), candidate.getPeriodSeconds(), candidate.getSkew());
            if (ok) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) {
            TotpSecretsEntity latest = candidates.getFirst();
            String cfg = formatConfig(latest);
            String detected = detectAuthenticatorConfig(latest, normalized);
            if (detected != null) {
                throw new IllegalArgumentException(
                        "验证码不正确（请确认使用最新生成的密钥，且手机时间已自动同步）。当前待启用密钥配置：" + cfg
                                + "。检测到你输入的验证码更符合：" + detected
                                + "。这通常是因为认证器未按二维码中的 algorithm/digits/period 参数生效（Google Authenticator 常固定为 SHA1 / 6 位 / 30 秒，即使扫码导入）。"
                                + "若你要使用 " + cfg + "，请使用支持该参数的认证器（例如 Aegis / 2FAS），或改用 " + detected + " 后重新生成密钥再启用。"
                );
            }
            throw new IllegalArgumentException(
                    "验证码不正确（请确认使用最新生成的密钥，且手机时间已自动同步）。当前待启用密钥配置：" + cfg
                            + "。Google Authenticator 的“手动录入密钥”通常仅支持 SHA1 / 6 位 / 30 秒，且扫码导入也可能不支持 SHA256/SHA512、8 位或 60 秒步长；请更换支持该参数的认证器应用，或保持兼容配置后重新生成密钥再启用。"
            );
        }

        List<TotpSecretsEntity> enabled = totpSecretsRepository.findByUserIdAndEnabledTrue(user.getId());
        for (TotpSecretsEntity e : enabled) {
            if (e.getId() != null && e.getId().equals(matched.getId())) continue;
            e.setEnabled(false);
        }
        if (!enabled.isEmpty()) totpSecretsRepository.saveAll(enabled);

        matched.setEnabled(true);
        matched.setVerifiedAt(LocalDateTime.now());
        totpSecretsRepository.save(matched);
        return toStatus(matched);
    }

    private String detectAuthenticatorConfig(TotpSecretsEntity latest, String code) {
        if (latest == null || latest.getSecretEncrypted() == null) return null;
        int codeLen = code == null ? 0 : code.length();
        if (codeLen != 6 && codeLen != 8) return null;

        byte[] secret = totpCryptoService.decrypt(latest.getSecretEncrypted());

        var settings = totpPolicyService.getSettingsOrDefault();
        List<String> allowedAlgorithms = settings == null || settings.getAllowedAlgorithms() == null || settings.getAllowedAlgorithms().isEmpty()
                ? List.of("SHA1", "SHA256", "SHA512")
                : settings.getAllowedAlgorithms();
        List<Integer> allowedDigits = settings == null || settings.getAllowedDigits() == null || settings.getAllowedDigits().isEmpty()
                ? List.of(6, 8)
                : settings.getAllowedDigits();
        List<Integer> allowedPeriods = settings == null || settings.getAllowedPeriodSeconds() == null || settings.getAllowedPeriodSeconds().isEmpty()
                ? List.of(30, 60)
                : settings.getAllowedPeriodSeconds();

        List<Integer> digitsCandidates = allowedDigits.stream().filter(d -> d != null && d == codeLen).toList();
        if (digitsCandidates.isEmpty()) return null;

        Integer maxSkew = settings == null ? null : settings.getMaxSkew();
        int skew = latest.getSkew() == null ? 0 : latest.getSkew();
        skew = Math.max(0, Math.min(10, skew));
        if (maxSkew != null) skew = Math.min(maxSkew, skew);

        String latestAlg = latest.getAlgorithm() == null ? null : latest.getAlgorithm().trim().toUpperCase();
        Integer latestPeriod = latest.getPeriodSeconds();
        List<String> algorithmOrder = new java.util.ArrayList<>();
        if (latestAlg != null && !latestAlg.isBlank()) algorithmOrder.add(latestAlg);
        if (!algorithmOrder.contains("SHA1")) algorithmOrder.add("SHA1");
        for (String a : allowedAlgorithms) {
            String t = a == null ? "" : a.trim().toUpperCase();
            if (t.isEmpty()) continue;
            if (!algorithmOrder.contains(t)) algorithmOrder.add(t);
        }

        List<Integer> periodOrder = new java.util.ArrayList<>();
        if (latestPeriod != null) periodOrder.add(latestPeriod);
        if (!periodOrder.contains(30)) periodOrder.add(30);
        for (Integer p : allowedPeriods) {
            if (p == null) continue;
            if (!periodOrder.contains(p)) periodOrder.add(p);
        }

        for (String alg : algorithmOrder) {
            for (Integer period : periodOrder) {
                for (Integer digits : digitsCandidates) {
                    boolean ok = totpService.verifyCode(secret, code, alg, digits, period, skew);
                    if (ok) {
                        return alg + " / " + digits + " 位 / " + period + " 秒";
                    }
                }
            }
        }
        return null;
    }

    @Transactional
    public TotpStatusResponse disableByEmail(String email, String code) {
        TotpSecretsEntity current = requireLatestEnabledSecret(email);

        byte[] secret = totpCryptoService.decrypt(current.getSecretEncrypted());
        boolean ok = totpService.verifyCode(secret, code, current.getAlgorithm(), current.getDigits(), current.getPeriodSeconds(), current.getSkew());
        if (!ok) throw new IllegalArgumentException("验证码不正确");

        current.setEnabled(false);
        totpSecretsRepository.save(current);
        return toStatus(current);
    }

    @Transactional
    public TotpStatusResponse disableByEmailWithoutTotp(String email) {
        TotpSecretsEntity current = requireLatestEnabledSecret(email);

        current.setEnabled(false);
        totpSecretsRepository.save(current);
        return toStatus(current);
    }

    private TotpSecretsEntity requireLatestEnabledSecret(String email) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!totpCryptoService.isConfigured()) {
            throw new IllegalStateException("TOTP 主密钥未配置，请联系管理员设置 APP_TOTP_MASTER_KEY（Base64）或 app.security.totp.master-key");
        }

        List<TotpSecretsEntity> enabled = totpSecretsRepository.findByUserIdAndEnabledTrue(user.getId());
        return enabled.stream()
                .max(Comparator.comparing(TotpSecretsEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new IllegalStateException("当前未启用 TOTP"));
    }

    private TotpStatusResponse toStatus(TotpSecretsEntity e) {
        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setMasterKeyConfigured(totpCryptoService.isConfigured());
        resp.setEnabled(e.getEnabled());
        resp.setVerifiedAt(e.getVerifiedAt());
        resp.setCreatedAt(e.getCreatedAt());
        resp.setAlgorithm(e.getAlgorithm());
        resp.setDigits(e.getDigits());
        resp.setPeriodSeconds(e.getPeriodSeconds());
        resp.setSkew(e.getSkew());
        return resp;
    }

    private static String buildOtpauthUri(String issuer, String accountLabel, String secretBase32, TotpPolicyService.ResolvedTotpConfig params) {
        String iss = issuer == null || issuer.isBlank() ? "EnterpriseRagCommunity" : issuer.trim();
        String label = iss + ":" + (accountLabel == null ? "" : accountLabel.trim());
        String path = URLEncoder.encode(label, StandardCharsets.UTF_8);
        String qIssuer = URLEncoder.encode(iss, StandardCharsets.UTF_8);
        String qSecret = URLEncoder.encode(secretBase32, StandardCharsets.UTF_8);
        String qAlg = URLEncoder.encode(params.algorithm(), StandardCharsets.UTF_8);
        return "otpauth://totp/" + path
                + "?secret=" + qSecret
                + "&issuer=" + qIssuer
                + "&algorithm=" + qAlg
                + "&digits=" + params.digits()
                + "&period=" + params.periodSeconds();
    }

    private static String formatConfig(TotpSecretsEntity e) {
        if (e == null) return "-";
        String alg = e.getAlgorithm() == null ? "-" : e.getAlgorithm();
        String digits = e.getDigits() == null ? "-" : String.valueOf(e.getDigits());
        String period = e.getPeriodSeconds() == null ? "-" : String.valueOf(e.getPeriodSeconds());
        String skew = e.getSkew() == null ? "-" : String.valueOf(e.getSkew());
        return alg + " / " + digits + " 位 / " + period + " 秒 / skew=" + skew;
    }

    private static int requireValidTotpDigits(Integer configuredDigits) {
        int digits = configuredDigits == null ? 0 : configuredDigits;
        if (digits != 6 && digits != 8) {
            throw new IllegalStateException("TOTP 配置不正确");
        }
        return digits;
    }
}
