package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.dto.content.TagsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostTagRepository;
import com.example.EnterpriseRagCommunity.service.content.TagsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagsControllerBranchUnitTest {

    @Mock
    TagsService tagsService;

    @Mock
    PostTagRepository postTagRepository;

    @Test
    void queryAndUpdate_shouldCoverUsageBranches() {
        TagsController c = new TagsController(tagsService, postTagRepository);

        TagsEntity t1 = new TagsEntity();
        t1.setId(1L);
        t1.setName("a");
        TagsEntity t2 = new TagsEntity();
        t2.setId(2L);
        t2.setName("b");

        PostTagRepository.TagUsageCount usage = mock(PostTagRepository.TagUsageCount.class);
        when(usage.getTagId()).thenReturn(1L);
        when(usage.getUsageCount()).thenReturn(9L);

        when(tagsService.query(any(TagsQueryDTO.class)))
                .thenReturn(new PageImpl<>(List.of(t1, t2), PageRequest.of(0, 20), 2))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0));
        when(postTagRepository.countUsageByTagIds(List.of(1L, 2L))).thenReturn(List.of(usage));
        when(tagsService.update(any(TagsUpdateDTO.class))).thenReturn(t1);
        when(postTagRepository.countUsageByTagIds(Collections.singleton(1L))).thenReturn(Collections.emptyList());

        var response = c.query(new TagsQueryDTO());
        assertEquals(200, response.getStatusCode().value());
        assertEquals(9L, response.getBody().getContent().get(0).getUsageCount());
        assertEquals(0L, response.getBody().getContent().get(1).getUsageCount());

        var empty = c.query(new TagsQueryDTO());
        assertTrue(empty.getBody().isEmpty());

        TagsUpdateDTO updateDTO = new TagsUpdateDTO();
        var updated = c.update(1L, updateDTO);
        assertEquals(200, updated.getStatusCode().value());
        assertEquals(0L, updated.getBody().getUsageCount());
    }
}
