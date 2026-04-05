package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDetailDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;

public final class LlmQueueTaskMappingSupport {

    private LlmQueueTaskMappingSupport() {
    }

    public static TaskMappedData from(LlmCallQueueService.TaskDetailSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new TaskMappedData(
                snapshot.id(),
                snapshot.seq(),
                snapshot.priority(),
                snapshot.type(),
                snapshot.label(),
                snapshot.status(),
                snapshot.providerId(),
                snapshot.model(),
                snapshot.createdAt(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.waitMs(),
                snapshot.durationMs(),
                snapshot.tokensIn(),
                snapshot.tokensOut(),
                snapshot.totalTokens(),
                snapshot.tokensPerSec(),
                snapshot.error(),
                snapshot.input(),
                snapshot.output()
        );
    }

    public static TaskMappedData from(LlmQueueTaskHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TaskMappedData(
                entity.getTaskId(),
                entity.getSeq(),
                entity.getPriority(),
                entity.getType(),
                null,
                entity.getStatus(),
                entity.getProviderId(),
                entity.getModel(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getWaitMs(),
                entity.getDurationMs(),
                entity.getTokensIn(),
                entity.getTokensOut(),
                entity.getTotalTokens(),
                entity.getTokensPerSec(),
                entity.getError(),
                entity.getInput(),
                entity.getOutput()
        );
    }

    public record TaskMappedData(
            String id,
            Long seq,
            Integer priority,
            LlmQueueTaskType type,
            String label,
            LlmQueueTaskStatus status,
            String providerId,
            String model,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime finishedAt,
            Long waitMs,
            Long durationMs,
            Integer tokensIn,
            Integer tokensOut,
            Integer totalTokens,
            Double tokensPerSec,
            String error,
            String input,
            String output
    ) {
        public void applyTo(AdminLlmQueueTaskDTO dto) {
            dto.setId(id);
            dto.setSeq(seq);
            dto.setPriority(priority);
            dto.setType(type);
            dto.setStatus(status);
            dto.setProviderId(providerId);
            dto.setModel(model);
            dto.setCreatedAt(createdAt);
            dto.setStartedAt(startedAt);
            dto.setFinishedAt(finishedAt);
            dto.setWaitMs(waitMs);
            dto.setDurationMs(durationMs);
            dto.setTokensIn(tokensIn);
            dto.setTokensOut(tokensOut);
            dto.setTotalTokens(totalTokens);
            dto.setTokensPerSec(tokensPerSec);
            dto.setError(error);
        }

        public void applyTo(AdminLlmQueueTaskDetailDTO dto) {
            dto.setId(id);
            dto.setSeq(seq);
            dto.setPriority(priority);
            dto.setType(type);
            dto.setLabel(label);
            dto.setStatus(status);
            dto.setProviderId(providerId);
            dto.setModel(model);
            dto.setCreatedAt(createdAt);
            dto.setStartedAt(startedAt);
            dto.setFinishedAt(finishedAt);
            dto.setWaitMs(waitMs);
            dto.setDurationMs(durationMs);
            dto.setTokensIn(tokensIn);
            dto.setTokensOut(tokensOut);
            dto.setTotalTokens(totalTokens);
            dto.setTokensPerSec(tokensPerSec);
            dto.setError(error);
            dto.setInput(input);
            dto.setOutput(output);
        }

        public void applyTo(LlmQueueTaskHistoryEntity entity) {
            entity.setTaskId(id);
            entity.setSeq(seq);
            entity.setPriority(priority);
            entity.setType(type);
            entity.setStatus(status);
            entity.setProviderId(providerId);
            entity.setModel(model);
            entity.setCreatedAt(createdAt);
            entity.setStartedAt(startedAt);
            entity.setFinishedAt(finishedAt);
            entity.setWaitMs(waitMs);
            entity.setDurationMs(durationMs);
            entity.setTokensIn(tokensIn);
            entity.setTokensOut(tokensOut);
            entity.setTotalTokens(totalTokens);
            entity.setTokensPerSec(tokensPerSec);
            entity.setError(error);
            entity.setInput(input);
            entity.setOutput(output);
        }
    }
}
