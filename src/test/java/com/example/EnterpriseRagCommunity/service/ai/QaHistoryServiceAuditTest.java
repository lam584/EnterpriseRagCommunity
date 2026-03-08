package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaSessionUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
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

public class QaHistoryServiceAuditTest {

    @Test
    void updateSessionWritesAuditLog() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        when(administratorService.findById(10L)).thenReturn(Optional.of(u));

        QaSessionsEntity s = new QaSessionsEntity();
        s.setId(200L);
        s.setUserId(10L);
        s.setTitle("t");
        s.setIsActive(true);
        when(qaSessionsRepository.findByIdAndUserId(200L, 10L)).thenReturn(Optional.of(s));
        when(qaSessionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        QaHistoryService svc = new QaHistoryService(
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

        svc.updateMySession(10L, 200L, req);

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("QA_SESSION_UPDATE"),
                eq("QA_SESSION"),
                eq(200L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}

