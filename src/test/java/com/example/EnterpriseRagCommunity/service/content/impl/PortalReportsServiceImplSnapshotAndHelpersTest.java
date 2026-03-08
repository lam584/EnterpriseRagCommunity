package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplSnapshotAndHelpersTest {

    @Test
    void snapshotProfileFields_userNull_returnsEmpty() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        Map<String, Object> out = ReflectionTestUtils.invokeMethod(svc, "snapshotProfileFields", new Object[]{null});
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void snapshotProfileFields_metadataNull_returnsUserBasicsOnly() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setUsername("alice");
        u.setMetadata(null);

        Map<String, Object> out = ReflectionTestUtils.invokeMethod(svc, "snapshotProfileFields", u);

        assertEquals(1L, out.get("user_id"));
        assertEquals("alice", out.get("username"));
        assertFalse(out.containsKey("avatarUrl"));
    }

    @Test
    void snapshotProfileFields_profileNotMap_ignoresProfile() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setUsername("alice");
        u.setMetadata(Map.of("profile", "not-map"));

        Map<String, Object> out = ReflectionTestUtils.invokeMethod(svc, "snapshotProfileFields", u);

        assertEquals(1L, out.get("user_id"));
        assertEquals("alice", out.get("username"));
        assertFalse(out.containsKey("bio"));
    }

    @Test
    void snapshotProfileFields_includesOnlyNonNullProfileFields() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setUsername("alice");
        Map<String, Object> profile = new HashMap<>();
        profile.put("avatarUrl", "http://a");
        profile.put("bio", null);
        profile.put("location", "c");
        profile.put("website", null);
        u.setMetadata(Map.of("profile", profile));

        Map<String, Object> out = ReflectionTestUtils.invokeMethod(svc, "snapshotProfileFields", u);

        assertEquals(1L, out.get("user_id"));
        assertEquals("alice", out.get("username"));
        assertEquals("http://a", out.get("avatarUrl"));
        assertEquals("c", out.get("location"));
        assertFalse(out.containsKey("bio"));
        assertFalse(out.containsKey("website"));
    }

    @Test
    void tryWriteReportSnapshot_returnsEarlyWhenIdsMissing() {
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);

        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", new Object[]{null, null, null});
        verify(moderationActionsRepository, never()).save(any());

        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", new ModerationQueueEntity(), new ReportsEntity(), Map.of());
        verify(moderationActionsRepository, never()).save(any());

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(1L);
        ReportsEntity rep = new ReportsEntity();
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q, rep, Map.of());
        verify(moderationActionsRepository, never()).save(any());
    }

    @Test
    void tryWriteReportSnapshot_writesSnapshotAndOmitsTargetSnapshotWhenNull() {
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(1L);
        ReportsEntity rep = new ReportsEntity();
        rep.setId(2L);
        rep.setReporterId(3L);
        rep.setTargetType(ReportTargetType.PROFILE);
        rep.setTargetId(4L);
        rep.setCreatedAt(LocalDateTime.of(2026, 3, 2, 10, 0));

        when(moderationActionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q, rep, null);

        ArgumentCaptor<ModerationActionsEntity> cap = ArgumentCaptor.forClass(ModerationActionsEntity.class);
        verify(moderationActionsRepository).save(cap.capture());

        ModerationActionsEntity a = cap.getValue();
        assertEquals(1L, a.getQueueId());
        assertEquals(3L, a.getActorUserId());
        assertEquals(ActionType.NOTE, a.getAction());
        assertEquals("REPORT_SNAPSHOT", a.getReason());
        assertNotNull(a.getSnapshot());
        assertTrue(String.valueOf(a.getSnapshot().get("content_snapshot_id")).contains(":at:"));
        assertFalse(a.getSnapshot().containsKey("target_snapshot"));
    }

    @Test
    void tryWriteReportSnapshot_writesSnapshotAndOmitsTargetSnapshotWhenEmpty() {
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(1L);
        ReportsEntity rep = new ReportsEntity();
        rep.setId(2L);
        rep.setReporterId(3L);
        rep.setTargetType(ReportTargetType.PROFILE);
        rep.setTargetId(4L);
        rep.setCreatedAt(LocalDateTime.of(2026, 3, 2, 10, 0));

        when(moderationActionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q, rep, Map.of());

        ArgumentCaptor<ModerationActionsEntity> cap = ArgumentCaptor.forClass(ModerationActionsEntity.class);
        verify(moderationActionsRepository).save(cap.capture());
        assertFalse(cap.getValue().getSnapshot().containsKey("target_snapshot"));
    }

    @Test
    void tryWriteReportSnapshot_targetTypeNullAndCreatedAtNull_areAllowed() {
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(1L);
        ReportsEntity rep = new ReportsEntity();
        rep.setId(2L);
        rep.setReporterId(3L);
        rep.setTargetType(null);
        rep.setTargetId(4L);
        rep.setCreatedAt(null);

        when(moderationActionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q, rep, Map.of("k", "v"));

        ArgumentCaptor<ModerationActionsEntity> cap = ArgumentCaptor.forClass(ModerationActionsEntity.class);
        verify(moderationActionsRepository).save(cap.capture());
        ModerationActionsEntity a = cap.getValue();
        assertEquals("report:2", a.getSnapshot().get("content_snapshot_id"));
        assertNull(a.getSnapshot().get("target_type"));
        assertEquals(Map.of("k", "v"), a.getSnapshot().get("target_snapshot"));
    }

    @Test
    void tryWriteReportSnapshot_saveThrows_isSwallowed() {
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(1L);
        ReportsEntity rep = new ReportsEntity();
        rep.setId(2L);
        rep.setReporterId(3L);

        when(moderationActionsRepository.save(any())).thenThrow(new RuntimeException("boom"));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q, rep, new LinkedHashMap<>());
    }

    @Test
    void normalizeReasonCode_blank_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonCode", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonCode", new Object[]{null}));
    }

    @Test
    void normalizeReasonCode_uppercasesAndTruncates() {
        String out = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonCode", "a".repeat(100));
        assertEquals(64, out.length());
        assertEquals(out, out.toUpperCase(java.util.Locale.ROOT));
    }

    @Test
    void normalizeReasonText_nullAndBlankReturnNull() {
        String out0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonText", new Object[]{null});
        assertNull(out0);
        String out1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonText", "   ");
        assertNull(out1);
    }

    @Test
    void normalizeReasonText_trimsAndTruncates() {
        String out = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonText", " " + "a".repeat(300) + " ");
        assertEquals(255, out.length());
    }

    @Test
    void helperMethods_asIntOrDefault_and_castToStringKeyMap_coverBranches() {
        Integer i0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "asIntOrDefault", "123", 7);
        assertEquals(123, i0);
        Integer i1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "asIntOrDefault", "abc", 7);
        assertEquals(7, i1);

        Map<String, Object> m0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "castToStringKeyMap", "not-map");
        assertNull(m0);

        LinkedHashMap<Object, Object> raw = new LinkedHashMap<>();
        raw.put(null, 1);
        raw.put(2, 3);
        Map<String, Object> m1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "castToStringKeyMap", raw);
        assertEquals(1, m1.size());
        assertEquals(3, m1.get("2"));
    }
}
