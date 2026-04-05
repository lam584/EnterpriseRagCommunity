package com.example.EnterpriseRagCommunity.service.impl;

import com.example.EnterpriseRagCommunity.dto.content.BoardsQueryDTO;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardServiceImplQueryTest {

    @Test
    void queryBoards_shouldBuildSpecificationAndDefaultPaging() {
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        BoardServiceImpl service = new BoardServiceImpl(boardsRepository, auditLogWriter, auditDiffBuilder);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<BoardsEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(boardsRepository.findAll(specCaptor.capture(), pageableCaptor.capture())).thenReturn(Page.empty());

        BoardsQueryDTO dto = new BoardsQueryDTO();
        dto.setId(10L);
        dto.setTenantId(20L);
        dto.setParentId(30L);
        dto.setName("Board");
        dto.setNameLike("Boa");
        dto.setDescription("desc");
        dto.setVisible(Boolean.TRUE);
        dto.setSortOrder(5);
        dto.setSortOrderFrom(1);
        dto.setSortOrderTo(9);
        dto.setCreatedFrom(LocalDateTime.now().minusDays(1));
        dto.setCreatedTo(LocalDateTime.now());
        dto.setUpdatedFrom(LocalDateTime.now().minusHours(6));
        dto.setUpdatedTo(LocalDateTime.now().plusHours(6));

        service.queryBoards(dto);

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("sortOrder")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("sortOrder").getDirection().name()).isEqualTo("ASC");

        CriteriaEnv env = new CriteriaEnv();
        specCaptor.getValue().toPredicate(env.root, env.criteriaQuery, env.cb);
    }

    private static final class CriteriaEnv {
        private final Root<BoardsEntity> root = mock(Root.class);
        private final CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

        private final Path<Long> idPath = mock(Path.class);
        private final Path<Long> tenantIdPath = mock(Path.class);
        private final Path<Long> parentIdPath = mock(Path.class);
        private final Path<String> namePath = mock(Path.class);
        private final Path<String> descriptionPath = mock(Path.class);
        private final Path<Boolean> visiblePath = mock(Path.class);
        private final Path<Integer> sortOrderPath = mock(Path.class);
        private final Path<LocalDateTime> createdAtPath = mock(Path.class);
        private final Path<LocalDateTime> updatedAtPath = mock(Path.class);

        private CriteriaEnv() {
            lenient().when(root.<Long>get("id")).thenReturn(idPath);
            lenient().when(root.<Long>get("tenantId")).thenReturn(tenantIdPath);
            lenient().when(root.<Long>get("parentId")).thenReturn(parentIdPath);
            lenient().when(root.<String>get("name")).thenReturn(namePath);
            lenient().when(root.<String>get("description")).thenReturn(descriptionPath);
            lenient().when(root.<Boolean>get("visible")).thenReturn(visiblePath);
            lenient().when(root.<Integer>get("sortOrder")).thenReturn(sortOrderPath);
            lenient().when(root.<LocalDateTime>get("createdAt")).thenReturn(createdAtPath);
            lenient().when(root.<LocalDateTime>get("updatedAt")).thenReturn(updatedAtPath);

            @SuppressWarnings("unchecked")
            Expression<String> lowered = mock(Expression.class);
            lenient().when(cb.equal(any(), any())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.like(any(Expression.class), anyString())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.ge(any(Expression.class), anyInt())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.le(any(Expression.class), anyInt())).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.and(any(Predicate[].class))).thenAnswer(inv -> mock(Predicate.class));
            lenient().when(cb.lower(any(Expression.class))).thenReturn(lowered);
        }
    }
}
