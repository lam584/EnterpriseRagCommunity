package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardModeratorsEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminModerationQueueControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BoardModeratorsRepository boardModeratorsRepository;

    @Autowired
    BoardsRepository boardsRepository;

    @Autowired
    UsersRepository usersRepository;

    @Autowired
    PostsRepository postsRepository;

    @Autowired
    CommentsRepository commentsRepository;

    @Autowired
    ModerationQueueRepository moderationQueueRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    AdminModerationQueueService adminModerationQueueService;

    @MockBean
    AdministratorService administratorService;

    Long board1;
    Long board2;
    Long moderatorId;
    Long normalUserId;
    String moderatorEmail = "mod@example.com";
    String normalEmail = "u@example.com";

    Long postInBoard1Id;
    Long postInBoard2Id;
    Long commentOnPost1Id;
    Long commentOnPost2Id;

    Long postQueueBoard1Id;
    Long postQueueBoard2Id;
    Long commentQueueBoard1Id;
    Long commentQueueBoard2Id;

    @BeforeEach
    void setup() {
        LocalDateTime now = LocalDateTime.now();
        board1 = boardsRepository.save(newBoardEntity("b1", now)).getId();
        board2 = boardsRepository.save(newBoardEntity("b2", now)).getId();

        UsersEntity moderator = usersRepository.save(newUserEntity(moderatorEmail, now));
        moderatorId = moderator.getId();
        UsersEntity normal = usersRepository.save(newUserEntity(normalEmail, now));
        normalUserId = normal.getId();

        Mockito.when(administratorService.findByUsername(Mockito.eq(moderatorEmail)))
                .thenReturn(Optional.of(moderator));
        Mockito.when(administratorService.findByUsername(Mockito.eq(normalEmail)))
                .thenReturn(Optional.of(normal));

        BoardModeratorsEntity m = new BoardModeratorsEntity();
        m.setBoardId(board1);
        m.setUserId(moderatorId);
        boardModeratorsRepository.save(m);

        postInBoard1Id = insertPost(board1, moderatorId, "p1", "c1", now);
        postInBoard2Id = insertPost(board2, normalUserId, "p2", "c2", now);

        CommentsEntity c1 = new CommentsEntity();
        c1.setPostId(postInBoard1Id);
        c1.setParentId(null);
        c1.setAuthorId(normalUserId);
        c1.setContent("comment1");
        c1.setStatus(CommentStatus.PENDING);
        c1.setIsDeleted(false);
        c1.setCreatedAt(now);
        c1.setUpdatedAt(now);
        c1 = commentsRepository.save(c1);
        commentOnPost1Id = c1.getId();

        CommentsEntity c2 = new CommentsEntity();
        c2.setPostId(postInBoard2Id);
        c2.setParentId(null);
        c2.setAuthorId(normalUserId);
        c2.setContent("comment2");
        c2.setStatus(CommentStatus.PENDING);
        c2.setIsDeleted(false);
        c2.setCreatedAt(now);
        c2.setUpdatedAt(now);
        c2 = commentsRepository.save(c2);
        commentOnPost2Id = c2.getId();

        postQueueBoard1Id = moderationQueueRepository.save(queue(ContentType.POST, postInBoard1Id)).getId();
        postQueueBoard2Id = moderationQueueRepository.save(queue(ContentType.POST, postInBoard2Id)).getId();
        commentQueueBoard1Id = moderationQueueRepository.save(queue(ContentType.COMMENT, commentOnPost1Id)).getId();
        commentQueueBoard2Id = moderationQueueRepository.save(queue(ContentType.COMMENT, commentOnPost2Id)).getId();

        AdminModerationQueueDetailDTO ok = new AdminModerationQueueDetailDTO();
        ok.setId(999L);
        ok.setStatus(QueueStatus.APPROVED);
        Mockito.when(adminModerationQueueService.approve(Mockito.anyLong(), Mockito.anyString())).thenReturn(ok);
    }

    @Test
    void approve_shouldDeny_forNormalUserWithoutPermissionOrModerator() throws Exception {
        mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", postQueueBoard1Id)
                        .with(user(normalEmail))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_shouldAllow_forModeratorOnOwnPostQueue() throws Exception {
        mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", postQueueBoard1Id)
                        .with(user(moderatorEmail))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void approve_shouldDeny_forModeratorOnOtherBoardPostQueue() throws Exception {
        mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", postQueueBoard2Id)
                        .with(user(moderatorEmail))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_shouldAllow_forModeratorOnOwnCommentQueue() throws Exception {
        mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", commentQueueBoard1Id)
                        .with(user(moderatorEmail))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void approve_shouldDeny_forModeratorOnOtherBoardCommentQueue() throws Exception {
        mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", commentQueueBoard2Id)
                        .with(user(moderatorEmail))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_shouldReject_blankReason() throws Exception {
        mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", postQueueBoard1Id)
                        .with(user(moderatorEmail))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    private static UsersEntity newUserEntity(String email, LocalDateTime now) {
        UsersEntity u = new UsersEntity();
        u.setEmail(email);
        u.setUsername(email);
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return u;
    }

    private static BoardsEntity newBoardEntity(String name, LocalDateTime now) {
        BoardsEntity b = new BoardsEntity();
        b.setName(name);
        b.setVisible(true);
        b.setSortOrder(0);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        return b;
    }

    private static ModerationQueueEntity queue(ContentType contentType, Long contentId) {
        LocalDateTime now = LocalDateTime.now();
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(contentType);
        q.setContentId(contentId);
        q.setStatus(QueueStatus.HUMAN);
        q.setCurrentStage(QueueStage.HUMAN);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setVersion(0);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        return q;
    }

    private Long insertPost(Long boardId, Long authorId, String title, String content, LocalDateTime now) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into posts (tenant_id, board_id, author_id, title, content, content_format, status, published_at, is_deleted, metadata, created_at, updated_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setObject(1, null);
            ps.setLong(2, boardId);
            ps.setLong(3, authorId);
            ps.setString(4, title);
            ps.setString(5, content);
            ps.setString(6, "MARKDOWN");
            ps.setString(7, "PENDING");
            ps.setObject(8, null);
            ps.setBoolean(9, false);
            ps.setObject(10, null);
            ps.setTimestamp(11, Timestamp.valueOf(now));
            ps.setTimestamp(12, Timestamp.valueOf(now));
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) throw new IllegalStateException("Failed to insert post");
        return key.longValue();
    }
}
