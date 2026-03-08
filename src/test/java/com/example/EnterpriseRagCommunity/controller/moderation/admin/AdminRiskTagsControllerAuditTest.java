package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.RiskLabelingRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.TagsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminRiskTagsControllerAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        TagsService tagsService = mock(TagsService.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        RiskLabelingRepository riskLabelingRepository = mock(RiskLabelingRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        TagsEntity saved = new TagsEntity();
        saved.setId(501L);
        saved.setType(TagType.RISK);
        when(tagsService.create(any())).thenReturn(saved);
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminRiskTagsController c = new AdminRiskTagsController(tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsCreateDTO dto = new TagsCreateDTO();
        dto.setName("t1");
        dto.setDescription("d");

        ResponseEntity<?> resp = c.create(dto);
        org.junit.jupiter.api.Assertions.assertEquals(201, resp.getStatusCode().value());

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("RISK_TAG_CREATE"),
                eq("TAG"),
                eq(501L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void updateWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        TagsService tagsService = mock(TagsService.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        RiskLabelingRepository riskLabelingRepository = mock(RiskLabelingRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        TagsEntity current = new TagsEntity();
        current.setId(502L);
        current.setType(TagType.RISK);
        when(tagsRepository.findById(502L)).thenReturn(Optional.of(current));

        TagsEntity saved = new TagsEntity();
        saved.setId(502L);
        saved.setType(TagType.RISK);
        when(tagsService.update(any())).thenReturn(saved);
        when(riskLabelingRepository.countUsageByTagIds(any())).thenReturn(List.of());
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminRiskTagsController c = new AdminRiskTagsController(tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        TagsUpdateDTO dto = new TagsUpdateDTO();

        c.update(502L, dto);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("RISK_TAG_UPDATE"),
                eq("TAG"),
                eq(502L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void deleteWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        TagsService tagsService = mock(TagsService.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        RiskLabelingRepository riskLabelingRepository = mock(RiskLabelingRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        TagsEntity current = new TagsEntity();
        current.setId(503L);
        current.setType(TagType.RISK);
        when(tagsRepository.findById(503L)).thenReturn(Optional.of(current));
        when(riskLabelingRepository.existsByTagId(503L)).thenReturn(false);
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminRiskTagsController c = new AdminRiskTagsController(tagsService, tagsRepository, riskLabelingRepository, auditLogWriter, auditDiffBuilder);
        c.delete(503L);

        verify(auditLogWriter).write(
                eq(null),
                eq("alice@example.com"),
                eq("RISK_TAG_DELETE"),
                eq("TAG"),
                eq(503L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}

