package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.RiskLabelingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskLabelingServiceNoAutoCreateTest {

    @Test
    void replaceRiskTags_shouldIgnoreUnknownTagsAndNotCreate() {
        RiskLabelingRepository riskLabelingRepository = mock(RiskLabelingRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        when(tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        RiskLabelingService svc = new RiskLabelingService(riskLabelingRepository, tagsRepository);
        svc.replaceRiskTags(ContentType.POST, 1L, Source.LLM, List.of("unknown-tag"), BigDecimal.valueOf(0.9), false);

        verify(tagsRepository, never()).save(any());
        verify(riskLabelingRepository, never()).save(any());
    }

    @Test
    void replaceRiskTags_shouldPersistExistingActiveTagsOnly() {
        RiskLabelingRepository riskLabelingRepository = mock(RiskLabelingRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);

        TagsEntity t = new TagsEntity();
        t.setId(10L);
        t.setTenantId(1L);
        t.setType(TagType.RISK);
        t.setSlug("violence");
        t.setName("Violence");
        t.setIsActive(true);

        when(tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(t));

        RiskLabelingService svc = new RiskLabelingService(riskLabelingRepository, tagsRepository);
        svc.replaceRiskTags(ContentType.POST, 1L, Source.LLM, List.of("violence"), BigDecimal.valueOf(0.9), false);

        verify(riskLabelingRepository).save(any());
        verify(tagsRepository, never()).save(any());
    }

    @Test
    void replaceRiskTags_shouldPersistByName() {
        RiskLabelingRepository riskLabelingRepository = mock(RiskLabelingRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);

        TagsEntity t = new TagsEntity();
        t.setId(11L);
        t.setTenantId(null);
        t.setType(TagType.RISK);
        t.setSlug("violence");
        t.setName("暴力血腥");
        t.setIsActive(true);

        when(tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(t));

        RiskLabelingService svc = new RiskLabelingService(riskLabelingRepository, tagsRepository);
        svc.replaceRiskTags(ContentType.POST, 1L, Source.LLM, List.of("暴力血腥"), BigDecimal.valueOf(0.9), false);

        verify(riskLabelingRepository).save(any());
        verify(tagsRepository, never()).save(any());
    }
}
