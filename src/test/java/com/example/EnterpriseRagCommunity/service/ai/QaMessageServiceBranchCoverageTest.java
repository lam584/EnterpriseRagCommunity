package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QaMessageServiceBranchCoverageTest {

    private static class Deps {
        final QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        final QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        final QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        final AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        final AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        final AdministratorService administratorService = mock(AdministratorService.class);
        final QaMessageService svc = new QaMessageService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );
    }

    @Test
    void updateMyMessage_userIdNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.updateMyMessage(null, 1L, "x")
        );
        assertEquals("userId is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void updateMyMessage_messageIdNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.updateMyMessage(1L, null, "x")
        );
        assertEquals("messageId is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void updateMyMessage_contentNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.updateMyMessage(1L, 2L, null)
        );
        assertEquals("content is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void updateMyMessage_contentBlank_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.updateMyMessage(1L, 2L, "   ")
        );
        assertEquals("content is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void updateMyMessage_messageNotFound_throws() {
        Deps d = new Deps();
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> d.svc.updateMyMessage(1L, 2L, "x")
        );
        assertEquals("message not found", ex.getMessage());
        verify(d.qaMessagesRepository).findById(2L);
        verify(d.qaSessionsRepository, never()).findByIdAndUserId(anyLong(), anyLong());
        verify(d.qaMessagesRepository, never()).save(any());
        verifyNoInteractions(d.auditLogWriter, d.auditDiffBuilder, d.administratorService, d.qaTurnsRepository);
    }

    @Test
    void updateMyMessage_sessionNotFound_throws() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        msg.setContent("old");
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> d.svc.updateMyMessage(1L, 2L, "x")
        );
        assertEquals("session not found", ex.getMessage());
        verify(d.qaMessagesRepository).findById(2L);
        verify(d.qaSessionsRepository).findByIdAndUserId(200L, 1L);
        verify(d.qaMessagesRepository, never()).save(any());
        verifyNoInteractions(d.auditLogWriter, d.auditDiffBuilder, d.administratorService, d.qaTurnsRepository);
    }

    @Test
    void updateMyMessage_sessionInactive_throws() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        msg.setContent("old");
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.updateMyMessage(1L, 2L, "x")
        );
        assertEquals("session inactive", ex.getMessage());
        verify(d.qaMessagesRepository, never()).save(any());
        verifyNoInteractions(d.auditLogWriter, d.auditDiffBuilder, d.administratorService, d.qaTurnsRepository);
    }

    @Test
    void updateMyMessage_roleAssistant_doesNotNullTokensIn() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent("old");
        msg.setTokensIn(123);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));
        when(d.qaMessagesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(d.administratorService.findById(1L)).thenReturn(Optional.empty());

        d.svc.updateMyMessage(1L, 2L, "new");

        assertEquals("new", msg.getContent());
        assertEquals(123, msg.getTokensIn());
        verify(d.qaMessagesRepository).save(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_UPDATE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("更新对话消息"),
                eq(null),
                any()
        );
    }

    @Test
    void updateMyMessage_adminServiceThrows_actorNameNull() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        msg.setContent("old");
        msg.setTokensIn(123);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        when(d.qaMessagesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(d.administratorService.findById(1L)).thenThrow(new RuntimeException("boom"));

        d.svc.updateMyMessage(1L, 2L, "new");

        assertEquals("new", msg.getContent());
        assertEquals(null, msg.getTokensIn());
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_UPDATE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("更新对话消息"),
                eq(null),
                any()
        );
    }

    @Test
    void toggleMyMessageFavorite_userIdNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.toggleMyMessageFavorite(null, 1L)
        );
        assertEquals("userId is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void toggleMyMessageFavorite_messageIdNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.toggleMyMessageFavorite(1L, null)
        );
        assertEquals("messageId is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void toggleMyMessageFavorite_messageNotFound_throws() {
        Deps d = new Deps();
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> d.svc.toggleMyMessageFavorite(1L, 2L)
        );
        assertEquals("message not found", ex.getMessage());
        verify(d.qaSessionsRepository, never()).findByIdAndUserId(anyLong(), anyLong());
        verify(d.qaMessagesRepository, never()).save(any());
        verifyNoInteractions(d.auditLogWriter, d.auditDiffBuilder, d.administratorService, d.qaTurnsRepository);
    }

    @Test
    void toggleMyMessageFavorite_sessionNotFound_throws() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setIsFavorite(false);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> d.svc.toggleMyMessageFavorite(1L, 2L)
        );
        assertEquals("session not found", ex.getMessage());
        verify(d.qaMessagesRepository, never()).save(any());
        verifyNoInteractions(d.auditLogWriter, d.auditDiffBuilder, d.administratorService, d.qaTurnsRepository);
    }

    @Test
    void toggleMyMessageFavorite_isFavoriteTrue_togglesToFalse() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setIsFavorite(true);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent("a");
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        when(d.qaMessagesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(d.administratorService.findById(1L)).thenReturn(Optional.empty());

        boolean out = d.svc.toggleMyMessageFavorite(1L, 2L);

        assertEquals(false, out);
        assertEquals(false, msg.getIsFavorite());
        verify(d.qaMessagesRepository).save(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_FAVORITE_TOGGLE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("收藏/取消收藏对话消息"),
                eq(null),
                any()
        );
    }

    @Test
    void toggleMyMessageFavorite_isFavoriteNull_togglesToTrue_andSummarizeNullFields() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setIsFavorite(null);
        msg.setRole(null);
        msg.setContent(null);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        when(d.qaMessagesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.auditDiffBuilder.build(any(), any())).thenReturn(Map.of());
        when(d.administratorService.findById(1L)).thenReturn(Optional.empty());

        boolean out = d.svc.toggleMyMessageFavorite(1L, 2L);

        assertEquals(true, out);
        assertEquals(true, msg.getIsFavorite());
        verify(d.qaMessagesRepository).save(same(msg));
    }

    @Test
    void deleteMyMessage_userIdNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.deleteMyMessage(null, 1L)
        );
        assertEquals("userId is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void deleteMyMessage_messageIdNull_throws() {
        Deps d = new Deps();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.deleteMyMessage(1L, null)
        );
        assertEquals("messageId is required", ex.getMessage());
        verifyNoInteractions(d.qaMessagesRepository, d.qaSessionsRepository, d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void deleteMyMessage_messageNotFound_throws() {
        Deps d = new Deps();
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> d.svc.deleteMyMessage(1L, 2L)
        );
        assertEquals("message not found", ex.getMessage());
        verify(d.qaSessionsRepository, never()).findByIdAndUserId(anyLong(), anyLong());
        verifyNoInteractions(d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void deleteMyMessage_sessionNotFound_throws() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> d.svc.deleteMyMessage(1L, 2L)
        );
        assertEquals("session not found", ex.getMessage());
        verifyNoInteractions(d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void deleteMyMessage_sessionInactive_throws() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> d.svc.deleteMyMessage(1L, 2L)
        );
        assertEquals("session inactive", ex.getMessage());
        verifyNoInteractions(d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void deleteMyMessage_userRole_noTurn_deletesOnlyQuestion_andWritesAudit() {
        Deps d = new Deps();

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("alice@example.com");
        when(d.administratorService.findById(1L)).thenReturn(Optional.of(u));

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        msg.setContent("hi");
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        when(d.qaTurnsRepository.findByQuestionMessageId(2L)).thenReturn(Optional.empty());

        ArgumentCaptor<Map> details = ArgumentCaptor.forClass(Map.class);

        d.svc.deleteMyMessage(1L, 2L);

        verify(d.qaTurnsRepository, never()).delete(any(QaTurnsEntity.class));
        verify(d.qaMessagesRepository, never()).deleteById(anyLong());
        verify(d.qaMessagesRepository).delete(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                eq("alice@example.com"),
                eq("QA_MESSAGE_DELETE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("删除对话消息"),
                eq(null),
                details.capture()
        );
        assertEquals(2L, details.getValue().get("id"));
        assertEquals("USER", details.getValue().get("role"));
        assertEquals(2, details.getValue().get("contentLen"));
        assertTrue(!details.getValue().containsKey("deletedAnswerMessageId"));
    }

    @Test
    void deleteMyMessage_userRole_turnPresent_answerNull_deletesTurnAndQuestion_andAuditsNullAnswerId() {
        Deps d = new Deps();
        when(d.administratorService.findById(1L)).thenReturn(Optional.empty());

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(9L);
        turn.setQuestionMessageId(2L);
        turn.setAnswerMessageId(null);
        when(d.qaTurnsRepository.findByQuestionMessageId(2L)).thenReturn(Optional.of(turn));

        ArgumentCaptor<Map> details = ArgumentCaptor.forClass(Map.class);

        d.svc.deleteMyMessage(1L, 2L);

        verify(d.qaTurnsRepository).delete(same(turn));
        verify(d.qaMessagesRepository, never()).deleteById(anyLong());
        verify(d.qaMessagesRepository).delete(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_DELETE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("删除对话消息"),
                eq(null),
                details.capture()
        );
        assertTrue(details.getValue().containsKey("deletedAnswerMessageId"));
        assertEquals(null, details.getValue().get("deletedAnswerMessageId"));
    }

    @Test
    void deleteMyMessage_userRole_turnPresent_answerNotNull_deletesTurnAnswerAndQuestion_andAuditsAnswerId() {
        Deps d = new Deps();
        when(d.administratorService.findById(1L)).thenThrow(new RuntimeException("boom"));

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(9L);
        turn.setQuestionMessageId(2L);
        turn.setAnswerMessageId(400L);
        when(d.qaTurnsRepository.findByQuestionMessageId(2L)).thenReturn(Optional.of(turn));

        ArgumentCaptor<Map> details = ArgumentCaptor.forClass(Map.class);

        d.svc.deleteMyMessage(1L, 2L);

        verify(d.qaTurnsRepository).delete(same(turn));
        verify(d.qaMessagesRepository).deleteById(400L);
        verify(d.qaMessagesRepository).delete(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_DELETE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("删除对话消息"),
                eq(null),
                details.capture()
        );
        assertEquals(400L, details.getValue().get("deletedAnswerMessageId"));
    }

    @Test
    void deleteMyMessage_assistant_withTurn_nullsTurnAnswerId_savesTurn_deletesMsg_andWritesAudit() {
        Deps d = new Deps();
        when(d.administratorService.findById(1L)).thenReturn(Optional.empty());

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent("a");
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(9L);
        turn.setAnswerMessageId(2L);
        when(d.qaTurnsRepository.findByAnswerMessageId(2L)).thenReturn(Optional.of(turn));
        when(d.qaTurnsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Map> details = ArgumentCaptor.forClass(Map.class);

        d.svc.deleteMyMessage(1L, 2L);

        verify(d.qaTurnsRepository).save(argThat(t -> t.getAnswerMessageId() == null));
        verify(d.qaMessagesRepository).delete(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_DELETE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("删除对话消息"),
                eq(null),
                details.capture()
        );
        assertEquals("ASSISTANT", details.getValue().get("role"));
        assertEquals(1, details.getValue().get("contentLen"));
        verify(d.qaTurnsRepository, never()).delete(any(QaTurnsEntity.class));
        verify(d.qaMessagesRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMyMessage_assistant_withoutTurn_deletesMsg_andWritesAudit() {
        Deps d = new Deps();
        when(d.administratorService.findById(1L)).thenReturn(Optional.empty());

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.ASSISTANT);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        when(d.qaTurnsRepository.findByAnswerMessageId(2L)).thenReturn(Optional.empty());

        d.svc.deleteMyMessage(1L, 2L);

        verify(d.qaTurnsRepository, never()).save(any());
        verify(d.qaMessagesRepository).delete(same(msg));
        verify(d.auditLogWriter).write(
                eq(1L),
                isNull(),
                eq("QA_MESSAGE_DELETE"),
                eq("QA_MESSAGE"),
                eq(2L),
                eq(AuditResult.SUCCESS),
                eq("删除对话消息"),
                eq(null),
                any()
        );
    }

    @Test
    void deleteMyMessage_roleSystem_doesNothing() {
        Deps d = new Deps();

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(2L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.SYSTEM);
        when(d.qaMessagesRepository.findById(2L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(d.qaSessionsRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(session));

        d.svc.deleteMyMessage(1L, 2L);

        verify(d.qaMessagesRepository, never()).delete(any(QaMessagesEntity.class));
        verifyNoInteractions(d.qaTurnsRepository, d.auditLogWriter, d.auditDiffBuilder, d.administratorService);
    }

    @Test
    void privateHelpers_summarizeMessageForAudit_handlesNull_andResolveActor_handlesNullUserId() throws Exception {
        Deps d = new Deps();

        Method summarize = QaMessageService.class.getDeclaredMethod("summarizeMessageForAudit", QaMessagesEntity.class);
        summarize.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) summarize.invoke(null, new Object[]{null});
        assertNotNull(out);
        assertTrue(out.isEmpty());

        Method resolveActor = QaMessageService.class.getDeclaredMethod("resolveActorNameOrNull", Long.class);
        resolveActor.setAccessible(true);
        String actor = (String) resolveActor.invoke(d.svc, new Object[]{null});
        assertEquals(null, actor);
    }
}
