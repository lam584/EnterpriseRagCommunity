package com.example.EnterpriseRagCommunity.service.moderation.trace;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunHistoryItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunHistoryPageDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminModerationPipelineTraceService {

    private final ModerationPipelineRunRepository runRepository;
    private final ModerationPipelineStepRepository stepRepository;

    @Transactional(readOnly = true)
    public AdminModerationPipelineRunDetailDTO getLatestByQueueId(Long queueId) {
        if (queueId == null) throw new IllegalArgumentException("queueId is null");
        ModerationPipelineRunEntity run = runRepository.findFirstByQueueIdOrderByCreatedAtDesc(queueId)
                .orElse(null);
        if (run == null) return new AdminModerationPipelineRunDetailDTO(null, List.of());
        List<ModerationPipelineStepEntity> steps = stepRepository.findAllByRunIdOrderByStepOrderAsc(run.getId());
        return ModerationPipelineResultAssembler.toDetail(run, steps);
    }

    @Transactional(readOnly = true)
    public AdminModerationPipelineRunDetailDTO getByRunId(Long runId) {
        if (runId == null) throw new IllegalArgumentException("runId is null");
        ModerationPipelineRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("pipeline run not found: " + runId));
        List<ModerationPipelineStepEntity> steps = stepRepository.findAllByRunIdOrderByStepOrderAsc(runId);
        return ModerationPipelineResultAssembler.toDetail(run, steps);
    }

    @Transactional(readOnly = true)
    public AdminModerationPipelineRunHistoryPageDTO history(Long queueId,
                                                           ContentType contentType,
                                                           Long contentId,
                                                           Integer page,
                                                           Integer pageSize) {
        // validate: allow either queueId OR (contentType+contentId)
        boolean hasQueue = queueId != null;
        boolean hasContent = contentType != null && contentId != null;

        int p = page == null ? 1 : Math.max(1, page);
        int ps = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 200);

        // Always return newest first
        Pageable pageable = PageRequest.of(p - 1, ps, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ModerationPipelineRunEntity> res;
        if (hasQueue) {
            res = runRepository.findAllByQueueIdOrderByCreatedAtDesc(queueId, pageable);
        } else if (hasContent) {
            res = runRepository.findAllByContentTypeAndContentIdOrderByCreatedAtDesc(contentType, contentId, pageable);
        } else {
            // fallback: latest runs globally (useful for admin dashboards)
            res = runRepository.findAll(pageable);
        }

        List<AdminModerationPipelineRunHistoryItemDTO> items = res.getContent()
                .stream()
                .map(AdminModerationPipelineRunHistoryItemDTO::fromEntity)
                .toList();

        return new AdminModerationPipelineRunHistoryPageDTO(
                items,
                res.getTotalPages(),
                res.getTotalElements(),
                p,
                ps
        );
    }
}
