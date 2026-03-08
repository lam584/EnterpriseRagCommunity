package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.scheduling.enabled=false")
@Transactional
class ModerationRuleAutoRunnerAntiSpamTest {

    @Autowired
    private ModerationRuleAutoRunner runner;

    @Autowired
    private ModerationPolicyConfigRepository policyConfigRepository;

    @Autowired
    private ModerationQueueRepository queueRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BoardsRepository boardsRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private ModerationActionsRepository moderationActionsRepository;

    @Test
    void runForQueueId_commentAntiSpamHit_shouldRouteToHuman() {
        LocalDateTime now = LocalDateTime.now();
        UsersEntity author = createUser("antispam_comment_author@example.com", "comment-author");
        BoardsEntity board = createBoard();
        PostsEntity post = createPost(author.getId(), board.getId());

        CommentsEntity c1 = createComment(author.getId(), post.getId(), "spam-1", now.minusSeconds(20));
        createComment(author.getId(), post.getId(), "spam-2", now.minusSeconds(15));
        createComment(author.getId(), post.getId(), "spam-3", now.minusSeconds(10));

        upsertPolicyConfig(ContentType.COMMENT, Map.of(
                "precheck", Map.of("rule", Map.of("enabled", true)),
                "anti_spam", Map.of(
                        "comment", Map.of(
                                "window_seconds", 60,
                                "max_per_author_per_window", 2
                        )
                )
        ));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.COMMENT);
        q.setContentId(c1.getId());
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        q.setPriority(0);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = queueRepository.save(q);

        runner.runForQueueId(q.getId());

        ModerationQueueEntity after = queueRepository.findById(q.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(QueueStatus.HUMAN);
        assertThat(after.getCurrentStage()).isEqualTo(QueueStage.HUMAN);
    }

    @Test
    void runForQueueId_profileAntiSpamHit_shouldRouteToHuman() {
        LocalDateTime now = LocalDateTime.now();
        UsersEntity user = createUser("antispam_profile_author@example.com", "profile-author");

        Map<String, Object> profilePending = new LinkedHashMap<>();
        profilePending.put("username", "profile-author");
        profilePending.put("bio", "updated bio");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profilePending", profilePending);
        user.setMetadata(metadata);
        user = usersRepository.save(user);

        upsertPolicyConfig(ContentType.PROFILE, Map.of(
                "precheck", Map.of("rule", Map.of("enabled", true)),
                "anti_spam", Map.of(
                        "profile", Map.of(
                                "window_minutes", 60,
                                "max_updates_per_window", 1,
                                "max_updates_per_day", 99
                        )
                )
        ));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.PROFILE);
        q.setContentId(user.getId());
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        q.setPriority(0);
        q.setCreatedAt(now.minusMinutes(30));
        q.setUpdatedAt(now.minusMinutes(30));
        q = queueRepository.save(q);

        ModerationActionsEntity snap1 = new ModerationActionsEntity();
        snap1.setQueueId(q.getId());
        snap1.setActorUserId(user.getId());
        snap1.setAction(ActionType.NOTE);
        snap1.setReason("PROFILE_PENDING_SNAPSHOT");
        snap1.setSnapshot(Map.of("pending_profile", Map.of("bio", "old")));
        snap1.setCreatedAt(now.minusMinutes(10));
        moderationActionsRepository.save(snap1);

        ModerationActionsEntity snap2 = new ModerationActionsEntity();
        snap2.setQueueId(q.getId());
        snap2.setActorUserId(user.getId());
        snap2.setAction(ActionType.NOTE);
        snap2.setReason("PROFILE_PENDING_SNAPSHOT");
        snap2.setSnapshot(Map.of("pending_profile", Map.of("bio", "new")));
        snap2.setCreatedAt(now.minusMinutes(5));
        moderationActionsRepository.save(snap2);

        runner.runForQueueId(q.getId());

        ModerationQueueEntity after = queueRepository.findById(q.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(QueueStatus.HUMAN);
        assertThat(after.getCurrentStage()).isEqualTo(QueueStage.HUMAN);
    }

    @Test
    void runForQueueId_profileDailyAntiSpamHit_shouldRouteToHuman() {
        LocalDateTime now = LocalDateTime.now();
        UsersEntity user = createUser("antispam_profile_day_author@example.com", "profile-day-author");

        Map<String, Object> profilePending = new LinkedHashMap<>();
        profilePending.put("username", "profile-day-author");
        profilePending.put("bio", "updated bio day");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profilePending", profilePending);
        user.setMetadata(metadata);
        user = usersRepository.save(user);

        upsertPolicyConfig(ContentType.PROFILE, Map.of(
                "precheck", Map.of("rule", Map.of("enabled", true)),
                "anti_spam", Map.of(
                        "profile", Map.of(
                                "window_minutes", 60,
                                "max_updates_per_window", 99,
                                "max_updates_per_day", 1
                        )
                )
        ));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.PROFILE);
        q.setContentId(user.getId());
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        q.setPriority(0);
        q.setCreatedAt(now.minusMinutes(30));
        q.setUpdatedAt(now.minusMinutes(30));
        q = queueRepository.save(q);

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        ModerationActionsEntity snap1 = new ModerationActionsEntity();
        snap1.setQueueId(q.getId());
        snap1.setActorUserId(user.getId());
        snap1.setAction(ActionType.NOTE);
        snap1.setReason("PROFILE_PENDING_SNAPSHOT");
        snap1.setSnapshot(Map.of("pending_profile", Map.of("bio", "old day")));
        snap1.setCreatedAt(dayStart.plusHours(1));
        moderationActionsRepository.save(snap1);

        ModerationActionsEntity snap2 = new ModerationActionsEntity();
        snap2.setQueueId(q.getId());
        snap2.setActorUserId(user.getId());
        snap2.setAction(ActionType.NOTE);
        snap2.setReason("PROFILE_PENDING_SNAPSHOT");
        snap2.setSnapshot(Map.of("pending_profile", Map.of("bio", "new day")));
        snap2.setCreatedAt(now.minusMinutes(1));
        moderationActionsRepository.save(snap2);

        runner.runForQueueId(q.getId());

        ModerationQueueEntity after = queueRepository.findById(q.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(QueueStatus.HUMAN);
        assertThat(after.getCurrentStage()).isEqualTo(QueueStage.HUMAN);
    }

    private UsersEntity createUser(String email, String username) {
        UsersEntity u = new UsersEntity();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        u.setMetadata(new LinkedHashMap<>());
        return usersRepository.save(u);
    }

    private BoardsEntity createBoard() {
        BoardsEntity b = new BoardsEntity();
        b.setName("anti-spam-board-" + System.nanoTime());
        b.setDescription("test");
        b.setVisible(true);
        b.setSortOrder(0);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        return boardsRepository.save(b);
    }

    private PostsEntity createPost(Long authorId, Long boardId) {
        PostsEntity p = new PostsEntity();
        p.setBoardId(boardId);
        p.setAuthorId(authorId);
        p.setTitle("anti-spam post");
        p.setContent("content");
        p.setContentLength(7);
        p.setIsChunkedReview(false);
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setIsDeleted(false);
        return postsRepository.save(p);
    }

    private CommentsEntity createComment(Long authorId, Long postId, String content, LocalDateTime createdAt) {
        CommentsEntity c = new CommentsEntity();
        c.setAuthorId(authorId);
        c.setPostId(postId);
        c.setParentId(null);
        c.setContent(content);
        c.setStatus(CommentStatus.VISIBLE);
        c.setIsDeleted(false);
        c.setCreatedAt(createdAt);
        c.setUpdatedAt(createdAt);
        return commentsRepository.save(c);
    }

    private void upsertPolicyConfig(ContentType contentType, Map<String, Object> config) {
        ModerationPolicyConfigEntity cfg = policyConfigRepository.findByContentType(contentType)
                .orElseGet(ModerationPolicyConfigEntity::new);
        cfg.setContentType(contentType);
        cfg.setPolicyVersion("test-antispam-v1");
        cfg.setConfig(new LinkedHashMap<>(config));
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(null);
        policyConfigRepository.save(cfg);
    }
}
