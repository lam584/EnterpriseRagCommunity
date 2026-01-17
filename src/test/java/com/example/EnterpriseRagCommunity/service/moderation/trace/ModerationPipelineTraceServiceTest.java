package com.example.EnterpriseRagCommunity.service.moderation.trace;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ModerationPipelineTraceServiceTest {

    @Autowired
    ModerationPipelineTraceService traceService;

    @Autowired
    ModerationPipelineRunRepository runRepository;

    @Autowired
    ModerationPipelineStepRepository stepRepository;

    @Test
    void ensureRun_and_steps_shouldWork() {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(999L);
        q.setContentType(ContentType.POST);
        q.setContentId(123L);

        ModerationPipelineRunEntity run = traceService.ensureRun(q);
        assertNotNull(run.getId());
        assertNotNull(run.getTraceId());

        ModerationPipelineStepEntity step = traceService.startStep(run.getId(), ModerationPipelineStepEntity.Stage.RULE, 1, null, Map.of("k", "v"));
        assertNotNull(step.getId());

        traceService.finishStepOk(step.getId(), "PASS", null, Map.of("hitCount", 0));
        ModerationPipelineStepEntity persisted = stepRepository.findById(step.getId()).orElseThrow();
        assertEquals("PASS", persisted.getDecision());
        assertNotNull(persisted.getEndedAt());

        traceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);
        ModerationPipelineRunEntity persistedRun = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(ModerationPipelineRunEntity.RunStatus.SUCCESS, persistedRun.getStatus());
        assertEquals(ModerationPipelineRunEntity.FinalDecision.HUMAN, persistedRun.getFinalDecision());
    }
}
