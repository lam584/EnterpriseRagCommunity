package com.example.EnterpriseRagCommunity.service.init;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class TotpMasterKeyBootstrapService {
    private static final int DEFAULT_KEY_BYTES = 32;
    private static final String ENV_NAME = "APP_TOTP_MASTER_KEY";

    private final SecureRandom secureRandom = new SecureRandom();

    public Result generateAndPersistToOsEnv() {
        String keyBase64 = generateKeyBase64();
        String osName = currentOsName();

        String preferredCommand = "setx /M " + ENV_NAME + " \"" + keyBase64 + "\"";
        String fallbackCommand = "setx " + ENV_NAME + " \"" + keyBase64 + "\"";

        if (!osName.contains("windows")) {
            Result r = new Result();
            r.setKeyBase64(keyBase64);
            r.setEnvVarName(ENV_NAME);
            r.setAttempted(true);
            r.setSucceeded(false);
            r.setScope(null);
            r.setCommand(preferredCommand);
            r.setFallbackCommand(fallbackCommand);
            r.setMessage("当前系统非 Windows，无法自动执行 setx。请手动设置环境变量并重启后端。");
            return r;
        }

        int systemExitCode = runCommandExitCode("cmd.exe", "/c", preferredCommand);
        if (systemExitCode == 0) {
            Result r = new Result();
            r.setKeyBase64(keyBase64);
            r.setEnvVarName(ENV_NAME);
            r.setAttempted(true);
            r.setSucceeded(true);
            r.setScope("SYSTEM");
            r.setCommand(preferredCommand);
            r.setFallbackCommand(fallbackCommand);
            r.setMessage("已写入系统级环境变量（需重启后端进程后生效）。");
            return r;
        }

        int userExitCode = runCommandExitCode("cmd.exe", "/c", fallbackCommand);
        if (userExitCode == 0) {
            Result r = new Result();
            r.setKeyBase64(keyBase64);
            r.setEnvVarName(ENV_NAME);
            r.setAttempted(true);
            r.setSucceeded(true);
            r.setScope("USER");
            r.setCommand(fallbackCommand);
            r.setFallbackCommand(fallbackCommand);
            r.setMessage("已写入用户级环境变量（需重启后端进程后生效）。");
            return r;
        }

        Result r = new Result();
        r.setKeyBase64(keyBase64);
        r.setEnvVarName(ENV_NAME);
        r.setAttempted(true);
        r.setSucceeded(false);
        r.setScope(null);
        r.setCommand(preferredCommand);
        r.setFallbackCommand(fallbackCommand);
        r.setMessage("自动写入环境变量失败，请手动执行命令并重启后端。");
        r.setError("systemExitCode=" + systemExitCode + " userExitCode=" + userExitCode);
        return r;
    }

    String currentOsName() {
        return String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT);
    }

    int runCommandExitCode(String... cmd) {
        return execCmd(cmd).exitCode;
    }

    private String generateKeyBase64() {
        byte[] raw = new byte[TotpMasterKeyBootstrapService.DEFAULT_KEY_BYTES];
        secureRandom.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static ExecResult execCmd(String... cmd) {
        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!out.isEmpty()) out.append('\n');
                    out.append(line);
                }
            }
            int code = process.waitFor();
            return new ExecResult(code, out.toString());
        } catch (Exception e) {
            return new ExecResult(1, e.getMessage() == null ? "exec failed" : e.getMessage());
        }
    }

    private record ExecResult(int exitCode, String output) {}

    @Setter
    @Getter
    public static class Result {
        private String envVarName;
        private String keyBase64;
        private boolean attempted;
        private boolean succeeded;
        private String scope;
        private String command;
        private String fallbackCommand;
        private String message;
        private String error;

    }
}
