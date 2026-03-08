package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplBuildReporterTrustAggTest {

    @Test
    void buildReporterTrustAgg_nullOrEmpty_returnsNaN() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        double v0 = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", new Object[]{null});
        double v1 = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of());
        assertTrue(Double.isNaN(v0));
        assertTrue(Double.isNaN(v1));
    }

    @Test
    void buildReporterTrustAgg_allIgnored_returnsNaN() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        ReportsEntity noReporter = new ReportsEntity();
        double v = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", Arrays.asList(null, noReporter));
        assertTrue(Double.isNaN(v));
    }

    @Test
    void buildReporterTrustAgg_usesDefaultWhenUserMissingOrMetadataNull() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        ReportsEntity r1 = new ReportsEntity();
        r1.setReporterId(1L);
        ReportsEntity r2 = new ReportsEntity();
        r2.setReporterId(2L);

        when(usersRepository.findById(1L)).thenReturn(Optional.empty());
        UsersEntity u2 = new UsersEntity();
        u2.setId(2L);
        u2.setMetadata(null);
        when(usersRepository.findById(2L)).thenReturn(Optional.of(u2));

        double v = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of(r1, r2));
        assertEquals(0.5D, v, 1e-9);
    }

    @Test
    void buildReporterTrustAgg_clampsTrustScoreAndIgnoresNonNumber() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        ReportsEntity r1 = new ReportsEntity();
        r1.setReporterId(1L);
        ReportsEntity r2 = new ReportsEntity();
        r2.setReporterId(2L);
        ReportsEntity r3 = new ReportsEntity();
        r3.setReporterId(3L);

        UsersEntity u1 = new UsersEntity();
        u1.setId(1L);
        u1.setMetadata(Map.of("trust_score", 2.0));
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u1));

        UsersEntity u2 = new UsersEntity();
        u2.setId(2L);
        u2.setMetadata(Map.of("trust_score", -1));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(u2));

        UsersEntity u3 = new UsersEntity();
        u3.setId(3L);
        u3.setMetadata(Map.of("trust_score", "0.9"));
        when(usersRepository.findById(3L)).thenReturn(Optional.of(u3));

        double v = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of(r1, r2, r3));
        assertEquals((1.0 + 0.0 + 0.5) / 3.0, v, 1e-9);
    }

    @Test
    void buildReporterTrustAgg_usesCachePerReporter() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        ReportsEntity r1 = new ReportsEntity();
        r1.setReporterId(1L);
        ReportsEntity r2 = new ReportsEntity();
        r2.setReporterId(1L);

        UsersEntity u1 = new UsersEntity();
        u1.setId(1L);
        u1.setMetadata(Map.of("trust_score", 0.7));
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u1));

        double v = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of(r1, r2));
        assertEquals(0.7, v, 1e-9);
        verify(usersRepository, times(1)).findById(eq(1L));
    }

    @Test
    void buildReporterTrustAgg_userRepoThrows_isSwallowed() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        ReportsEntity r1 = new ReportsEntity();
        r1.setReporterId(1L);
        when(usersRepository.findById(1L)).thenThrow(new RuntimeException("boom"));

        double v = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of(r1));
        assertEquals(0.5, v, 1e-9);
    }
}
