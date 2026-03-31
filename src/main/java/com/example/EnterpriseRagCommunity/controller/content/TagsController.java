package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostTagRepository;
import com.example.EnterpriseRagCommunity.service.content.TagsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Api(tags = "标签管理")
public class TagsController {

    private final TagsService tagsService;
    private final PostTagRepository postTagRepository;

    @GetMapping
    @ApiOperation("查询标签列表")
    public ResponseEntity<Page<TagsDTO>> query(@ModelAttribute TagsQueryDTO queryDTO) {
        Page<TagsEntity> page = tagsService.query(queryDTO);
        Map<Long, Long> usageCountMap = Collections.emptyMap();
        if (!page.isEmpty()) {
            var ids = page.getContent().stream().map(TagsEntity::getId).collect(Collectors.toList());
            usageCountMap = postTagRepository.countUsageByTagIds(ids).stream()
                    .collect(Collectors.toMap(PostTagRepository.TagUsageCount::getTagId, PostTagRepository.TagUsageCount::getUsageCount));
        }
        Map<Long, Long> finalUsageCountMap = usageCountMap;
        return ResponseEntity.ok(page.map(e -> toDTO(e, finalUsageCountMap.getOrDefault(e.getId(), 0L))));
    }

    @PostMapping
    @ApiOperation("创建标签")
    public ResponseEntity<TagsDTO> create(@Valid @RequestBody TagsCreateDTO createDTO) {
        TagsEntity saved = tagsService.create(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved, 0L));
    }

    @PutMapping("/{id}")
    @ApiOperation("更新标签")
    public ResponseEntity<TagsDTO> update(@PathVariable("id") Long id, @Valid @RequestBody TagsUpdateDTO updateDTO) {
        updateDTO.setId(id);
        TagsEntity saved = tagsService.update(updateDTO);
        long usageCount = postTagRepository.countUsageByTagIds(Collections.singleton(saved.getId())).stream()
                .findFirst()
                .map(PostTagRepository.TagUsageCount::getUsageCount)
                .orElse(0L);
        return ResponseEntity.ok(toDTO(saved, usageCount));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除标签")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        tagsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private TagsDTO toDTO(TagsEntity entity, Long usageCount) {
        TagsDTO dto = new TagsDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setType(entity.getType());
        dto.setName(entity.getName());
        dto.setSlug(entity.getSlug());
        dto.setDescription(entity.getDescription());
        dto.setIsSystem(entity.getIsSystem());
        dto.setIsActive(entity.getIsActive());
        dto.setThreshold(entity.getThreshold());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUsageCount(usageCount == null ? 0L : usageCount);
        return dto;
    }
}

