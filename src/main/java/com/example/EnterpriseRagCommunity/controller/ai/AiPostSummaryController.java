package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostAiSummaryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostAiSummaryEntity;
import com.example.EnterpriseRagCommunity.service.ai.PostSummaryGenConfigService;
import com.example.EnterpriseRagCommunity.repository.ai.PostAiSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostSummaryController {

    private final PostSummaryGenConfigService postSummaryGenConfigService;
    private final PostAiSummaryRepository postAiSummaryRepository;

    @GetMapping("/summary/config")
    public PostSummaryGenPublicConfigDTO getSummaryConfig() {
        return postSummaryGenConfigService.getPublicConfig();
    }

    @GetMapping("/{postId}/summary")
    public PostAiSummaryDTO getPostSummary(@PathVariable("postId") Long postId) {
        PostSummaryGenPublicConfigDTO cfg = postSummaryGenConfigService.getPublicConfig();

        PostAiSummaryDTO dto = new PostAiSummaryDTO();
        dto.setPostId(postId);
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));

        if (!dto.getEnabled()) {
            dto.setStatus("DISABLED");
            return dto;
        }

        PostAiSummaryEntity s = postAiSummaryRepository.findByPostId(postId).orElse(null);
        if (s == null) {
            dto.setStatus("PENDING");
            return dto;
        }

        dto.setStatus(s.getStatus());
        if ("SUCCESS".equalsIgnoreCase(s.getStatus())) {
            dto.setSummaryTitle(s.getSummaryTitle());
            dto.setSummaryText(s.getSummaryText());
        }
        dto.setModel(s.getModel());
        dto.setGeneratedAt(s.getGeneratedAt());
        dto.setLatencyMs(s.getLatencyMs());
        dto.setErrorMessage(normalizePublicError(s.getErrorMessage()));
        return dto;
    }

    private static String normalizePublicError(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        int nl = t.indexOf('\n');
        if (nl >= 0) t = t.substring(0, nl);
        if (t.length() > 500) t = t.substring(0, 500);
        return t.trim();
    }
}

