package com.example.EnterpriseRagCommunity.service.init;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class TotpMasterKeyBootstrapServiceBranchTest {

    @Test
    void generateAndPersistToOsEnv_shouldReturnManualMessage_whenNonWindows() {
        TestableTotpService service = new TestableTotpService("linux", List.of());

        TotpMasterKeyBootstrapService.Result result = service.generateAndPersistToOsEnv();

        assertEquals("APP_TOTP_MASTER_KEY", result.getEnvVarName());
        assertTrue(result.isAttempted());
        assertFalse(result.isSucceeded());
        assertNull(result.getScope());
        assertTrue(result.getMessage().contains("非 Windows"));
        assertTrue(result.getCommand().contains("setx /M APP_TOTP_MASTER_KEY"));
        assertTrue(result.getFallbackCommand().contains("setx APP_TOTP_MASTER_KEY"));
        assertNotNull(result.getKeyBase64());
        assertFalse(result.getKeyBase64().isBlank());
    }

    @Test
    void generateAndPersistToOsEnv_shouldReturnSystemSuccess_whenSystemSetxSucceeds() {
        TestableTotpService service = new TestableTotpService("windows", List.of(0));

        TotpMasterKeyBootstrapService.Result result = service.generateAndPersistToOsEnv();

        assertTrue(result.isSucceeded());
        assertEquals("SYSTEM", result.getScope());
        assertTrue(result.getCommand().contains("setx /M APP_TOTP_MASTER_KEY"));
    }

    @Test
    void generateAndPersistToOsEnv_shouldReturnUserScope_whenSystemFailsAndUserSucceeds() {
        TestableTotpService service = new TestableTotpService("windows", List.of(2, 0));

        TotpMasterKeyBootstrapService.Result result = service.generateAndPersistToOsEnv();

        assertTrue(result.isSucceeded());
        assertEquals("USER", result.getScope());
        assertTrue(result.getCommand().contains("setx APP_TOTP_MASTER_KEY"));
    }

    @Test
    void generateAndPersistToOsEnv_shouldReturnError_whenSystemAndUserBothFail() {
        TestableTotpService service = new TestableTotpService("windows", List.of(3, 7));

        TotpMasterKeyBootstrapService.Result result = service.generateAndPersistToOsEnv();

        assertFalse(result.isSucceeded());
        assertNull(result.getScope());
        assertTrue(result.getMessage().contains("自动写入环境变量失败"));
        assertEquals("systemExitCode=3 userExitCode=7", result.getError());
    }

    @Test
    void execCmd_shouldJoinLinesAndReturnExitCode_whenProcessSucceeds() throws Exception {
        Process process = mock(Process.class);

        try (MockedConstruction<ProcessBuilder> mockedPb = mockConstruction(ProcessBuilder.class, (pb, _ctx) -> {
            when(pb.redirectErrorStream(true)).thenReturn(pb);
            when(pb.start()).thenReturn(process);
        })) {
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream("line1\nline2\n".getBytes(StandardCharsets.UTF_8)));
            doReturn(0).when(process).waitFor();

            Object execResult = invokeStaticExecCmd(new String[]{"cmd.exe", "/c", "echo"});

            assertEquals(1, mockedPb.constructed().size());
            assertEquals(0, readExecResultExitCode(execResult));
            assertEquals("line1\nline2", readExecResultOutput(execResult));
        }
    }

    @Test
    void execCmd_shouldReturnFallbackError_whenStartThrowsException() throws Exception {
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class, (pb, _ctx) -> {
            when(pb.redirectErrorStream(true)).thenReturn(pb);
            when(pb.start()).thenThrow(new RuntimeException("boom"));
        })) {
            Object execResult = invokeStaticExecCmd(new String[]{"cmd.exe", "/c", "echo"});

            assertEquals(1, readExecResultExitCode(execResult));
            assertEquals("boom", readExecResultOutput(execResult));
        }
    }

    @Test
    void execCmd_shouldUseDefaultMessage_whenExceptionMessageIsNull() throws Exception {
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(ProcessBuilder.class, (pb, _ctx) -> {
            when(pb.redirectErrorStream(true)).thenReturn(pb);
            when(pb.start()).thenThrow(new RuntimeException());
        })) {
            Object execResult = invokeStaticExecCmd(new String[]{"cmd.exe", "/c", "echo"});

            assertEquals(1, readExecResultExitCode(execResult));
            assertEquals("exec failed", readExecResultOutput(execResult));
        }
    }

    private static Object invokeStaticExecCmd(String[] command) throws Exception {
        Method m = TotpMasterKeyBootstrapService.class.getDeclaredMethod("execCmd", String[].class);
        m.setAccessible(true);
        return m.invoke(null, (Object) command);
    }

    private static int readExecResultExitCode(Object execResult) throws Exception {
        Method exitCode = execResult.getClass().getDeclaredMethod("exitCode");
        exitCode.setAccessible(true);
        return (int) exitCode.invoke(execResult);
    }

    private static String readExecResultOutput(Object execResult) throws Exception {
        Method output = execResult.getClass().getDeclaredMethod("output");
        output.setAccessible(true);
        return String.valueOf(output.invoke(execResult));
    }

    private static class TestableTotpService extends TotpMasterKeyBootstrapService {
        private final String osName;
        private final Queue<Integer> exitCodes;

        private TestableTotpService(String osName, List<Integer> exitCodes) {
            this.osName = osName;
            this.exitCodes = new ArrayDeque<>(exitCodes);
        }

        @Override
        String currentOsName() {
            return osName;
        }

        @Override
        int runCommandExitCode(String... cmd) {
            Integer code = exitCodes.poll();
            return code == null ? 1 : code;
        }
    }
}
