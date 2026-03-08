package com.example.EnterpriseRagCommunity.service.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventDetailDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventLogDTO;
import com.example.EnterpriseRagCommunity.entity.ai.AiChatContextEventsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.AiChatContextEventsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatContextEventLogsService {
    private final AiChatContextEventsRepository eventsRepository;

    @Transactional(readOnly = true)
    public Page<AdminChatContextEventLogDTO> list(LocalDateTime from, LocalDateTime to, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        var pageable = PageRequest.of(safePage, safeSize);

        Page<AiChatContextEventsEntity> p;
        if (from != null && to != null) {
            p = eventsRepository.findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(from, to, pageable);
        } else if (from != null) {
            p = eventsRepository.findByCreatedAtAfterOrderByCreatedAtDescIdDesc(from, pageable);
        } else if (to != null) {
            p = eventsRepository.findByCreatedAtBeforeOrderByCreatedAtDescIdDesc(to, pageable);
        } else {
            p = eventsRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
        }

        return p.map(ChatContextEventLogsService::toLogDTO);
    }

    @Transactional(readOnly = true)
    public AdminChatContextEventDetailDTO get(long id) {
        AiChatContextEventsEntity e = eventsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found"));
        return toDetailDTO(e);
    }

    private static AdminChatContextEventLogDTO toLogDTO(AiChatContextEventsEntity e) {
        AdminChatContextEventLogDTO dto = new AdminChatContextEventLogDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setSessionId(e.getSessionId());
        dto.setQuestionMessageId(e.getQuestionMessageId());
        dto.setKind(e.getKind());
        dto.setReason(e.getReason());
        dto.setBeforeTokens(e.getBeforeTokens());
        dto.setAfterTokens(e.getAfterTokens());
        dto.setBeforeChars(e.getBeforeChars());
        dto.setAfterChars(e.getAfterChars());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private static AdminChatContextEventDetailDTO toDetailDTO(AiChatContextEventsEntity e) {
        AdminChatContextEventDetailDTO dto = new AdminChatContextEventDetailDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setSessionId(e.getSessionId());
        dto.setQuestionMessageId(e.getQuestionMessageId());
        dto.setKind(e.getKind());
        dto.setReason(e.getReason());
        dto.setTargetPromptTokens(e.getTargetPromptTokens());
        dto.setReserveAnswerTokens(e.getReserveAnswerTokens());
        dto.setBeforeTokens(e.getBeforeTokens());
        dto.setAfterTokens(e.getAfterTokens());
        dto.setBeforeChars(e.getBeforeChars());
        dto.setAfterChars(e.getAfterChars());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setDetailJson(e.getDetailJson());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}
