package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
import com.example.EnterpriseRagCommunity.testsupport.MySqlTestcontainersBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ModerationPrecheckRejectIntegrationTest extends MySqlTestcontainersBase {

    @Autowired
    private ModerationRuleAutoRunner ruleRunner;

    @Autowired
    private ModerationVecAutoRunner vecRunner;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ModerationQueueRepository queueRepository;

    @Autowired
    private ModerationRulesRepository rulesRepository;

    @Autowired
    private ModerationPolicyConfigRepository policyConfigRepository;

        @Autowired
        private BoardsRepository boardsRepository;

        @Autowired
        private PostsRepository postsRepository;

        @Autowired
        private CommentsRepository commentsRepository;

        @Autowired
        private ModerationPipelineStepRepository pipelineStepRepository;

    @Test
    void ruleActionReject_shouldEndWithRejectedStatus() {
        String marker = "rule-reject-" + UUID.randomUUID().toString().replace("-", "");
        UsersEntity user = createUser(marker);

        upsertProfilePolicy(Map.of(
                "precheck", Map.of(
                        "rule", Map.of(
                                "enabled", true,
                                "high_action", "REJECT",
                                "medium_action", "REJECT",
                                "low_action", "REJECT"
                        ),
                        "vec", Map.of(
                                "enabled", true,
                                "threshold", 0.2,
                                "hit_action", "LLM",
                                "miss_action", "LLM"
                        )
                )
        ));

        ModerationRulesEntity rule = new ModerationRulesEntity();
        rule.setName("it-rule-" + marker);
        rule.setType(RuleType.REGEX);
        rule.setPattern(marker);
        rule.setSeverity(Severity.HIGH);
        rule.setEnabled(true);
        rule.setMetadata(Map.of());
        rule.setCreatedAt(LocalDateTime.now());
        rulesRepository.save(rule);

        ModerationQueueEntity queue = createQueue(user.getId(), QueueStage.RULE);

        ruleRunner.runForQueueId(queue.getId());

        ModerationQueueEntity after = queueRepository.findById(queue.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(QueueStatus.REJECTED);
    }

    @Test
    void vecMissActionReject_shouldEndWithRejectedStatus() {
        String marker = "vec-reject-" + UUID.randomUUID().toString().replace("-", "");
        UsersEntity user = createUser(marker);

        upsertProfilePolicy(Map.of(
                "precheck", Map.of(
                        "rule", Map.of(
                                "enabled", false,
                                "high_action", "LLM",
                                "medium_action", "LLM",
                                "low_action", "LLM"
                        ),
                        "vec", Map.of(
                                "enabled", false,
                                "threshold", 0.2,
                                "hit_action", "LLM",
                                "miss_action", "REJECT"
                        )
                )
        ));

        ModerationQueueEntity queue = createQueue(user.getId(), QueueStage.VEC);

        vecRunner.runForQueueId(queue.getId());

        ModerationQueueEntity after = queueRepository.findById(queue.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(QueueStatus.REJECTED);
    }

    @Test
    void vecEmptyTextMissActionReject_shouldEndWithRejectedStatus() {
        String marker = "vec-empty-reject-" + UUID.randomUUID().toString().replace("-", "");
        UsersEntity user = createUser(marker);
        CommentsEntity comment = createWhitespaceComment(user.getId());

        upsertPolicy(ContentType.COMMENT, Map.of(
                "precheck", Map.of(
                        "rule", Map.of(
                                "enabled", false,
                                "high_action", "LLM",
                                "medium_action", "LLM",
                                "low_action", "LLM"
                        ),
                        "vec", Map.of(
                                "enabled", true,
                                "threshold", 0.2,
                                "hit_action", "LLM",
                                "miss_action", "REJECT"
                        )
                )
        ));

        ModerationQueueEntity queue = createQueue(ContentType.COMMENT, comment.getId(), QueueStage.VEC);

        vecRunner.runForQueueId(queue.getId());

        ModerationQueueEntity after = queueRepository.findById(queue.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(QueueStatus.REJECTED);

        boolean hasEmptyTextVecStep = pipelineStepRepository.findAll().stream()
                .filter(step -> step.getStage() == ModerationPipelineStepEntity.Stage.VEC)
                .map(ModerationPipelineStepEntity::getDetailsJson)
                .filter(Objects::nonNull)
                .anyMatch(details -> "empty text".equals(String.valueOf(details.get("reason")))
                        && "REJECT".equals(String.valueOf(details.get("action"))));
        assertThat(hasEmptyTextVecStep).isTrue();
    }

    private UsersEntity createUser(String marker) {
        UsersEntity user = new UsersEntity();
        user.setEmail(marker + "@example.com");
        user.setUsername(marker);
        user.setPasswordHash("x");
        user.setStatus(AccountStatus.ACTIVE);
        user.setIsDeleted(false);
        user.setAccessVersion(0L);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setMetadata(new LinkedHashMap<>());
        return usersRepository.save(user);
    }

        private CommentsEntity createWhitespaceComment(Long userId) {
                BoardsEntity board = new BoardsEntity();
                board.setName("it-board-" + UUID.randomUUID().toString().replace("-", ""));
                board.setDescription("it");
                board.setVisible(true);
                board.setSortOrder(0);
                board.setCreatedAt(LocalDateTime.now());
                board.setUpdatedAt(LocalDateTime.now());
                board = boardsRepository.save(board);

                PostsEntity post = new PostsEntity();
                post.setBoardId(board.getId());
                post.setAuthorId(userId);
                post.setTitle("t");
                post.setContent("x");
                post.setContentLength(1);
                post.setIsChunkedReview(false);
                post.setContentFormat(ContentFormat.PLAIN);
                post.setStatus(PostStatus.PENDING);
                post.setIsDeleted(false);
                post.setMetadata(new LinkedHashMap<>());
                post = postsRepository.save(post);

                CommentsEntity comment = new CommentsEntity();
                comment.setPostId(post.getId());
                comment.setAuthorId(userId);
                comment.setContent("   ");
                comment.setStatus(CommentStatus.PENDING);
                comment.setIsDeleted(false);
                comment.setMetadata(new LinkedHashMap<>());
                comment.setCreatedAt(LocalDateTime.now());
                comment.setUpdatedAt(LocalDateTime.now());
                return commentsRepository.save(comment);
        }

        private ModerationQueueEntity createQueue(Long contentId, QueueStage stage) {
                return createQueue(ContentType.PROFILE, contentId, stage);
        }

        private ModerationQueueEntity createQueue(ContentType contentType, Long contentId, QueueStage stage) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
                q.setContentType(contentType);
        q.setContentId(contentId);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(stage);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        return queueRepository.save(q);
    }

    private void upsertProfilePolicy(Map<String, Object> config) {
                upsertPolicy(ContentType.PROFILE, config);
        }

        private void upsertPolicy(ContentType contentType, Map<String, Object> config) {
                ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(contentType)
                .orElseGet(ModerationPolicyConfigEntity::new);
                policy.setContentType(contentType);
        policy.setPolicyVersion("it-precheck-reject");
        policy.setConfig(new LinkedHashMap<>(config));
        policy.setUpdatedAt(LocalDateTime.now());
        policy.setUpdatedBy(null);
        policyConfigRepository.save(policy);
    }
}
