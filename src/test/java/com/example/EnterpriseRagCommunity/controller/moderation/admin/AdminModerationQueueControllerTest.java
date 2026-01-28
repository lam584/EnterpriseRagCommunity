package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

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
    private PostsRepository postsRepository;

    @Autowired
    private UsersRepository usersRepository;

    private void ensureUserExists(String username) {
        if (usersRepository.findByEmailAndIsDeletedFalse(username).isPresent()) return;
        UsersEntity u = new UsersEntity();
        u.setTenantId(null);
        u.setEmail(username);
        u.setUsername(username);
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        usersRepository.save(u);
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void reject_shouldReturn409_whenAlreadyApproved() throws Exception {
        ensureUserExists("u");
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
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("不能直接驳回");
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void approve_shouldReturn409_whenAlreadyRejected() throws Exception {
        ensureUserExists("u");
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
                        .content("{}"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("不能直接通过");
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_moderation_queue:action"})
    void toHuman_shouldMoveToHuman_forTerminalTask() throws Exception {
        ensureUserExists("u");
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
                        .content("{}"))
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
        ensureUserExists("u");
        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(1L);
        p.setAuthorId(1L);
        p.setTitle("t");
        p.setContent("c");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setPublishedAt(LocalDateTime.now());
        p.setIsDeleted(false);
        postsRepository.save(p);

        LocalDateTime now = LocalDateTime.now();
        ModerationQueueEntity q = moderationQueueRepository.findByContentTypeAndContentId(ContentType.POST, p.getId())
                .orElseGet(ModerationQueueEntity::new);
        if (q.getCaseType() == null) q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(p.getId());
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
                        .content("{}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/admin/moderation/queue/{id}/reject", q.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"status\":\"REJECTED\"");
    }
}
