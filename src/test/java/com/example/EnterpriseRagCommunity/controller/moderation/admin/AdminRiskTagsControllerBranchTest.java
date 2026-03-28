package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.RiskLabelingRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.TagsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRiskTagsControllerBranchTest {

    @Mock
    private TagsService tagsService;
    @Mock
    private TagsRepository tagsRepository;
    @Mock
    private RiskLabelingRepository riskLabelingRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private AuditDiffBuilder auditDiffBuilder;

    @Test
    void query_setsRiskType_whenInputQueryIsNull() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsEntity e = new TagsEntity();
        e.setId(11L);
        e.setName("tag-1");
        e.setType(TagType.RISK);
        Page<TagsEntity> page = new PageImpl<>(List.of(e));
        when(tagsService.query(any(TagsQueryDTO.class))).thenReturn(page);
        RiskLabelingRepository.TagUsageCount usage = new RiskLabelingRepository.TagUsageCount() {
            @Override
            public Long getTagId() {
                return 11L;
            }

            @Override
            public Long getUsageCount() {
                return 3L;
            }
        };
        when(riskLabelingRepository.countUsageByTagIds(List.of(11L))).thenReturn(List.of(usage));

        var resp = controller.query(null);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, resp.getBody().getTotalElements());
        assertEquals(3L, resp.getBody().getContent().get(0).getUsageCount());
        verify(tagsService).query(any(TagsQueryDTO.class));
    }

    @Test
    void update_throws_whenCurrentTagTypeIsNotRisk() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsEntity current = new TagsEntity();
        current.setId(12L);
        current.setType(TagType.SYSTEM);
        when(tagsRepository.findById(12L)).thenReturn(Optional.of(current));
        TagsUpdateDTO dto = new TagsUpdateDTO();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.update(12L, dto));

        assertEquals("仅允许修改风险标签", ex.getMessage());
    }

    @Test
    void delete_throws_whenTagIsUsed() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsEntity current = new TagsEntity();
        current.setId(13L);
        current.setType(TagType.RISK);
        when(tagsRepository.findById(13L)).thenReturn(Optional.of(current));
        when(riskLabelingRepository.existsByTagId(13L)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> controller.delete(13L));

        assertEquals("标签正在使用，无法删除。", ex.getMessage());
    }

    @Test
    void query_shouldSkipUsageLookup_whenPageIsEmpty() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        when(tagsService.query(any(TagsQueryDTO.class))).thenReturn(Page.empty());

        var resp = controller.query(new TagsQueryDTO());

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().getTotalElements());
    }

    @Test
    void create_update_delete_successPaths() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        TagsCreateDTO create = new TagsCreateDTO();
        create.setName("r1");
        TagsEntity created = new TagsEntity();
        created.setId(21L);
        created.setType(TagType.RISK);
        when(tagsService.create(any())).thenReturn(created);
        var createResp = controller.create(create);
        assertEquals(201, createResp.getStatusCode().value());

        TagsEntity current = new TagsEntity();
        current.setId(22L);
        current.setType(TagType.RISK);
        when(tagsRepository.findById(22L)).thenReturn(Optional.of(current));
        TagsEntity updated = new TagsEntity();
        updated.setId(22L);
        updated.setType(TagType.RISK);
        when(tagsService.update(any())).thenReturn(updated);
        when(riskLabelingRepository.countUsageByTagIds(any())).thenReturn(List.of());
        var updateResp = controller.update(22L, new TagsUpdateDTO());
        assertEquals(200, updateResp.getStatusCode().value());

        TagsEntity deleting = new TagsEntity();
        deleting.setId(23L);
        deleting.setType(TagType.RISK);
        when(tagsRepository.findById(23L)).thenReturn(Optional.of(deleting));
        when(riskLabelingRepository.existsByTagId(23L)).thenReturn(false);
        var delResp = controller.delete(23L);
        assertEquals(204, delResp.getStatusCode().value());
    }

    @Test
    void create_shouldCoverCurrentUsernameOrNullBranches() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());
        TagsCreateDTO create = new TagsCreateDTO();
        create.setName("r2");
        TagsEntity created = new TagsEntity();
        created.setId(31L);
        created.setType(TagType.RISK);
        when(tagsService.create(any())).thenReturn(created);

        SecurityContextHolder.clearContext();
        controller.create(create);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "n/a", List.of())
        );
        controller.create(create);

        Authentication unauth = org.mockito.Mockito.mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);
        controller.create(create);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  ", "n/a", List.of())
        );
        controller.create(create);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(" bob@example.com ", "n/a", List.of())
        );
        controller.create(create);

        SecurityContextHolder.clearContext();
    }

    @Test
    void update_and_delete_shouldThrowWhenTagNotFound() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        when(tagsRepository.findById(44L)).thenReturn(Optional.empty());

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> controller.update(44L, new TagsUpdateDTO()));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> controller.delete(44L));

        assertEquals("审核标签不存在: 44", ex1.getMessage());
        assertEquals("审核标签不存在: 44", ex2.getMessage());
    }

    @Test
    void privateToDto_shouldHandleNullUsageCount() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsEntity e = new TagsEntity();
        e.setId(1L);
        var dto = (com.example.EnterpriseRagCommunity.dto.content.TagsDTO)
                ReflectionTestUtils.invokeMethod(controller, "toDTO", e, null);
        assertEquals(0L, dto.getUsageCount());
    }

    @Test
    void create_shouldRejectNullBody() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.create(null));

        assertEquals("参数不能为空", ex.getMessage());
    }

    @Test
    void update_shouldRejectTypeMutation_whenTypeIsNotRisk() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setType(TagType.TOPIC);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> controller.update(55L, dto));

        assertEquals("风险标签类型不可修改", ex.getMessage());
    }

    @Test
    void delete_shouldRejectNullIdAndNonRiskTag() {
        AdminRiskTagsController controller = new AdminRiskTagsController(
                tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> controller.delete(null));
        assertEquals("id 不能为空", ex1.getMessage());

        TagsEntity nonRisk = new TagsEntity();
        nonRisk.setId(66L);
        nonRisk.setType(TagType.TOPIC);
        when(tagsRepository.findById(66L)).thenReturn(Optional.of(nonRisk));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> controller.delete(66L));
        assertEquals("仅允许删除风险标签", ex2.getMessage());
    }
}
