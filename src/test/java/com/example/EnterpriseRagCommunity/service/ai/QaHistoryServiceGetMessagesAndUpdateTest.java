package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaMessageDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaHistoryServiceGetMessagesAndUpdateTest {

    @Test
    void getMySessionMessages_should_throw_when_inactive() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(10L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () -> service.getMySessionMessages(1L, 10L));
    }

    @Test
    void getMySessionMessages_should_skip_sources_repo_when_no_assistant() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(10L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        QaMessagesEntity u = new QaMessagesEntity();
        u.setId(1L);
        u.setSessionId(10L);
        u.setRole(MessageRole.USER);
        u.setContent("q");
        u.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(u));
        when(qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        List<QaMessageDTO> out = service.getMySessionMessages(1L, 10L);
        assertEquals(1, out.size());
        assertNull(out.get(0).getSources());

        verify(qaMessageSourcesRepository, never()).findByMessageIdInOrderByMessageIdAscSourceIndexAsc(any());
    }

    @Test
    void getMySessionMessages_should_attach_latency_and_ignore_null_turns_and_sources_rows() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(10L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        LocalDateTime base = LocalDateTime.now().minusMinutes(1);

        QaMessagesEntity u = new QaMessagesEntity();
        u.setId(101L);
        u.setSessionId(10L);
        u.setRole(MessageRole.USER);
        u.setContent("q");
        u.setCreatedAt(base.plusSeconds(1));

        QaMessagesEntity a1 = new QaMessagesEntity();
        a1.setId(102L);
        a1.setSessionId(10L);
        a1.setRole(MessageRole.ASSISTANT);
        a1.setContent("a1");
        a1.setCreatedAt(base.plusSeconds(2));

        QaMessagesEntity a2 = new QaMessagesEntity();
        a2.setId(103L);
        a2.setSessionId(10L);
        a2.setRole(MessageRole.ASSISTANT);
        a2.setContent("a2");
        a2.setCreatedAt(base.plusSeconds(3));

        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(u, a1, a2));

        QaTurnsEntity t = new QaTurnsEntity();
        t.setAnswerMessageId(102L);
        t.setLatencyMs(123);
        t.setFirstTokenLatencyMs(45);

        QaTurnsEntity tBad = new QaTurnsEntity();
        tBad.setAnswerMessageId(null);

        when(qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(Arrays.asList(null, tBad, t));

        QaMessageSourcesEntity rowBad1 = new QaMessageSourcesEntity();
        rowBad1.setMessageId(null);

        QaMessageSourcesEntity rowOk = new QaMessageSourcesEntity();
        rowOk.setMessageId(102L);
        rowOk.setSourceIndex(0);
        rowOk.setTitle("doc");

        when(qaMessageSourcesRepository.findByMessageIdInOrderByMessageIdAscSourceIndexAsc(eq(List.of(102L, 103L))))
                .thenReturn(Arrays.asList(null, rowBad1, rowOk));

        List<QaMessageDTO> out = service.getMySessionMessages(1L, 10L);
        assertEquals(3, out.size());

        assertNull(out.get(0).getSources());

        QaMessageDTO outA1 = out.get(1);
        assertEquals(102L, outA1.getId());
        assertNotNull(outA1.getSources());
        assertEquals(1, outA1.getSources().size());
        assertEquals(Integer.valueOf(123), outA1.getLatencyMs());
        assertEquals(Integer.valueOf(45), outA1.getFirstTokenLatencyMs());

        QaMessageDTO outA2 = out.get(2);
        assertEquals(103L, outA2.getId());
        assertNotNull(outA2.getSources());
        assertEquals(0, outA2.getSources().size());
        assertNull(outA2.getLatencyMs());
        assertNull(outA2.getFirstTokenLatencyMs());
    }

    @Test
    void updateMySession_should_trim_empty_title_to_null_and_update_isActive() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("a@x");
        when(administratorService.findById(1L)).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaSessionsEntity s = new QaSessionsEntity();
        s.setId(10L);
        s.setUserId(1L);
        s.setTitle("old");
        s.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(s));
        when(qaSessionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionUpdateRequest req = new QaSessionUpdateRequest();
        req.setTitle("   ");
        req.setIsActive(false);

        QaSessionDTO out = service.updateMySession(1L, 10L, req);
        assertEquals(10L, out.getId());
        assertNull(out.getTitle());
        assertEquals(false, out.getIsActive());

        verify(auditLogWriter).write(
                eq(1L),
                eq("a@x"),
                eq("QA_SESSION_UPDATE"),
                eq("QA_SESSION"),
                eq(10L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateMySession_should_not_change_title_when_title_null() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("a@x");
        when(administratorService.findById(1L)).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaSessionsEntity s = new QaSessionsEntity();
        s.setId(10L);
        s.setUserId(1L);
        s.setTitle("old");
        s.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(s));
        when(qaSessionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionUpdateRequest req = new QaSessionUpdateRequest();

        QaSessionDTO out = service.updateMySession(1L, 10L, req);
        assertEquals("old", out.getTitle());
    }

    @Test
    void updateMySession_should_write_audit_with_null_actor_when_userId_null() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaSessionsEntity s = new QaSessionsEntity();
        s.setId(10L);
        s.setUserId(null);
        s.setTitle("old");
        s.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(10L, null)).thenReturn(Optional.of(s));
        when(qaSessionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionUpdateRequest req = new QaSessionUpdateRequest();
        req.setTitle("t2");

        service.updateMySession(null, 10L, req);

        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("QA_SESSION_UPDATE"),
                eq("QA_SESSION"),
                eq(10L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateMySession_should_write_audit_with_null_actor_when_admin_service_throws() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        when(administratorService.findById(1L)).thenThrow(new RuntimeException("boom"));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaSessionsEntity s = new QaSessionsEntity();
        s.setId(10L);
        s.setUserId(1L);
        s.setTitle("old");
        s.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(s));
        when(qaSessionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QaHistoryService service = new QaHistoryService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                qaMessageSourcesRepository,
                chatContextGovernanceConfigService,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        QaSessionUpdateRequest req = new QaSessionUpdateRequest();
        req.setTitle("t2");

        service.updateMySession(1L, 10L, req);

        verify(auditLogWriter).write(
                eq(1L),
                eq(null),
                eq("QA_SESSION_UPDATE"),
                eq("QA_SESSION"),
                eq(10L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}
