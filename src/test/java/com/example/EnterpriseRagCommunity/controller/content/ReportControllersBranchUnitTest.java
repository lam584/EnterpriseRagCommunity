package com.example.EnterpriseRagCommunity.controller.content;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;

@ExtendWith(MockitoExtension.class)
class ReportControllersBranchUnitTest {

    @Mock
    PortalReportsService portalReportsService;

    @Mock
    NotificationsService notificationsService;

    @Mock
    AdministratorService administratorService;

    @Mock
    AuditLogWriter auditLogWriter;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userReports_shouldCoverSuccessAndFailureBranches() {
        UserReportsController c = new UserReportsController();
        setField(c, "portalReportsService", portalReportsService);
        setField(c, "notificationsService", notificationsService);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        UserReportsController.UserReportRequest req = new UserReportsController.UserReportRequest();
        req.setReasonCode("SPAM");
        req.setReasonText("  detail  ");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u1@example.com", "p", List.of()));
        when(administratorService.findByUsername("u1@example.com")).thenReturn(Optional.of(user(101L)));
        when(portalReportsService.reportProfile(11L, "SPAM", "  detail  ")).thenReturn(new PortalReportsService.ReportSubmitResult(1L, 2L));

        Map<String, Object> ok = c.reportProfile(11L, req);
        assertEquals(1L, ok.get("reportId"));
        assertEquals(2L, ok.get("queueId"));
        verify(auditLogWriter).write(eq(101L), eq("u1@example.com"), eq("REPORT_PROFILE_CREATE"), eq("REPORT"), eq(1L), eq(AuditResult.SUCCESS), anyString(), isNull(), anyMap());
        verify(notificationsService).createNotification(eq(101L), eq("REPORT"), eq("举报提交成功"), contains("补充：detail"));

        SecurityContextHolder.clearContext();
        when(portalReportsService.reportProfile(12L, "SPAM", "  detail  ")).thenReturn(new PortalReportsService.ReportSubmitResult(3L, 4L));
        Map<String, Object> anonymousOk = c.reportProfile(12L, req);
        assertEquals(3L, anonymousOk.get("reportId"));
        verifyNoMoreInteractions(notificationsService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u2@example.com", "p", List.of()));
        when(administratorService.findByUsername("u2@example.com")).thenReturn(Optional.of(user(102L)));
        doThrow(new RuntimeException("audit-fail")).when(auditLogWriter).write(eq(102L), any(), eq("REPORT_PROFILE_CREATE"), eq("REPORT"), isNull(), eq(AuditResult.FAIL), anyString(), isNull(), anyMap());
        when(portalReportsService.reportProfile(13L, "SPAM", "  detail  ")).thenThrow(new RuntimeException("x".repeat(300)));
        assertThrows(RuntimeException.class, () -> c.reportProfile(13L, req));
        verify(notificationsService).createNotification(eq(102L), eq("REPORT"), eq("举报提交失败"), contains("失败原因"));
    }

    @Test
    void userReports_shouldCoverNullResultAndNullRequestFailure() {
        UserReportsController c = new UserReportsController();
        setField(c, "portalReportsService", portalReportsService);
        setField(c, "notificationsService", notificationsService);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u10@example.com", "p", List.of()));
        when(administratorService.findByUsername("u10@example.com")).thenReturn(Optional.of(user(1001L)));

        UserReportsController.UserReportRequest req = new UserReportsController.UserReportRequest();
        req.setReasonCode("SPAM");
        req.setReasonText("   ");
        when(portalReportsService.reportProfile(90L, "SPAM", "   ")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> c.reportProfile(90L, req));

        assertThrows(NullPointerException.class, () -> c.reportProfile(91L, null));
    }

    @Test
    void commentReports_shouldCoverSafeMsgNullAndBlankUsername() {
        CommentReportsController c = new CommentReportsController();
        setField(c, "portalReportsService", portalReportsService);
        setField(c, "notificationsService", notificationsService);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        CommentReportsController.CommentReportRequest req = new CommentReportsController.CommentReportRequest();
        req.setReasonCode("ABUSE");
        req.setReasonText(null);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("   ", "p", List.of()));
        when(administratorService.findByUsername("   ")).thenReturn(Optional.of(user(201L)));
        when(portalReportsService.reportComment(21L, "ABUSE", null)).thenReturn(new PortalReportsService.ReportSubmitResult(9L, 10L));

        Map<String, Object> ok = c.reportComment(21L, req);
        assertEquals(9L, ok.get("reportId"));
        verify(auditLogWriter).write(eq(201L), isNull(), eq("REPORT_COMMENT_CREATE"), eq("REPORT"), eq(9L), eq(AuditResult.SUCCESS), anyString(), isNull(), anyMap());

        when(portalReportsService.reportComment(22L, "ABUSE", null)).thenThrow(new RuntimeException((String) null));
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        assertThrows(RuntimeException.class, () -> c.reportComment(22L, req));
        verify(auditLogWriter).write(eq(201L), isNull(), eq("REPORT_COMMENT_CREATE"), eq("REPORT"), isNull(), eq(AuditResult.FAIL), anyString(), isNull(), detailsCaptor.capture());
        assertTrue(detailsCaptor.getValue().containsKey("message"));
        assertNull(detailsCaptor.getValue().get("message"));
    }

    @Test
    void commentReports_shouldCoverNullResultAndReqNullFailure() {
        CommentReportsController c = new CommentReportsController();
        setField(c, "portalReportsService", portalReportsService);
        setField(c, "notificationsService", notificationsService);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u20@example.com", "p", List.of()));
        when(administratorService.findByUsername("u20@example.com")).thenReturn(Optional.of(user(2001L)));

        CommentReportsController.CommentReportRequest req = new CommentReportsController.CommentReportRequest();
        req.setReasonCode("ABUSE");
        req.setReasonText("   ");
        when(portalReportsService.reportComment(190L, "ABUSE", "   ")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> c.reportComment(190L, req));

        assertThrows(NullPointerException.class, () -> c.reportComment(191L, null));
    }

    @Test
    void postReports_shouldCoverUnauthenticatedAndNotAuthenticatedBranches() {
        PostReportsController c = new PostReportsController();
        setField(c, "portalReportsService", portalReportsService);
        setField(c, "notificationsService", notificationsService);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        PostReportsController.PostReportRequest req = new PostReportsController.PostReportRequest();
        req.setReasonCode("OTHER");
        req.setReasonText(" ");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u3@example.com", "p"));
        when(portalReportsService.reportPost(31L, "OTHER", " ")).thenReturn(new PortalReportsService.ReportSubmitResult(5L, 6L));
        Map<String, Object> unauthTokenOk = c.reportPost(31L, req);
        assertEquals(5L, unauthTokenOk.get("reportId"));
        verify(auditLogWriter, never()).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationsService, never()).createNotification(anyLong(), anyString(), anyString(), anyString());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u4@example.com", "p", List.of()));
        when(administratorService.findByUsername("u4@example.com")).thenReturn(Optional.of(user(301L)));
        when(portalReportsService.reportPost(32L, "OTHER", " ")).thenReturn(new PortalReportsService.ReportSubmitResult(7L, 8L));
        Map<String, Object> success = c.reportPost(32L, req);
        assertEquals(7L, success.get("reportId"));
        verify(auditLogWriter).write(eq(301L), eq("u4@example.com"), eq("REPORT_POST_CREATE"), eq("REPORT"), eq(7L), eq(AuditResult.SUCCESS), anyString(), isNull(), anyMap());

        when(portalReportsService.reportPost(33L, "OTHER", " ")).thenThrow(new RuntimeException("failed"));
        assertThrows(RuntimeException.class, () -> c.reportPost(33L, req));
        verify(auditLogWriter).write(eq(301L), eq("u4@example.com"), eq("REPORT_POST_CREATE"), eq("REPORT"), isNull(), eq(AuditResult.FAIL), anyString(), isNull(), anyMap());
    }

    @Test
    void postReports_shouldCoverNullResultAndNullRequestFailure() {
        PostReportsController c = new PostReportsController();
        setField(c, "portalReportsService", portalReportsService);
        setField(c, "notificationsService", notificationsService);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u30@example.com", "p", List.of()));
        when(administratorService.findByUsername("u30@example.com")).thenReturn(Optional.of(user(3001L)));

        PostReportsController.PostReportRequest req = new PostReportsController.PostReportRequest();
        req.setReasonCode("OTHER");
        req.setReasonText("   ");
        when(portalReportsService.reportPost(290L, "OTHER", "   ")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> c.reportPost(290L, req));

        assertThrows(NullPointerException.class, () -> c.reportPost(291L, null));
    }

    @Test
    void reportControllers_shouldCoverRemainingConditionalBranches() {
        UserReportsController userC = new UserReportsController();
        setField(userC, "portalReportsService", portalReportsService);
        setField(userC, "notificationsService", notificationsService);
        setField(userC, "administratorService", administratorService);
        setField(userC, "auditLogWriter", auditLogWriter);

        CommentReportsController commentC = new CommentReportsController();
        setField(commentC, "portalReportsService", portalReportsService);
        setField(commentC, "notificationsService", notificationsService);
        setField(commentC, "administratorService", administratorService);
        setField(commentC, "auditLogWriter", auditLogWriter);

        PostReportsController postC = new PostReportsController();
        setField(postC, "portalReportsService", portalReportsService);
        setField(postC, "notificationsService", notificationsService);
        setField(postC, "administratorService", administratorService);
        setField(postC, "auditLogWriter", auditLogWriter);

        UserReportsController.UserReportRequest ur = new UserReportsController.UserReportRequest();
        ur.setReasonCode("R1");
        ur.setReasonText(null);
        CommentReportsController.CommentReportRequest cr = new CommentReportsController.CommentReportRequest();
        cr.setReasonCode("R2");
        cr.setReasonText("x");
        PostReportsController.PostReportRequest pr = new PostReportsController.PostReportRequest();
        pr.setReasonCode("R3");
        pr.setReasonText(null);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("nouser@example.com", "p", List.of()));
        when(administratorService.findByUsername("nouser@example.com")).thenReturn(Optional.empty());
        when(portalReportsService.reportProfile(301L, "R1", null)).thenReturn(new PortalReportsService.ReportSubmitResult(1L, 1L));
        when(portalReportsService.reportComment(302L, "R2", "x")).thenReturn(new PortalReportsService.ReportSubmitResult(2L, 2L));
        when(portalReportsService.reportPost(303L, "R3", null)).thenReturn(new PortalReportsService.ReportSubmitResult(3L, 3L));
        assertEquals(1L, userC.reportProfile(301L, ur).get("reportId"));
        assertEquals(2L, commentC.reportComment(302L, cr).get("reportId"));
        assertEquals(3L, postC.reportPost(303L, pr).get("reportId"));

        when(portalReportsService.reportProfile(311L, "R1", null)).thenThrow(new RuntimeException("u"));
        when(portalReportsService.reportComment(312L, "R2", "x")).thenThrow(new RuntimeException("c"));
        when(portalReportsService.reportPost(313L, "R3", null)).thenThrow(new RuntimeException("p"));
        assertThrows(RuntimeException.class, () -> userC.reportProfile(311L, ur));
        assertThrows(RuntimeException.class, () -> commentC.reportComment(312L, cr));
        assertThrows(RuntimeException.class, () -> postC.reportPost(313L, pr));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("hasuser@example.com", "p", List.of()));
        when(administratorService.findByUsername("hasuser@example.com")).thenReturn(Optional.of(user(7001L)));
        when(portalReportsService.reportProfile(321L, "R1", null)).thenThrow(new RuntimeException((String) null));
        when(portalReportsService.reportPost(322L, "R3", null)).thenThrow(new RuntimeException((String) null));
        assertThrows(RuntimeException.class, () -> userC.reportProfile(321L, ur));
        assertThrows(RuntimeException.class, () -> postC.reportPost(322L, pr));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("full@example.com", "p", List.of()));
        when(administratorService.findByUsername("full@example.com")).thenReturn(Optional.of(user(8001L)));
        UserReportsController.UserReportRequest urBlank = new UserReportsController.UserReportRequest();
        urBlank.setReasonCode("R1");
        urBlank.setReasonText("   ");
        when(portalReportsService.reportProfile(331L, "R1", null)).thenReturn(new PortalReportsService.ReportSubmitResult(11L, 11L));
        when(portalReportsService.reportProfile(332L, "R1", "   ")).thenReturn(new PortalReportsService.ReportSubmitResult(12L, 12L));
        assertEquals(11L, userC.reportProfile(331L, ur).get("reportId"));
        assertEquals(12L, userC.reportProfile(332L, urBlank).get("reportId"));

        CommentReportsController.CommentReportRequest crBlank = new CommentReportsController.CommentReportRequest();
        crBlank.setReasonCode("R2");
        crBlank.setReasonText("   ");
        when(portalReportsService.reportComment(333L, "R2", "   ")).thenReturn(new PortalReportsService.ReportSubmitResult(13L, 13L));
        assertEquals(13L, commentC.reportComment(333L, crBlank).get("reportId"));

        PostReportsController.PostReportRequest prNonBlank = new PostReportsController.PostReportRequest();
        prNonBlank.setReasonCode("R3");
        prNonBlank.setReasonText("abc");
        when(portalReportsService.reportPost(334L, "R3", null)).thenReturn(new PortalReportsService.ReportSubmitResult(14L, 14L));
        when(portalReportsService.reportPost(335L, "R3", "abc")).thenReturn(new PortalReportsService.ReportSubmitResult(15L, 15L));
        assertEquals(14L, postC.reportPost(334L, pr).get("reportId"));
        assertEquals(15L, postC.reportPost(335L, prNonBlank).get("reportId"));
    }

    @Test
    void helpers_shouldCoverPrivateBranchesAcrossReportControllers() {
        UserReportsController userC = new UserReportsController();
        PostReportsController postC = new PostReportsController();
        CommentReportsController commentC = new CommentReportsController();
        setField(userC, "administratorService", administratorService);
        setField(postC, "administratorService", administratorService);
        setField(commentC, "administratorService", administratorService);

        SecurityContextHolder.clearContext();
        assertNull(invoke(userC, "currentUserIdOrNull"));
        assertNull(invoke(postC, "currentUserIdOrNull"));
        assertNull(invoke(commentC, "currentUserIdOrNull"));
        assertNull(invokeStatic(UserReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(CommentReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));

        Authentication notAuth = new UsernamePasswordAuthenticationToken("u@example.com", "p");
        SecurityContextHolder.getContext().setAuthentication(notAuth);
        assertNull(invoke(userC, "currentUserIdOrNull"));
        assertNull(invoke(commentC, "currentUserIdOrNull"));
        assertNull(invoke(postC, "currentUserIdOrNull"));
        assertNull(invokeStatic(UserReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(CommentReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(PostReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "p", List.of()));
        assertNull(invoke(postC, "currentUserIdOrNull"));
        assertNull(invoke(userC, "currentUserIdOrNull"));
        assertNull(invoke(commentC, "currentUserIdOrNull"));
        assertNull(invokeStatic(PostReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(UserReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(CommentReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("  ", "p", List.of()));
        assertNull(invokeStatic(UserReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("nobody@example.com", "p", List.of()));
        when(administratorService.findByUsername("nobody@example.com")).thenReturn(Optional.empty());
        assertNull(invoke(commentC, "currentUserIdOrNull"));

        Authentication authNameNull = org.mockito.Mockito.mock(Authentication.class);
        when(authNameNull.isAuthenticated()).thenReturn(true);
        when(authNameNull.getPrincipal()).thenReturn("principal");
        when(authNameNull.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authNameNull);
        assertNull(invokeStatic(UserReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(CommentReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));
        assertNull(invokeStatic(PostReportsController.class, "currentUsernameOrNull", new Class<?>[]{}));

        assertNull(invokeStatic(UserReportsController.class, "safeMsg", new Class<?>[]{String.class}, (Object) null));
        assertEquals("x", invokeStatic(UserReportsController.class, "safeMsg", new Class<?>[]{String.class}, " x "));
        assertEquals(256, ((String) invokeStatic(UserReportsController.class, "safeMsg", new Class<?>[]{String.class}, "a".repeat(300))).length());
        assertNull(invokeStatic(CommentReportsController.class, "safeMsg", new Class<?>[]{String.class}, (Object) null));
        assertEquals("y", invokeStatic(CommentReportsController.class, "safeMsg", new Class<?>[]{String.class}, " y "));
        assertEquals(256, ((String) invokeStatic(CommentReportsController.class, "safeMsg", new Class<?>[]{String.class}, "b".repeat(300))).length());
        assertNull(invokeStatic(PostReportsController.class, "safeMsg", new Class<?>[]{String.class}, (Object) null));
        assertEquals("z", invokeStatic(PostReportsController.class, "safeMsg", new Class<?>[]{String.class}, " z "));
        assertEquals(256, ((String) invokeStatic(PostReportsController.class, "safeMsg", new Class<?>[]{String.class}, "c".repeat(300))).length());
    }

    private static UsersEntity user(Long id) {
        UsersEntity user = new UsersEntity();
        user.setId(id);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invokeStatic(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
