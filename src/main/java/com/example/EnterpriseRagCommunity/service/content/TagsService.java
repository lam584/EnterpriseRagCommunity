package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import org.springframework.data.domain.Page;

/**
 * 标签管理 Service（薄 Repository + Service 组合查询 Specification）。
 *
 * 注意：Controller 层必须返回 DTO，而不是 Entity。
 */
public interface TagsService {

    Page<TagsEntity> query(TagsQueryDTO queryDTO);

    TagsEntity create(TagsCreateDTO createDTO);

    TagsEntity update(TagsUpdateDTO updateDTO);

    void delete(Long id);
}
