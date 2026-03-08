package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaMessageDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaHistoryServiceSourcesTest {

    @Test
    void getMySessionMessages_should_attach_sources_for_assistant_messages() {
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

        QaMessagesEntity u = new QaMessagesEntity();
        u.setId(101L);
        u.setSessionId(10L);
        u.setRole(MessageRole.USER);
        u.setContent("q");
        u.setCreatedAt(LocalDateTime.now().minusSeconds(2));

        QaMessagesEntity a = new QaMessagesEntity();
        a.setId(102L);
        a.setSessionId(10L);
        a.setRole(MessageRole.ASSISTANT);
        a.setContent("ans");
        a.setCreatedAt(LocalDateTime.now().minusSeconds(1));

        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(u, a));

        QaMessageSourcesEntity s1 = new QaMessageSourcesEntity();
        s1.setId(1L);
        s1.setMessageId(102L);
        s1.setSourceIndex(1);
        s1.setPostId(2001L);
        s1.setChunkIndex(3);
        s1.setScore(0.88);
        s1.setTitle("doc1");
        s1.setUrl("http://x");

        when(qaMessageSourcesRepository.findByMessageIdInOrderByMessageIdAscSourceIndexAsc(List.of(102L))).thenReturn(List.of(s1));

        List<QaMessageDTO> out = service.getMySessionMessages(1L, 10L);
        assertEquals(2, out.size());

        QaMessageDTO outUser = out.get(0);
        assertEquals(101L, outUser.getId());
        assertNull(outUser.getSources());

        QaMessageDTO outAssistant = out.get(1);
        assertEquals(102L, outAssistant.getId());
        assertNotNull(outAssistant.getSources());
        assertEquals(1, outAssistant.getSources().size());
        assertEquals(1, outAssistant.getSources().get(0).getIndex());
        assertEquals(2001L, outAssistant.getSources().get(0).getPostId());
        assertEquals(3, outAssistant.getSources().get(0).getChunkIndex());
        assertEquals("doc1", outAssistant.getSources().get(0).getTitle());
        assertEquals("http://x", outAssistant.getSources().get(0).getUrl());
    }
}
