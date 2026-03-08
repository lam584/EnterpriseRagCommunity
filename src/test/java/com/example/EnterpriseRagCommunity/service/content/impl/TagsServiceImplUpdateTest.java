package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagsServiceImplUpdateTest {

    @Test
    void update_shouldThrow_whenNotFound() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setId(10L);

        when(tagsRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Tag not found with id: 10");
    }

    @Test
    void update_shouldIgnoreNullOptionalFields() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.TOPIC, "old-slug"))
                .thenReturn(Optional.empty());
        when(tagsRepository.save(any(TagsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setId(10L);
        dto.setTenantId(null);
        dto.setType(null);
        dto.setName(null);
        dto.setSlug(null);
        dto.setDescription(null);
        dto.setIsSystem(null);
        dto.setIsActive(null);
        dto.setThreshold(null);

        TagsEntity saved = service.update(dto);

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getSlug()).isEqualTo("old-slug");
        verify(tagsRepository).save(entity);
    }

    @Test
    void update_shouldUpdateFieldsAndTrimAndAllowSameIdUniqueHit() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));

        TagsEntity existsSameId = new TagsEntity();
        existsSameId.setId(10L);
        when(tagsRepository.findByTenantIdAndTypeAndSlug(2L, TagType.RISK, "软-色情"))
                .thenReturn(Optional.of(existsSameId));
        when(tagsRepository.save(any(TagsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setId(10L);
        dto.setTenantId(Optional.of(2L));
        dto.setType(Optional.of(TagType.RISK));
        dto.setName(Optional.of("  New Name  "));
        dto.setSlug(Optional.of("软-色情"));
        dto.setDescription(Optional.of("   "));
        dto.setIsSystem(Optional.of(Boolean.TRUE));
        dto.setIsActive(Optional.of(Boolean.FALSE));
        dto.setThreshold(Optional.of(0.5));
        dto.setCreatedAt(Optional.of(LocalDateTime.now().minusDays(1)));

        TagsEntity saved = service.update(dto);

        assertThat(saved.getTenantId()).isEqualTo(2L);
        assertThat(saved.getType()).isEqualTo(TagType.RISK);
        assertThat(saved.getName()).isEqualTo("New Name");
        assertThat(saved.getSlug()).isEqualTo("软-色情");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getIsSystem()).isTrue();
        assertThat(saved.getIsActive()).isFalse();
        assertThat(saved.getThreshold()).isEqualTo(0.5);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void update_shouldThrow_whenUniqueConflictWithDifferentId() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));

        TagsEntity existsDifferentId = new TagsEntity();
        existsDifferentId.setId(11L);
        when(tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.TOPIC, "new-slug"))
                .thenReturn(Optional.of(existsDifferentId));

        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setId(10L);
        dto.setSlug(Optional.of("new-slug"));

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("标签已存在（tenantId+type+slug 唯一）。");
    }

    @Test
    void update_shouldSetNullNameWhenOptionalGetReturnsNull() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.TOPIC, "old-slug"))
                .thenReturn(Optional.empty());
        when(tagsRepository.save(any(TagsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        @SuppressWarnings("unchecked")
        Optional<String> optionalPresentNull = mock(Optional.class);
        when(optionalPresentNull.isPresent()).thenReturn(true);
        when(optionalPresentNull.get()).thenReturn(null);

        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setId(10L);
        dto.setName(optionalPresentNull);

        TagsEntity saved = service.update(dto);
        assertThat(saved.getName()).isNull();
    }

    @Test
    void update_shouldThrowWhenSlugOptionalGetReturnsNullAndTriggersValidation() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));

        @SuppressWarnings("unchecked")
        Optional<String> optionalPresentNull = mock(Optional.class);
        when(optionalPresentNull.isPresent()).thenReturn(true);
        when(optionalPresentNull.get()).thenReturn(null);

        TagsUpdateDTO dto = new TagsUpdateDTO();
        dto.setId(10L);
        dto.setSlug(optionalPresentNull);

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 不能为空。");
        verify(tagsRepository).findById(eq(10L));
    }

    private static TagsEntity baseEntity() {
        TagsEntity entity = new TagsEntity();
        entity.setId(10L);
        entity.setTenantId(1L);
        entity.setType(TagType.TOPIC);
        entity.setName("Old Name");
        entity.setSlug("old-slug");
        entity.setDescription("Old Desc");
        entity.setIsSystem(Boolean.FALSE);
        entity.setIsActive(Boolean.TRUE);
        entity.setThreshold(0.1);
        entity.setCreatedAt(LocalDateTime.now().minusDays(1));
        return entity;
    }
}

