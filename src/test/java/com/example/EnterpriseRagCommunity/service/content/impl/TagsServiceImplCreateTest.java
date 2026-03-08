package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.TagsCreateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.PostTagRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagsServiceImplCreateTest {

    @Test
    void create_shouldTrimAndDefaultAndSave_whenUnique() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsCreateDTO dto = new TagsCreateDTO();
        dto.setTenantId(1L);
        dto.setType(TagType.TOPIC);
        dto.setName("  Java  ");
        dto.setSlug("  java-1  ");
        dto.setDescription("   ");
        dto.setIsSystem(null);
        dto.setIsActive(Boolean.TRUE);
        dto.setThreshold(0.3);

        when(tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.TOPIC, "java-1"))
                .thenReturn(Optional.empty());
        when(tagsRepository.save(any(TagsEntity.class))).thenAnswer(inv -> {
            TagsEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        LocalDateTime before = LocalDateTime.now();
        TagsEntity saved = service.create(dto);
        LocalDateTime after = LocalDateTime.now();

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(TagType.TOPIC);
        assertThat(saved.getName()).isEqualTo("Java");
        assertThat(saved.getSlug()).isEqualTo("java-1");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getIsSystem()).isFalse();
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getThreshold()).isEqualTo(0.3);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void create_shouldThrow_whenDuplicateExists() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsCreateDTO dto = new TagsCreateDTO();
        dto.setTenantId(1L);
        dto.setType(TagType.TOPIC);
        dto.setName("Java");
        dto.setSlug("java");
        dto.setIsSystem(Boolean.FALSE);
        dto.setIsActive(Boolean.TRUE);

        TagsEntity existing = new TagsEntity();
        existing.setId(11L);
        when(tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.TOPIC, "java"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("标签已存在（tenantId+type+slug 唯一）。");
    }
}

