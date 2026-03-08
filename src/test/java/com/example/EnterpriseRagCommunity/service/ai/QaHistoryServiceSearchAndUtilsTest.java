package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.dto.ai.QaSearchHitDTO;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class QaHistoryServiceSearchAndUtilsTest {

    @Test
    void toBooleanModeQuery_should_handle_blank_and_special_chars() {
        assertEquals("", QaHistoryService.toBooleanModeQuery("   "));
        assertEquals("+hello*", QaHistoryService.toBooleanModeQuery("hello"));
        assertEquals("+hello* +world*", QaHistoryService.toBooleanModeQuery(" hello   world "));
        assertEquals("+hello* +world*", QaHistoryService.toBooleanModeQuery(" +hello  world*  @@@ "));
    }

    @Test
    void snippet_should_handle_null_whitespace_and_truncation() {
        assertEquals("", QaHistoryService.snippet(null, 10));
        assertEquals("a b c", QaHistoryService.snippet("  a \n b\tc  ", 10));
        assertEquals("0123…", QaHistoryService.snippet("  0123456789  ", 4));
    }

    @Test
    void searchMyHistory_should_return_empty_when_query_blank() {
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

        assertTrue(service.searchMyHistory(1L, "   ", 0, 10).isEmpty());
        assertTrue(service.searchMyHistory(1L, null, 0, 10).isEmpty());
    }

    @Test
    void searchMyHistory_should_sort_null_createdAt_last_and_page_safely() {
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

        QaSessionsEntity s1 = new QaSessionsEntity();
        s1.setId(10L);
        s1.setTitle("t1");
        s1.setCreatedAt(null);

        QaMessagesEntity m1 = new QaMessagesEntity();
        m1.setId(20L);
        m1.setSessionId(10L);
        m1.setContent("  hello \n world  ");
        LocalDateTime now = LocalDateTime.now();
        m1.setCreatedAt(now);

        QaMessagesEntity m2 = new QaMessagesEntity();
        m2.setId(21L);
        m2.setSessionId(10L);
        m2.setContent("x");
        m2.setCreatedAt(null);

        PageRequest pr = PageRequest.of(0, 2);
        when(qaSessionsRepository.searchByTitleFulltext(eq(1L), eq("+k*"), any())).thenReturn(new PageImpl<>(List.of(s1), pr, 1));
        when(qaMessagesRepository.searchMyMessagesFulltext(eq(1L), eq("+k*"), any())).thenReturn(new PageImpl<>(List.of(m1, m2), pr, 2));

        Page<QaSearchHitDTO> page0 = service.searchMyHistory(1L, "k", 0, 2);
        assertEquals(2, page0.getContent().size());
        assertNotNull(page0.getContent().get(0));
        assertEquals(now, page0.getContent().get(0).getCreatedAt());

        Page<QaSearchHitDTO> page1 = service.searchMyHistory(1L, "k", 1, 2);
        assertEquals(1, page1.getContent().size());

        Page<QaSearchHitDTO> pageOut = service.searchMyHistory(1L, "k", 10, 2);
        assertTrue(pageOut.getContent().isEmpty());

        verify(qaSessionsRepository, times(3)).searchByTitleFulltext(eq(1L), eq("+k*"), any());
        verify(qaMessagesRepository, times(3)).searchMyMessagesFulltext(eq(1L), eq("+k*"), any());
    }

    @Test
    void searchMyHistory_should_sort_when_b_createdAt_null() {
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

        QaSessionsEntity s1 = new QaSessionsEntity();
        s1.setId(10L);
        s1.setTitle("t1");
        s1.setCreatedAt(null);

        QaMessagesEntity m1 = new QaMessagesEntity();
        m1.setId(20L);
        m1.setSessionId(10L);
        m1.setContent("x");
        LocalDateTime now = LocalDateTime.now();
        m1.setCreatedAt(now);

        PageRequest pr = PageRequest.of(0, 10);
        when(qaSessionsRepository.searchByTitleFulltext(eq(1L), eq("+k*"), any())).thenReturn(new PageImpl<>(List.of(s1), pr, 1));
        when(qaMessagesRepository.searchMyMessagesFulltext(eq(1L), eq("+k*"), any())).thenReturn(new PageImpl<>(List.of(m1), pr, 1));

        Page<QaSearchHitDTO> out = service.searchMyHistory(1L, "k", 0, 10);
        assertEquals(2, out.getContent().size());
        assertEquals(now, out.getContent().get(0).getCreatedAt());
    }

    @Test
    void searchMyHistory_should_handle_both_createdAt_null() {
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

        QaSessionsEntity s1 = new QaSessionsEntity();
        s1.setId(10L);
        s1.setTitle("t1");
        s1.setCreatedAt(null);

        QaMessagesEntity m1 = new QaMessagesEntity();
        m1.setId(20L);
        m1.setSessionId(10L);
        m1.setContent("x");
        m1.setCreatedAt(null);

        PageRequest pr = PageRequest.of(0, 10);
        when(qaSessionsRepository.searchByTitleFulltext(eq(1L), eq("+k*"), any())).thenReturn(new PageImpl<>(List.of(s1), pr, 1));
        when(qaMessagesRepository.searchMyMessagesFulltext(eq(1L), eq("+k*"), any())).thenReturn(new PageImpl<>(List.of(m1), pr, 1));

        Page<QaSearchHitDTO> out = service.searchMyHistory(1L, "k", 0, 10);
        assertEquals(2, out.getContent().size());
        assertEquals("SESSION_TITLE", out.getContent().get(0).getType());
        assertEquals("MESSAGE", out.getContent().get(1).getType());
        assertEquals(2, out.getTotalElements());
    }
}
