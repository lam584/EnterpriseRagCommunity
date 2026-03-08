package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QaMessageServiceAuditTest {

    @Test
    void updateMessageWritesAuditLog() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        when(administratorService.findById(10L)).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(300L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.USER);
        msg.setContent("c");
        when(qaMessagesRepository.findById(300L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(10L);
        session.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(200L, 10L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QaMessageService svc = new QaMessageService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        svc.updateMyMessage(10L, 300L, "c2");

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("QA_MESSAGE_UPDATE"),
                eq("QA_MESSAGE"),
                eq(300L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void toggleFavoriteWritesAuditLog() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        when(administratorService.findById(10L)).thenReturn(Optional.of(u));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(301L);
        msg.setSessionId(200L);
        msg.setRole(MessageRole.ASSISTANT);
        msg.setContent("a");
        msg.setIsFavorite(false);
        when(qaMessagesRepository.findById(301L)).thenReturn(Optional.of(msg));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(200L);
        session.setUserId(10L);
        session.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(200L, 10L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QaMessageService svc = new QaMessageService(
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                auditLogWriter,
                auditDiffBuilder,
                administratorService
        );

        svc.toggleMyMessageFavorite(10L, 301L);

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("QA_MESSAGE_FAVORITE_TOGGLE"),
                eq("QA_MESSAGE"),
                eq(301L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}

