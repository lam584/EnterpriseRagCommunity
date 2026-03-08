package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class ModerationRuleAutoRunnerUnlockTest {

    @Autowired
    private ModerationRuleAutoRunner runner;

    @Autowired
    private ModerationQueueRepository queueRepository;

    @Test
    void runOnce_shouldUnlockQueueAfterRuleStage() {
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = queueRepository.save(q);

        runner.runOnce();

        ModerationQueueEntity after = queueRepository.findById(q.getId()).orElseThrow();
        assertThat(after.getLockedBy()).isNull();
        assertThat(after.getLockedAt()).isNull();
    }
}

