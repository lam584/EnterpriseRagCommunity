package com.example.EnterpriseRagCommunity.service.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventDetailDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventLogDTO;
import com.example.EnterpriseRagCommunity.entity.ai.AiChatContextEventsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.AiChatContextEventsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatContextEventLogsServiceTest {

    @Test
    void list_withFromAndTo_usesBetweenAndNormalizesPageSize() {
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventLogsService service = new ChatContextEventLogsService(repository);
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 2, 0, 0);
        AiChatContextEventsEntity entity = buildEntity(11L);
        Page<AiChatContextEventsEntity> page = new PageImpl<>(java.util.List.of(entity));
        when(repository.findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(eq(from), eq(to), any(Pageable.class))).thenReturn(page);

        Page<AdminChatContextEventLogDTO> result = service.list(from, to, -5, 1000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(eq(from), eq(to), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(200, pageable.getPageSize());
        assertEquals(1, result.getTotalElements());
        AdminChatContextEventLogDTO dto = result.getContent().get(0);
        assertEquals(11L, dto.getId());
        assertEquals(101L, dto.getUserId());
        assertEquals(5L, dto.getSessionId());
        assertEquals(7L, dto.getQuestionMessageId());
        assertEquals("CHAT", dto.getKind());
        assertEquals("clip", dto.getReason());
        assertEquals(120, dto.getBeforeTokens());
        assertEquals(80, dto.getAfterTokens());
    }

    @Test
    void list_withOnlyFrom_usesAfterAndClampsSizeToOne() {
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventLogsService service = new ChatContextEventLogsService(repository);
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        when(repository.findByCreatedAtAfterOrderByCreatedAtDescIdDesc(eq(from), any(Pageable.class))).thenReturn(Page.empty());

        service.list(from, null, 2, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByCreatedAtAfterOrderByCreatedAtDescIdDesc(eq(from), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(1, pageable.getPageSize());
    }

    @Test
    void list_withOnlyTo_usesBefore() {
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventLogsService service = new ChatContextEventLogsService(repository);
        LocalDateTime to = LocalDateTime.of(2026, 3, 2, 0, 0);
        when(repository.findByCreatedAtBeforeOrderByCreatedAtDescIdDesc(eq(to), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, to, 0, 20);

        verify(repository).findByCreatedAtBeforeOrderByCreatedAtDescIdDesc(eq(to), any(Pageable.class));
    }

    @Test
    void list_withoutRange_usesFindAll() {
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventLogsService service = new ChatContextEventLogsService(repository);
        when(repository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, 1, 10);

        verify(repository).findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class));
    }

    @Test
    void get_whenFound_mapsAllFields() {
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventLogsService service = new ChatContextEventLogsService(repository);
        AiChatContextEventsEntity entity = buildEntity(15L);
        entity.setTargetPromptTokens(400);
        entity.setReserveAnswerTokens(150);
        entity.setDetailJson(Map.of("k", "v"));
        when(repository.findById(15L)).thenReturn(Optional.of(entity));

        AdminChatContextEventDetailDTO dto = service.get(15L);

        assertNotNull(dto);
        assertEquals(15L, dto.getId());
        assertEquals(101L, dto.getUserId());
        assertEquals(400, dto.getTargetPromptTokens());
        assertEquals(150, dto.getReserveAnswerTokens());
        assertEquals("CHAT", dto.getKind());
        assertEquals("clip", dto.getReason());
        assertEquals("v", dto.getDetailJson().get("k"));
    }

    @Test
    void get_whenMissing_throwsIllegalArgumentException() {
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventLogsService service = new ChatContextEventLogsService(repository);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.get(99L));

        assertEquals("not found", ex.getMessage());
    }

    private static AiChatContextEventsEntity buildEntity(long id) {
        AiChatContextEventsEntity entity = new AiChatContextEventsEntity();
        entity.setId(id);
        entity.setUserId(101L);
        entity.setSessionId(5L);
        entity.setQuestionMessageId(7L);
        entity.setKind("CHAT");
        entity.setReason("clip");
        entity.setBeforeTokens(120);
        entity.setAfterTokens(80);
        entity.setBeforeChars(1000);
        entity.setAfterChars(700);
        entity.setLatencyMs(88);
        entity.setCreatedAt(LocalDateTime.of(2026, 3, 1, 12, 30));
        return entity;
    }
}
