package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.RiskLabelingRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.CurrentUsernameResolver;
import com.example.EnterpriseRagCommunity.service.content.TagsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/risk-tags")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRiskTagsController {

    private final TagsService tagsService;
    private final TagsRepository tagsRepository;
    private final RiskLabelingRepository riskLabelingRepository;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_risk_tags','read'))")
    public ResponseEntity<Page<TagsDTO>> query(@ModelAttribute TagsQueryDTO queryDTO) {
        if (queryDTO == null) queryDTO = new TagsQueryDTO();
        queryDTO.setType(TagType.RISK);
        Page<TagsEntity> page = tagsService.query(queryDTO);

        Map<Long, Long> usageCountMap = Collections.emptyMap();
        if (!page.isEmpty()) {
            var ids = page.getContent().stream().map(TagsEntity::getId).collect(Collectors.toList());
            usageCountMap = riskLabelingRepository.countUsageByTagIds(ids).stream()
                    .collect(Collectors.toMap(RiskLabelingRepository.TagUsageCount::getTagId, RiskLabelingRepository.TagUsageCount::getUsageCount));
        }
        Map<Long, Long> finalUsageCountMap = usageCountMap;
        return ResponseEntity.ok(page.map(e -> toDTO(e, finalUsageCountMap.getOrDefault(e.getId(), 0L))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_risk_tags','write'))")
    public ResponseEntity<TagsDTO> create(@Valid @RequestBody TagsCreateDTO createDTO) {
        if (createDTO == null) throw new IllegalArgumentException("参数不能为空");
        createDTO.setType(TagType.RISK);
        if (createDTO.getTenantId() == null) createDTO.setTenantId(1L);
        if (createDTO.getIsSystem() == null) createDTO.setIsSystem(false);
        if (createDTO.getIsActive() == null) createDTO.setIsActive(true);

        TagsEntity saved = tagsService.create(createDTO);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "RISK_TAG_CREATE",
                "TAG",
                saved.getId(),
                AuditResult.SUCCESS,
                "创建风险标签",
                null,
                auditDiffBuilder.build(Map.of(), saved)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved, 0L));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_risk_tags','write'))")
    public ResponseEntity<TagsDTO> update(@PathVariable("id") Long id, @Valid @RequestBody TagsUpdateDTO updateDTO) {
        if (updateDTO == null) updateDTO = new TagsUpdateDTO();
        updateDTO.setId(id);
        if (updateDTO.getType() != null && updateDTO.getType() != TagType.RISK) {
            throw new IllegalArgumentException("风险标签类型不可修改");
        }

        TagsEntity current = tagsRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("审核标签不存在: " + id));
        if (current.getType() != TagType.RISK) {
            throw new IllegalArgumentException("仅允许修改风险标签");
        }

        TagsEntity saved = tagsService.update(updateDTO);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "RISK_TAG_UPDATE",
                "TAG",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新风险标签",
                null,
                auditDiffBuilder.build(current, saved)
        );
        long usageCount = riskLabelingRepository.countUsageByTagIds(Collections.singleton(saved.getId())).stream()
                .findFirst()
                .map(RiskLabelingRepository.TagUsageCount::getUsageCount)
                .orElse(0L);
        return ResponseEntity.ok(toDTO(saved, usageCount));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_risk_tags','write'))")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        TagsEntity current = tagsRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("审核标签不存在: " + id));
        if (current.getType() != TagType.RISK) {
            throw new IllegalArgumentException("仅允许删除风险标签");
        }
        if (riskLabelingRepository.existsByTagId(id)) {
            throw new IllegalStateException("标签正在使用，无法删除。");
        }
        tagsService.delete(id);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "RISK_TAG_DELETE",
                "TAG",
                id,
                AuditResult.SUCCESS,
                "删除风险标签",
                null,
                auditDiffBuilder.build(current, Map.of())
        );
        return ResponseEntity.noContent().build();
    }

    private static String currentUsernameOrNull() {
        return CurrentUsernameResolver.currentUsernameOrNull();
    }

    private TagsDTO toDTO(TagsEntity entity, Long usageCount) {
        TagsDTO dto = new TagsDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setUsageCount(usageCount == null ? 0L : usageCount);
        return dto;
    }
}
