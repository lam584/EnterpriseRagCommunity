package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminModerationQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BoardsRepository boardsRepository;

    private UsersEntity ensureUser(String username) {
        return usersRepository.findByEmailAndIsDeletedFalse(username).orElseGet(() -> {
            UsersEntity u = new UsersEntity();
            u.setTenantId(null);
            u.setEmail(username);
            u.setUsername(username);
            u.setPasswordHash("x");
            u.setStatus(AccountStatus.ACTIVE);
            u.setIsDeleted(false);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return usersRepository.save(u);
        });
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void reject_shouldReturn409_whenAlreadyApproved() throws Exception {
        ensureUser("u");
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.APPROVED);
        q.setCurrentStage(QueueStage.HUMAN);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setFinishedAt(now);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = moderationQueueRepository.save(q);

        String body = mockMvc.perform(post("/api/admin/moderation/queue/{id}/reject", q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("不能直接驳回");
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void approve_shouldReturn409_whenAlreadyRejected() throws Exception {
        ensureUser("u");
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.REJECTED);
        q.setCurrentStage(QueueStage.HUMAN);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setFinishedAt(now);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = moderationQueueRepository.save(q);

        String body = mockMvc.perform(post("/api/admin/moderation/queue/{id}/approve", q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("不能直接通过");
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void toHuman_shouldMoveToHuman_forTerminalTask() throws Exception {
        ensureUser("u");
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.APPROVED);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setFinishedAt(now);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = moderationQueueRepository.save(q);

        String body = mockMvc.perform(post("/api/admin/moderation/queue/{id}/to-human", q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"status\":\"HUMAN\"");
        assertThat(body).contains("\"currentStage\":\"HUMAN\"");
        assertThat(body).contains("\"assignedToId\":null");
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void toHuman_thenReject_shouldReachRejected() throws Exception {
        UsersEntity u = ensureUser("u");
        LocalDateTime now = LocalDateTime.now();
        Long boardId = boardsRepository.save(board("b", now)).getId();
        Long postId = insertPost(boardId, u.getId(), "t", "c", "PUBLISHED", now);

        ModerationQueueEntity q = moderationQueueRepository.findByContentTypeAndContentId(ContentType.POST, postId)
                .orElseGet(ModerationQueueEntity::new);
        if (q.getCaseType() == null) q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(postId);
        q.setStatus(QueueStatus.APPROVED);
        q.setCurrentStage(QueueStage.HUMAN);
        if (q.getPriority() == null) q.setPriority(0);
        q.setAssignedToId(null);
        q.setFinishedAt(now);
        if (q.getCreatedAt() == null) q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = moderationQueueRepository.save(q);

        mockMvc.perform(post("/api/admin/moderation/queue/{id}/to-human", q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/admin/moderation/queue/{id}/reject", q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"status\":\"REJECTED\"");
    }

    private static BoardsEntity board(String name, LocalDateTime now) {
        BoardsEntity b = new BoardsEntity();
        b.setTenantId(null);
        b.setName(name);
        b.setVisible(true);
        b.setSortOrder(0);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        return b;
    }

    private Long insertPost(Long boardId, Long authorId, String title, String content, String status, LocalDateTime now) {
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
            ps.setString(7, status);
            ps.setTimestamp(8, Timestamp.valueOf(now));
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
