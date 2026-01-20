package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.service.content.TagsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Api(tags = "标签管理")
public class TagsController {

    private final TagsService tagsService;

    @GetMapping
    @ApiOperation("查询标签列表")
    public ResponseEntity<Page<TagsDTO>> query(@ModelAttribute TagsQueryDTO queryDTO) {
        Page<TagsDTO> page = tagsService.query(queryDTO).map(this::toDTO);
        return ResponseEntity.ok(page);
    }

    @PostMapping
    @ApiOperation("创建标签")
    public ResponseEntity<TagsDTO> create(@Valid @RequestBody TagsCreateDTO createDTO) {
        TagsEntity saved = tagsService.create(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
    }

    @PutMapping("/{id}")
    @ApiOperation("更新标签")
    public ResponseEntity<TagsDTO> update(@PathVariable("id") Long id, @RequestBody TagsUpdateDTO updateDTO) {
        updateDTO.setId(id);
        TagsEntity saved = tagsService.update(updateDTO);
        return ResponseEntity.ok(toDTO(saved));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除标签")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        tagsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private TagsDTO toDTO(TagsEntity entity) {
        TagsDTO dto = new TagsDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}

