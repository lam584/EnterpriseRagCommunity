package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.TagsQueryDTO;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.repository.content.PostTagRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagsServiceImplQueryTest {

    @Test
    void query_shouldUseDefaultPagingAndSort_whenNoSortByAndNullPageParams() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<TagsEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(tagsRepository.findAll(specCaptor.capture(), pageableCaptor.capture()))
                .thenReturn(Page.empty());

        TagsQueryDTO dto = new TagsQueryDTO();
        dto.setPage(null);
        dto.setPageSize(null);

        service.query(dto);

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection().name()).isEqualTo("DESC");

        CriteriaEnv env = new CriteriaEnv();
        specCaptor.getValue().toPredicate(env.root, env.criteriaQuery, env.cb);
    }

    @Test
    void query_shouldExecuteSpecificationBranches_andCoverKeywordTryCatch_andSortBranches() {
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PostTagRepository postTagRepository = mock(PostTagRepository.class);
        TagsServiceImpl service = new TagsServiceImpl(tagsRepository, postTagRepository);

        when(tagsRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        TagsQueryDTO dto1 = new TagsQueryDTO();
        dto1.setId(10L);
        dto1.setTenantId(1L);
        dto1.setType(TagType.TOPIC);
        dto1.setName("Java");
        dto1.setNameLike("ja");
        dto1.setKeyword("123");
        dto1.setSlug("java");
        dto1.setDescription("desc");
        dto1.setIsSystem(Boolean.TRUE);
        dto1.setIsActive(Boolean.FALSE);
        dto1.setCreatedAt(LocalDateTime.now());
        dto1.setCreatedFrom(dto1.getCreatedAt().minusDays(1));
        dto1.setCreatedTo(dto1.getCreatedAt().plusDays(1));
        dto1.setPage(2);
        dto1.setPageSize(5);
        dto1.setSortBy("name");
        dto1.setSortOrder("asc");

        TagsQueryDTO dto2 = new TagsQueryDTO();
        dto2.setKeyword("abc");
        dto2.setSortBy("name");
        dto2.setSortOrder("desc");

        service.query(dto1);
        service.query(dto2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<TagsEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tagsRepository, times(2)).findAll(specCaptor.capture(), pageableCaptor.capture());

        Pageable p1 = pageableCaptor.getAllValues().get(0);
        assertThat(p1.getPageNumber()).isEqualTo(1);
        assertThat(p1.getPageSize()).isEqualTo(5);
        assertThat(p1.getSort().getOrderFor("name")).isNotNull();
        assertThat(p1.getSort().getOrderFor("name").getDirection().name()).isEqualTo("ASC");

        Pageable p2 = pageableCaptor.getAllValues().get(1);
        assertThat(p2.getSort().getOrderFor("name")).isNotNull();
        assertThat(p2.getSort().getOrderFor("name").getDirection().name()).isEqualTo("DESC");

        CriteriaEnv env = new CriteriaEnv();
        specCaptor.getAllValues().forEach(spec -> spec.toPredicate(env.root, env.criteriaQuery, env.cb));
    }

    private static final class CriteriaEnv {
        private final Root<TagsEntity> root = mock(Root.class);
        private final CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

        private final Path<Long> idPath = mock(Path.class);
        private final Path<Long> tenantIdPath = mock(Path.class);
        private final Path<TagType> typePath = mock(Path.class);
        private final Path<String> namePath = mock(Path.class);
        private final Path<String> slugPath = mock(Path.class);
        private final Path<String> descriptionPath = mock(Path.class);
        private final Path<Boolean> isSystemPath = mock(Path.class);
        private final Path<Boolean> isActivePath = mock(Path.class);
        private final Path<LocalDateTime> createdAtPath = mock(Path.class);

        private CriteriaEnv() {
            lenient().when(root.<Long>get("id")).thenReturn(idPath);
            lenient().when(root.<Long>get("tenantId")).thenReturn(tenantIdPath);
            lenient().when(root.<TagType>get("type")).thenReturn(typePath);
            lenient().when(root.<String>get("name")).thenReturn(namePath);
            lenient().when(root.<String>get("slug")).thenReturn(slugPath);
            lenient().when(root.<String>get("description")).thenReturn(descriptionPath);
            lenient().when(root.<Boolean>get("isSystem")).thenReturn(isSystemPath);
            lenient().when(root.<Boolean>get("isActive")).thenReturn(isActivePath);
            lenient().when(root.<LocalDateTime>get("createdAt")).thenReturn(createdAtPath);

            @SuppressWarnings("unchecked")
            Expression<String> typeAsString = mock(Expression.class);
            lenient().when(typePath.as(String.class)).thenReturn(typeAsString);

            lenient().when(cb.equal(any(), any())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.like(any(Expression.class), anyString())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.lower(any(Expression.class))).thenAnswer(inv -> mock(Expression.class));
            lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.or(any(Predicate[].class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.and(any(Predicate[].class))).thenAnswer(inv -> mock(Predicate.class));
        }
    }
}
