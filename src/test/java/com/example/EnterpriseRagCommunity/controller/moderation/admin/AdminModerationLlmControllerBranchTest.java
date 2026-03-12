package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationLlmControllerBranchTest {

    @Mock
    private AdminModerationLlmService service;
    @Mock
    private AuditLogWriter auditLogWriter;

    @Test
    void getConfig_returnsServiceResult() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationConfigDTO cfg = new LlmModerationConfigDTO();
        cfg.setId(1L);
        when(service.getConfig()).thenReturn(cfg);

        LlmModerationConfigDTO actual = controller.getConfig();

        assertSame(cfg, actual);
    }

    @Test
    void upsertConfig_success_withNullPrincipal() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        LlmModerationConfigDTO saved = new LlmModerationConfigDTO();
        saved.setVersion(2);
        when(service.upsertConfig(payload, null, null)).thenReturn(saved);

        LlmModerationConfigDTO actual = controller.upsertConfig(payload, null);

        assertSame(saved, actual);
        verify(auditLogWriter, never()).write(any(), any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void upsertConfig_success_withPrincipal() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        LlmModerationConfigDTO saved = new LlmModerationConfigDTO();
        when(service.upsertConfig(payload, null, "owner@example.com")).thenReturn(saved);

        LlmModerationConfigDTO actual = controller.upsertConfig(payload, () -> "owner@example.com");

        assertSame(saved, actual);
    }

    @Test
    void upsertConfig_failure_writesAuditAndRethrows() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        RuntimeException ex = new RuntimeException(" \n\t failed-msg \t ");
        when(service.upsertConfig(any(), eq(null), eq("u@example.com"))).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> controller.upsertConfig(payload, () -> "u@example.com"));
        assertSame(ex, thrown);

        verify(auditLogWriter).write(eq(null), eq("u@example.com"), eq("CONFIG_CHANGE"), eq("MODERATION_LLM_CONFIG"),
                eq(null), any(), eq("failed-msg"), eq(null), eq(Map.of()));
    }

    @Test
    void upsertConfig_failure_whenAuditWriterThrows_stillRethrowOriginal() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        RuntimeException ex = new RuntimeException("   ");
        when(service.upsertConfig(any(), eq(null), eq("u@example.com"))).thenThrow(ex);
        doThrow(new RuntimeException("audit-fail")).when(auditLogWriter)
                .write(any(), any(), any(), any(), any(), any(), any(), any(), anyMap());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> controller.upsertConfig(payload, () -> "u@example.com"));
        assertSame(ex, thrown);
    }

    @Test
    void upsertConfig_failure_withNullPrincipal_shouldAuditWithNullUser() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        RuntimeException ex = new RuntimeException("x");
        when(service.upsertConfig(any(), eq(null), eq(null))).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.upsertConfig(payload, null));

        assertSame(ex, thrown);
        verify(auditLogWriter).write(eq(null), eq(null), eq("CONFIG_CHANGE"), eq("MODERATION_LLM_CONFIG"),
                eq(null), any(), eq("x"), eq(null), eq(Map.of()));
    }

    @Test
    void test_success_withQueueId_writesSuccessAuditForQueue() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(88L);
        req.setText("hello");
        LlmModerationTestRequest.ImageInput image = new LlmModerationTestRequest.ImageInput();
        image.setUrl("http://img");
        req.setImages(List.of(image));

        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecision("APPROVE");
        resp.setScore(0.1);
        resp.setInputMode("TEXT");
        resp.setLatencyMs(15L);
        when(service.test(req)).thenReturn(resp);

        LlmModerationTestResponse actual = controller.test(req, () -> "alice@example.com");

        assertSame(resp, actual);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), eq("alice@example.com"), eq("LLM_TEST"), eq("MODERATION_QUEUE"), eq(88L),
                any(), eq("LLM 试运行"), eq(null), captor.capture());
        Map<String, Object> details = captor.getValue();
        assertEquals(88L, details.get("queueId"));
        assertEquals(true, details.get("hasText"));
        assertEquals(1, details.get("images"));
        assertEquals("APPROVE", details.get("decision"));
    }

    @Test
    void test_success_withNullRequest_writesSuccessAuditForSystem() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        when(service.test(null)).thenReturn(resp);

        LlmModerationTestResponse actual = controller.test(null, null);

        assertSame(resp, actual);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), eq(null), eq("LLM_TEST"), eq("SYSTEM"), eq(null),
                any(), eq("LLM 试运行"), eq(null), captor.capture());
        assertNull(captor.getValue().get("queueId"));
        assertEquals(false, captor.getValue().get("hasText"));
        assertEquals(0, captor.getValue().get("images"));
    }

    @Test
    void test_failure_writesFailAuditAndRethrows() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(7L);
        req.setText("   ");
        RuntimeException ex = new RuntimeException(" \n broken \r ");
        when(service.test(req)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.test(req, () -> "bob@example.com"));
        assertSame(ex, thrown);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), eq("bob@example.com"), eq("LLM_TEST"), eq("MODERATION_QUEUE"), eq(7L),
                any(), eq("broken"), eq(null), captor.capture());
        assertEquals(false, captor.getValue().get("hasText"));
    }

    @Test
    void test_success_whenResponseIsNull_stillWritesAudit() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(9L);
        when(service.test(req)).thenReturn(null);

        var out = controller.test(req, () -> "u@example.com");

        assertNull(out);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), eq("u@example.com"), eq("LLM_TEST"), eq("MODERATION_QUEUE"), eq(9L),
                any(), eq("LLM 试运行"), eq(null), captor.capture());
        assertNull(captor.getValue().get("decision"));
    }

    @Test
    void test_success_whenAuditThrows_shouldStillReturnResponse() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(10L);
        req.setText("t");
        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        when(service.test(req)).thenReturn(resp);
        doThrow(new RuntimeException("audit")).when(auditLogWriter)
                .write(any(), any(), any(), any(), any(), any(), any(), any(), anyMap());

        var out = controller.test(req, () -> "u@example.com");

        assertSame(resp, out);
    }

    @Test
    void test_failure_whenAuditThrows_shouldStillRethrowOriginal() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        RuntimeException ex = new RuntimeException("bad");
        when(service.test(null)).thenThrow(ex);
        doThrow(new RuntimeException("audit")).when(auditLogWriter)
                .write(any(), any(), any(), any(), any(), any(), any(), any(), anyMap());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.test(null, () -> "u@example.com"));
        assertSame(ex, thrown);
    }

    @Test
    void test_failure_withBlankMessageAndSystemTarget() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        RuntimeException ex = new RuntimeException("   ");
        when(service.test(null)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.test(null, null));

        assertSame(ex, thrown);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), eq(null), eq("LLM_TEST"), eq("SYSTEM"), eq(null),
                any(), eq(null), eq(null), captor.capture());
        assertEquals(0, captor.getValue().get("images"));
    }

    @Test
    void test_success_systemTarget_whenQueueIdNull() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(null);
        req.setText("  ");
        req.setImages(null);
        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        when(service.test(req)).thenReturn(resp);

        var out = controller.test(req, () -> "ops@example.com");

        assertSame(resp, out);
        verify(auditLogWriter).write(eq(null), eq("ops@example.com"), eq("LLM_TEST"), eq("SYSTEM"), eq(null),
                any(), eq("LLM 试运行"), eq(null), any());
    }

    @Test
    void test_failure_withNonBlankTextAndImages_shouldCoverFailureBranches() {
        AdminModerationLlmController controller = new AdminModerationLlmController(service, auditLogWriter);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(null);
        req.setText("abc");
        LlmModerationTestRequest.ImageInput image = new LlmModerationTestRequest.ImageInput();
        image.setUrl("https://a");
        req.setImages(List.of(image));
        RuntimeException ex = new RuntimeException("e");
        when(service.test(req)).thenThrow(ex);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.test(req, () -> "ops@example.com"));

        assertSame(ex, thrown);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(eq(null), eq("ops@example.com"), eq("LLM_TEST"), eq("SYSTEM"), eq(null),
                any(), eq("e"), eq(null), captor.capture());
        assertEquals(true, captor.getValue().get("hasText"));
        assertEquals(1, captor.getValue().get("images"));
    }

    @Test
    void safeText_shouldCoverMaxLenBranches() {
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationLlmController.class, "safeText", null, 1));
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationLlmController.class, "safeText", " \n\t ", 2));
        assertEquals("", ReflectionTestUtils.invokeMethod(AdminModerationLlmController.class, "safeText", "abc", -1));
        assertEquals("", ReflectionTestUtils.invokeMethod(AdminModerationLlmController.class, "safeText", "abc", 0));
        assertEquals("abc", ReflectionTestUtils.invokeMethod(AdminModerationLlmController.class, "safeText", "abc", 5));
        assertEquals("ab", ReflectionTestUtils.invokeMethod(AdminModerationLlmController.class, "safeText", "abcd", 2));
    }
}
