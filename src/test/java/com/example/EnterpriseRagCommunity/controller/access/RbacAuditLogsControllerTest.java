package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.RbacAuditQueryDTO;
import com.example.EnterpriseRagCommunity.entity.access.RbacAuditLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.RbacAuditLogsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacAuditLogsControllerTest {

    @Test
    void query_should_normalize_page_and_size_and_build_empty_spec() {
        RbacAuditLogsRepository repo = mock(RbacAuditLogsRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RbacAuditLogsController controller = new RbacAuditLogsController(repo);

        RbacAuditQueryDTO q = new RbacAuditQueryDTO();
        q.setPageNum(0);
        q.setPageSize(-1);
        controller.query(q);

        var captorSpec = org.mockito.ArgumentCaptor.forClass(Specification.class);
        var captorPageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(captorSpec.capture(), captorPageable.capture());

        assertThat(captorPageable.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captorPageable.getValue().getPageSize()).isEqualTo(20);

        Specification<RbacAuditLogsEntity> spec = captorSpec.getValue();
        Root<RbacAuditLogsEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate andPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Predicate out = spec.toPredicate(root, query, cb);
        assertThat(out).isSameAs(andPredicate);
    }

    @Test
    void query_should_add_predicates_for_all_filters() {
        RbacAuditLogsRepository repo = mock(RbacAuditLogsRepository.class);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RbacAuditLogsController controller = new RbacAuditLogsController(repo);

        RbacAuditQueryDTO q = new RbacAuditQueryDTO();
        q.setPageNum(2);
        q.setPageSize(5);
        q.setActorUserId(10L);
        q.setAction(" GRANT ");
        q.setTargetType(" ROLE ");
        q.setTargetId(" abc ");
        q.setCreatedAfter(LocalDateTime.now().minusDays(1));
        q.setCreatedBefore(LocalDateTime.now());

        controller.query(q);

        var captorSpec = org.mockito.ArgumentCaptor.forClass(Specification.class);
        var captorPageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(captorSpec.capture(), captorPageable.capture());

        assertThat(captorPageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captorPageable.getValue().getPageSize()).isEqualTo(5);

        Specification<RbacAuditLogsEntity> spec = captorSpec.getValue();
        Root<RbacAuditLogsEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        @SuppressWarnings("unchecked")
        Path<Object> anyPath = mock(Path.class);
        when(root.get(anyString())).thenReturn(anyPath);

        Predicate p = mock(Predicate.class);
        when(cb.equal(any(), any())).thenReturn(p);
        when(cb.like(any(), anyString())).thenReturn(p);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(p);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(p);
        when(cb.and(any(Predicate[].class))).thenReturn(p);

        Predicate out = spec.toPredicate(root, query, cb);
        assertThat(out).isSameAs(p);
    }
}

