package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.security.Permissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminModerationChunkReviewLogsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ModerationChunkRepository chunkRepository;

    @Autowired
    ModerationChunkSetRepository chunkSetRepository;

    @Autowired
    ModerationQueueRepository moderationQueueRepository;

    @Autowired
    PostsRepository postsRepository;

    @Autowired
    FileAssetsRepository fileAssetsRepository;

    @Autowired
    FileAssetExtractionsRepository fileAssetExtractionsRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void list_shouldDeny_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/moderation/chunk-review/logs")
                        .with(user("u@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void content_shouldDeny_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/moderation/chunk-review/logs/1/content")
                        .with(user("u@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAndDetail_shouldWork_withPermission() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                "INSERT INTO users(id,email,username,password_hash,status,is_deleted,created_at,updated_at) VALUES (1,'u1@example.com','u1','x','ACTIVE',0,NOW(3),NOW(3)) ON DUPLICATE KEY UPDATE id=id"
        );
        jdbcTemplate.update(
                "INSERT INTO boards(id,tenant_id,parent_id,name,description,visible,sort_order,created_at,updated_at) VALUES (1,NULL,NULL,'b',NULL,1,0,NOW(3),NOW(3)) ON DUPLICATE KEY UPDATE id=id"
        );

        PostsEntity post = new PostsEntity();
        post.setBoardId(1L);
        post.setAuthorId(1L);
        post.setTitle("t");
        post.setContent("hello chunk content\nline2");
        post.setContentLength(post.getContent().length());
        post.setIsChunkedReview(true);
        post.setContentFormat(ContentFormat.MARKDOWN);
        post.setStatus(PostStatus.PUBLISHED);
        post = postsRepository.save(post);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(post.getId());
        q.setStatus(QueueStatus.REVIEWING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setCreatedAt(now.minusMinutes(3));
        q.setUpdatedAt(now.minusMinutes(1));
        q = moderationQueueRepository.save(q);

        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(q.getId());
        set.setCaseType(ModerationCaseType.CONTENT);
        set.setContentType(ContentType.POST);
        set.setContentId(post.getId());
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setChunkThresholdChars(12000);
        set.setChunkSizeChars(4000);
        set.setOverlapChars(400);
        set.setTotalChunks(3);
        set.setCompletedChunks(1);
        set.setFailedChunks(0);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("enableGlobalMemory", true);
        set.setConfigJson(cfg);
        Map<String, Object> mem = new HashMap<>();
        mem.put("k", "v");
        set.setMemoryJson(mem);
        set.setCreatedAt(now.minusMinutes(2));
        set.setUpdatedAt(now.minusMinutes(1));
        set = chunkSetRepository.save(set);

        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setChunkSetId(set.getId());
        c.setSourceType(ChunkSourceType.POST_TEXT);
        c.setSourceKey("post:" + post.getId());
        c.setFileAssetId(null);
        c.setFileName(null);
        c.setChunkIndex(0);
        c.setStartOffset(0);
        c.setEndOffset(5);
        c.setStatus(ChunkStatus.SUCCESS);
        c.setAttempts(1);
        c.setLastError(null);
        c.setModel("gpt-test");
        c.setVerdict(Verdict.APPROVE);
        c.setConfidence(new BigDecimal("0.9876"));
        Map<String, Object> labels = new HashMap<>();
        labels.put("risk", "low");
        c.setLabels(labels);
        c.setTokensIn(111);
        c.setTokensOut(222);
        c.setDecidedAt(now.minusMinutes(1));
        c.setCreatedAt(now.minusMinutes(2));
        c.setUpdatedAt(now.minusMinutes(1));
        c = chunkRepository.save(c);

        String perm = Permissions.perm("admin_moderation_chunk_review", "access");

        mockMvc.perform(get("/api/admin/moderation/chunk-review/logs")
                        .with(user("admin@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(c.getId()))
                .andExpect(jsonPath("$[0].queueId").value(q.getId().intValue()));

        mockMvc.perform(get("/api/admin/moderation/chunk-review/logs/{id}", c.getId())
                        .with(user("admin@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunk.id").value(c.getId()))
                .andExpect(jsonPath("$.chunkSet.id").value(set.getId()))
                .andExpect(jsonPath("$.chunk.labels.risk").value("low"))
                .andExpect(jsonPath("$.chunkSet.configJson.enableGlobalMemory").value(true));

        mockMvc.perform(get("/api/admin/moderation/chunk-review/logs/{id}/content", c.getId())
                        .with(user("admin@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.chunkId").value(c.getId()))
                .andExpect(jsonPath("$.text").value("hello"));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setPath("C:/tmp/test.txt");
        fa.setUrl("/uploads/test.txt");
        fa.setOriginalName("test.txt");
        fa.setSizeBytes(123L);
        fa.setMimeType("text/plain");
        fa.setSha256(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        fa = fileAssetsRepository.save(fa);

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(fa.getId());
        ex.setExtractedText("a [[IMAGE_1]] b [[IMAGE_2]]");
        ex.setExtractedMetadataJson("{\"extractedImages\":[{\"index\":1,\"placeholder\":\"[[IMAGE_1]]\",\"url\":\"/uploads/derived-images/2026/02/x.png\",\"mimeType\":\"image/png\",\"fileName\":\"x.png\",\"sizeBytes\":12},{\"index\":2,\"placeholder\":\"[[IMAGE_2]]\",\"url\":\"/uploads/derived-images/2026/02/y.png\",\"mimeType\":\"image/png\",\"fileName\":\"y.png\",\"sizeBytes\":34}]}");
        fileAssetExtractionsRepository.save(ex);

        ModerationChunkEntity c2 = new ModerationChunkEntity();
        c2.setChunkSetId(set.getId());
        c2.setSourceType(ChunkSourceType.FILE_TEXT);
        c2.setSourceKey("file:" + fa.getId());
        c2.setFileAssetId(fa.getId());
        c2.setFileName("test.txt");
        c2.setChunkIndex(1);
        c2.setStartOffset(0);
        c2.setEndOffset(ex.getExtractedText().length());
        c2.setStatus(ChunkStatus.SUCCESS);
        c2.setAttempts(1);
        c2.setModel("gpt-test");
        c2.setVerdict(Verdict.APPROVE);
        c2.setConfidence(new BigDecimal("0.1111"));
        c2.setTokensIn(1);
        c2.setTokensOut(2);
        c2.setDecidedAt(now.minusMinutes(1));
        c2.setCreatedAt(now.minusMinutes(2));
        c2.setUpdatedAt(now.minusMinutes(1));
        c2 = chunkRepository.save(c2);

        mockMvc.perform(get("/api/admin/moderation/chunk-review/logs/{id}/content", c2.getId())
                        .with(user("admin@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.chunkId").value(c2.getId()))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].url").value("/uploads/derived-images/2026/02/x.png"));
    }
}
