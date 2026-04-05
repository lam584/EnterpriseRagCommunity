package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.entity.semantic.GenerationJobsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class AiSuggestionSupport {

    private AiSuggestionSupport() {
    }

    static String buildExcerpt(String content, int maxChars) {
        if (content == null) return null;
        String text = content.trim();
        if (text.isEmpty()) return null;
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars);
        }
        return text;
    }

    static List<String> cleanNonBlankStrings(Collection<String> values) {
        List<String> cleaned = new ArrayList<>();
        if (values == null) {
            return cleaned;
        }
        for (String value : values) {
            if (value == null) continue;
            String text = value.trim();
            if (!text.isBlank()) {
                cleaned.add(text);
            }
        }
        return cleaned;
    }

    static void applyCommonHistoryFields(PostSuggestionGenHistoryEntity history,
                                         int requestedCount,
                                         int maxChars,
                                         int contentLen,
                                         String content,
                                         List<String> output,
                                         Long jobId) {
        history.setRequestedCount(requestedCount);
        history.setAppliedMaxContentChars(maxChars);
        history.setContentLen(contentLen);
        history.setContentExcerpt(buildExcerpt(content, 240));
        history.setOutputJson(output == null ? List.of() : new ArrayList<>(output));
        history.setJobId(jobId);
    }

    static GenerationJobsEntity buildSucceededSuggestionJob(String promptCode,
                                                            String usedModel,
                                                            String usedProviderId,
                                                            Double temperature,
                                                            Double topP,
                                                            Long latency,
                                                            Integer promptVersion) {
        GenerationJobsEntity job = new GenerationJobsEntity();
        job.setJobType(GenerationJobType.SUGGESTION);
        job.setTargetType(GenerationTargetType.POST);
        job.setTargetId(0L);
        job.setStatus(GenerationJobStatus.SUCCEEDED);
        job.setPromptCode(promptCode);
        job.setModel(usedModel);
        job.setProviderId(usedProviderId);
        job.setTemperature(temperature);
        job.setTopP(topP);
        job.setLatencyMs(latency);
        job.setPromptVersion(promptVersion);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(job.getCreatedAt());
        return job;
    }

    static PostSuggestionGenHistoryEntity newSuggestionHistory(SuggestionKind kind, Long actorUserId) {
        PostSuggestionGenHistoryEntity history = new PostSuggestionGenHistoryEntity();
        history.setKind(kind);
        history.setUserId(actorUserId);
        history.setCreatedAt(LocalDateTime.now());
        return history;
    }
}
