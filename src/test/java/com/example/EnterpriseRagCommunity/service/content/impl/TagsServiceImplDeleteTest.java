package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.PostTagRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagsServiceImplDeleteTest {

    @Test
    void delete_shouldThrow_whenNotFound() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        when(tagsRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Tag not found with id: 10");
    }

    @Test
    void delete_shouldThrow_whenSystemTag() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        entity.setIsSystem(Boolean.TRUE);
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("系统标签不可删除。");
    }

    @Test
    void delete_shouldThrow_whenTagInUse() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        entity.setIsSystem(Boolean.FALSE);
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(postTagRepository.existsByTagId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("标签正在使用，无法删除。");
    }

    @Test
    void delete_shouldDelete_whenAllowed() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        TagsEntity entity = baseEntity();
        entity.setIsSystem(Boolean.FALSE);
        when(tagsRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(postTagRepository.existsByTagId(10L)).thenReturn(false);

        service.delete(10L);

        verify(tagsRepository).deleteById(10L);
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

