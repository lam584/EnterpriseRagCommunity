package com.example.EnterpriseRagCommunity.service.init;

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
        String keyBase64 = generateKeyBase64(DEFAULT_KEY_BYTES);
        String osName = String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT);

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

        ExecResult system = execCmd("cmd.exe", "/c", preferredCommand);
        if (system.exitCode == 0) {
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

        ExecResult user = execCmd("cmd.exe", "/c", fallbackCommand);
        if (user.exitCode == 0) {
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
        r.setError("systemExitCode=" + system.exitCode + " userExitCode=" + user.exitCode);
        return r;
    }

    private String generateKeyBase64(int bytes) {
        byte[] raw = new byte[bytes];
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

        public String getEnvVarName() {
            return envVarName;
        }

        public void setEnvVarName(String envVarName) {
            this.envVarName = envVarName;
        }

        public String getKeyBase64() {
            return keyBase64;
        }

        public void setKeyBase64(String keyBase64) {
            this.keyBase64 = keyBase64;
        }

        public boolean isAttempted() {
            return attempted;
        }

        public void setAttempted(boolean attempted) {
            this.attempted = attempted;
        }

        public boolean isSucceeded() {
            return succeeded;
        }

        public void setSucceeded(boolean succeeded) {
            this.succeeded = succeeded;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getFallbackCommand() {
            return fallbackCommand;
        }

        public void setFallbackCommand(String fallbackCommand) {
            this.fallbackCommand = fallbackCommand;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}

