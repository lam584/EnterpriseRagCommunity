package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaCompressContextResultDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaHistoryServiceCompressContextTest {

    @Test
    void compressMySessionContext_should_require_owner_session() {
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

        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.compressMySessionContext(1L, 10L));
    }

    @Test
    void compressMySessionContext_should_compress_and_delete_old_messages() {
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
        session.setTitle("t");
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setIsActive(true);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setCompressionEnabled(true);
        cfg.setCompressionKeepLastMessages(2);
        cfg.setCompressionPerMessageSnippetChars(50);
        cfg.setCompressionMaxChars(2000);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        QaMessagesEntity m1 = msg(1L, 10L, MessageRole.USER, "q1", base.plusSeconds(1));
        QaMessagesEntity m2 = msg(2L, 10L, MessageRole.ASSISTANT, "a1", base.plusSeconds(2));
        QaMessagesEntity m3 = msg(3L, 10L, MessageRole.USER, "q2", base.plusSeconds(3));
        QaMessagesEntity m4 = msg(4L, 10L, MessageRole.ASSISTANT, "a2", base.plusSeconds(4));
        QaMessagesEntity m5 = msg(5L, 10L, MessageRole.USER, "q3", base.plusSeconds(5));
        QaMessagesEntity m6 = msg(6L, 10L, MessageRole.ASSISTANT, "a3", base.plusSeconds(6));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(m1, m2, m3, m4, m5, m6));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            e.setId(999L);
            return e;
        });

        QaTurnsEntity t1 = new QaTurnsEntity();
        t1.setId(100L);
        t1.setSessionId(10L);
        t1.setQuestionMessageId(1L);
        t1.setAnswerMessageId(2L);
        t1.setCreatedAt(base.plusSeconds(2));

        QaTurnsEntity t2 = new QaTurnsEntity();
        t2.setId(101L);
        t2.setSessionId(10L);
        t2.setQuestionMessageId(5L);
        t2.setAnswerMessageId(6L);
        t2.setCreatedAt(base.plusSeconds(6));

        when(qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(t1, t2));

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertNotNull(out);
        assertEquals(10L, out.getSessionId());
        assertEquals(999L, out.getSummaryMessageId());
        assertEquals(4, out.getCompressedDeletedCount());
        assertEquals(2, out.getKeptLast());
        assertNotNull(out.getSummary());

        verify(qaMessageSourcesRepository, times(1)).deleteByMessageIdIn(eq(List.of(2L, 4L)));
        verify(qaTurnsRepository, times(1)).delete(eq(t1));
        verify(qaMessagesRepository, times(1)).deleteAllById(eq(List.of(1L, 2L, 3L, 4L)));
    }

    @Test
    void compressMySessionContext_should_noop_when_disabled_or_insufficient_messages() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfgDisabled = new ChatContextGovernanceConfigDTO();
        cfgDisabled.setCompressionEnabled(false);
        cfgDisabled.setCompressionKeepLastMessages(2);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfgDisabled);

        QaCompressContextResultDTO out1 = service.compressMySessionContext(1L, 10L);
        assertNull(out1.getSummaryMessageId());
        assertEquals(0, out1.getCompressedDeletedCount());

        ChatContextGovernanceConfigDTO cfgEnabled = new ChatContextGovernanceConfigDTO();
        cfgEnabled.setCompressionEnabled(true);
        cfgEnabled.setCompressionKeepLastMessages(10);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfgEnabled);

        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        QaMessagesEntity m1 = msg(1L, 10L, MessageRole.USER, "q1", base.plusSeconds(1));
        QaMessagesEntity m2 = msg(2L, 10L, MessageRole.ASSISTANT, "a1", base.plusSeconds(2));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(m1, m2));

        QaCompressContextResultDTO out2 = service.compressMySessionContext(1L, 10L);
        assertNull(out2.getSummaryMessageId());
        assertEquals(0, out2.getCompressedDeletedCount());
    }

    @Test
    void compressMySessionContext_should_throw_when_session_inactive() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () -> service.compressMySessionContext(1L, 10L));
    }

    @Test
    void compressMySessionContext_should_noop_when_cfg_null() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(null);

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertEquals(10L, out.getSessionId());
        assertEquals(0, out.getKeptLast());
        assertNull(out.getSummaryMessageId());
        assertEquals(0, out.getCompressedDeletedCount());
        assertEquals("", out.getSummary());

        verify(qaMessagesRepository, never()).findBySessionIdOrderByCreatedAtAsc(anyLong());
        verify(qaMessageSourcesRepository, never()).deleteByMessageIdIn(any());
        verify(qaTurnsRepository, never()).findBySessionIdOrderByCreatedAtAsc(anyLong());
        verify(qaMessagesRepository, never()).deleteAllById(any());
    }

    @Test
    void compressMySessionContext_should_handle_keepLast_null_and_empty_msgs() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setCompressionEnabled(true);
        cfg.setCompressionKeepLastMessages(null);
        cfg.setCompressionPerMessageSnippetChars(20);
        cfg.setCompressionMaxChars(2000);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfg);

        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertEquals(0, out.getKeptLast());
        assertNull(out.getSummaryMessageId());
        assertEquals(0, out.getCompressedDeletedCount());
        assertEquals("", out.getSummary());

        verify(qaMessagesRepository, never()).save(any());
        verify(qaMessageSourcesRepository, never()).deleteByMessageIdIn(any());
        verify(qaMessagesRepository, never()).deleteAllById(any());
    }

    @Test
    void compressMySessionContext_should_delete_sources_only_for_assistant_with_non_null_id_even_with_null_elements() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setCompressionEnabled(true);
        cfg.setCompressionKeepLastMessages(0);
        cfg.setCompressionPerMessageSnippetChars(20);
        cfg.setCompressionMaxChars(2000);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        QaMessagesEntity aNoId = msg(null, 10L, MessageRole.ASSISTANT, "a0", base.plusSeconds(1));
        QaMessagesEntity aOk = msg(5L, 10L, MessageRole.ASSISTANT, "a1", base.plusSeconds(2));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(Arrays.asList(null, aNoId, aOk));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            e.setId(999L);
            return e;
        });

        when(qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertEquals(1, out.getCompressedDeletedCount());
        assertNotNull(out.getSummaryMessageId());

        verify(qaMessageSourcesRepository).deleteByMessageIdIn(eq(List.of(5L)));
        verify(qaMessagesRepository).deleteAllById(eq(List.of(5L)));
    }

    @Test
    void compressMySessionContext_should_use_now_when_first_toCompress_null_and_skip_deletes_when_deleteIds_empty() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setCompressionEnabled(true);
        cfg.setCompressionKeepLastMessages(0);
        cfg.setCompressionPerMessageSnippetChars(20);
        cfg.setCompressionMaxChars(2000);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        QaMessagesEntity u1 = new QaMessagesEntity();
        u1.setId(null);
        u1.setSessionId(10L);
        u1.setRole(MessageRole.USER);
        u1.setContent("q1");
        u1.setCreatedAt(base.plusSeconds(1));

        QaMessagesEntity u2 = new QaMessagesEntity();
        u2.setId(null);
        u2.setSessionId(10L);
        u2.setRole(MessageRole.USER);
        u2.setContent("q2");
        u2.setCreatedAt(base.plusSeconds(2));

        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(Arrays.asList(null, u1, u2));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            e.setId(999L);
            return e;
        });

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertEquals(999L, out.getSummaryMessageId());
        assertEquals(0, out.getCompressedDeletedCount());
        assertNotNull(out.getSummary());

        verify(qaMessagesRepository).save(argThat(m -> m.getCreatedAt() != null));
        verify(qaMessageSourcesRepository, never()).deleteByMessageIdIn(any());
        verify(qaTurnsRepository, never()).findBySessionIdOrderByCreatedAtAsc(anyLong());
        verify(qaMessagesRepository, never()).deleteAllById(any());
    }

    @Test
    void compressMySessionContext_should_skip_sources_delete_when_no_assistant_in_compressed_messages_and_delete_turns_by_aid() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setCompressionEnabled(true);
        cfg.setCompressionKeepLastMessages(2);
        cfg.setCompressionPerMessageSnippetChars(50);
        cfg.setCompressionMaxChars(2000);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        QaMessagesEntity sys1 = msg(1L, 10L, MessageRole.SYSTEM, "s1", base.plusSeconds(0));
        QaMessagesEntity sys2 = msg(2L, 10L, MessageRole.SYSTEM, "s2", base.plusSeconds(0));
        QaMessagesEntity u1 = msg(10L, 10L, MessageRole.USER, "q1", base.plusSeconds(1));
        QaMessagesEntity u2 = msg(11L, 10L, MessageRole.USER, "q2", base.plusSeconds(2));
        QaMessagesEntity a1 = msg(12L, 10L, MessageRole.ASSISTANT, "a1", base.plusSeconds(3));
        QaMessagesEntity u3 = msg(13L, 10L, MessageRole.USER, "q3", base.plusSeconds(4));

        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(sys1, sys2, u1, u2, a1, u3));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            e.setId(999L);
            return e;
        });

        QaTurnsEntity tAidMatch = new QaTurnsEntity();
        tAidMatch.setId(100L);
        tAidMatch.setSessionId(10L);
        tAidMatch.setQuestionMessageId(9999L);
        tAidMatch.setAnswerMessageId(10L);
        tAidMatch.setCreatedAt(base.plusSeconds(1));

        QaTurnsEntity tNoMatch = new QaTurnsEntity();
        tNoMatch.setId(101L);
        tNoMatch.setSessionId(10L);
        tNoMatch.setQuestionMessageId(8888L);
        tNoMatch.setAnswerMessageId(7777L);
        tNoMatch.setCreatedAt(base.plusSeconds(2));

        when(qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(Arrays.asList(null, tAidMatch, tNoMatch));

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertEquals(999L, out.getSummaryMessageId());
        assertEquals(2, out.getCompressedDeletedCount());

        verify(qaMessageSourcesRepository, never()).deleteByMessageIdIn(any());
        verify(qaTurnsRepository).delete(eq(tAidMatch));
        verify(qaTurnsRepository, never()).delete(eq(tNoMatch));
        verify(qaMessagesRepository).deleteAllById(eq(List.of(10L, 11L)));
    }

    @Test
    void compressMySessionContext_should_treat_negative_keepLast_as_zero_and_use_default_limits_when_cfg_values_null() {
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
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setCompressionEnabled(true);
        cfg.setCompressionKeepLastMessages(-1);
        cfg.setCompressionPerMessageSnippetChars(null);
        cfg.setCompressionMaxChars(null);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime base = LocalDateTime.now().minusMinutes(10);
        QaMessagesEntity u1 = msg(10L, 10L, MessageRole.USER, "q1", base.plusSeconds(1));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(u1));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            e.setId(999L);
            return e;
        });

        when(qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        QaCompressContextResultDTO out = service.compressMySessionContext(1L, 10L);
        assertEquals(0, out.getKeptLast());
        assertEquals(999L, out.getSummaryMessageId());
        assertEquals(1, out.getCompressedDeletedCount());
        assertNotNull(out.getSummary());

        verify(qaMessagesRepository).deleteAllById(eq(List.of(10L)));
    }

    private static QaMessagesEntity msg(Long id, Long sessionId, MessageRole role, String content, LocalDateTime createdAt) {
        QaMessagesEntity m = new QaMessagesEntity();
        m.setId(id);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setCreatedAt(createdAt);
        return m;
    }
}
