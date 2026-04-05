package com.example.EnterpriseRagCommunity.service.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.repository.ai.AiChatContextEventsRepository;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatContextEventsRetentionJob {
    private final ChatContextGovernanceConfigService configService;
    private final AiChatContextEventsRepository eventsRepository;

    @Scheduled(cron = "${ai.chat.context.logs.retention.cron:0 12 * * * *}")
    @Transactional
    public void run() {
        ChatContextGovernanceConfigDTO cfg = configService.getConfigOrDefault();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getLogEnabled())) return;
        int keepDays = cfg.getLogMaxDays() == null ? 30 : Math.clamp(cfg.getLogMaxDays(), 1, 3650);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        eventsRepository.deleteByCreatedAtBefore(cutoff);
    }
}
