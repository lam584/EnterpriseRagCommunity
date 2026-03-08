package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @GetMapping
    public ResponseEntity<Page<NotificationsEntity>> listMyNotifications(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "unreadOnly", required = false) Boolean unreadOnly,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<NotificationsEntity> result = notificationsService.listMyNotifications(
                Optional.ofNullable(type),
                Optional.ofNullable(unreadOnly),
                pageable
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        long count = notificationsService.countMyUnread();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationsEntity> markRead(@PathVariable("id") Long id, Authentication authentication, HttpServletRequest request) {
        Long userId = currentUserIdOrNull(authentication, request);
        String actorName = currentUsernameOrNull(authentication, request);
        try {
            NotificationsEntity out = notificationsService.markMyNotificationRead(id);
            if (userId != null) {
                auditLogWriter.write(
                        userId,
                        actorName,
                        "NOTIFICATION_MARK_READ",
                        "NOTIFICATION",
                        id,
                        AuditResult.SUCCESS,
                        "标记通知已读",
                        null,
                        Map.of("read", true)
                );
            }
            return ResponseEntity.ok(out);
        } catch (RuntimeException ex) {
            if (userId != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("read", true);
                details.put("error", ex.getClass().getName());
                details.put("message", safeMsg(ex.getMessage()));
                try {
                    auditLogWriter.write(
                            userId,
                            actorName,
                            "NOTIFICATION_MARK_READ",
                            "NOTIFICATION",
                            id,
                            AuditResult.FAIL,
                            "标记通知已读失败",
                            null,
                            details
                    );
                } catch (Exception ignore) {
                }
            }
            throw ex;
        }
    }

    @PatchMapping("/read")
    public ResponseEntity<Map<String, Integer>> markReadBatch(@RequestBody Map<String, Object> body, Authentication authentication, HttpServletRequest request) {
        Object idsObj = body == null ? null : body.get("ids");
        if (!(idsObj instanceof List<?> list)) {
            return ResponseEntity.badRequest().body(Map.of("updated", 0));
        }
        List<Long> ids = list.stream().filter(x -> x instanceof Number).map(x -> ((Number) x).longValue()).toList();
        Long userId = currentUserIdOrNull(authentication, request);
        String actorName = currentUsernameOrNull(authentication, request);
        try {
            int updated = notificationsService.markMyNotificationsRead(ids);
            if (userId != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("count", ids.size());
                details.put("updated", updated);
                details.put("idsSample", ids.stream().limit(20).toList());
                auditLogWriter.write(
                        userId,
                        actorName,
                        "NOTIFICATION_MARK_READ_BATCH",
                        "NOTIFICATION",
                        null,
                        AuditResult.SUCCESS,
                        "批量标记通知已读",
                        null,
                        details
                );
            }
            return ResponseEntity.ok(Map.of("updated", updated));
        } catch (RuntimeException ex) {
            if (userId != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("count", ids.size());
                details.put("idsSample", ids.stream().limit(20).toList());
                details.put("error", ex.getClass().getName());
                details.put("message", safeMsg(ex.getMessage()));
                try {
                    auditLogWriter.write(
                            userId,
                            actorName,
                            "NOTIFICATION_MARK_READ_BATCH",
                            "NOTIFICATION",
                            null,
                            AuditResult.FAIL,
                            "批量标记通知已读失败",
                            null,
                            details
                    );
                } catch (Exception ignore) {
                }
            }
            throw ex;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id, Authentication authentication, HttpServletRequest request) {
        Long userId = currentUserIdOrNull(authentication, request);
        String actorName = currentUsernameOrNull(authentication, request);
        try {
            notificationsService.deleteMyNotification(id);
            if (userId != null) {
                auditLogWriter.write(
                        userId,
                        actorName,
                        "NOTIFICATION_DELETE",
                        "NOTIFICATION",
                        id,
                        AuditResult.SUCCESS,
                        "删除通知",
                        null,
                        Map.of()
                );
            }
            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            if (userId != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("error", ex.getClass().getName());
                details.put("message", safeMsg(ex.getMessage()));
                try {
                    auditLogWriter.write(
                            userId,
                            actorName,
                            "NOTIFICATION_DELETE",
                            "NOTIFICATION",
                            id,
                            AuditResult.FAIL,
                            "删除通知失败",
                            null,
                            details
                    );
                } catch (Exception ignore) {
                }
            }
            throw ex;
        }
    }

    ResponseEntity<NotificationsEntity> markRead(Long id) {
        return markRead(id, null, null);
    }

    ResponseEntity<Map<String, Integer>> markReadBatch(Map<String, Object> body) {
        return markReadBatch(body, null, null);
    }

    ResponseEntity<?> delete(Long id) {
        return delete(id, null, null);
    }

    private Long currentUserIdOrNull(Authentication authentication, HttpServletRequest request) {
        String email = currentUsernameOrNull(authentication, request);
        if (email == null) {
            return null;
        }
        return administratorService.findByUsername(email).map(UsersEntity::getId).orElse(null);
    }

    private static String currentUsernameOrNull(Authentication authentication, HttpServletRequest request) {
        Authentication auth = authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        }
        if (request != null && request.getUserPrincipal() != null) {
            String name = request.getUserPrincipal().getName();
            return name == null || name.isBlank() ? null : name.trim();
        }
        if (request != null && request.getSession(false) != null) {
            Object securityContext = request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
            if (securityContext instanceof org.springframework.security.core.context.SecurityContext context) {
                Authentication sessionAuth = context.getAuthentication();
                if (sessionAuth != null && sessionAuth.isAuthenticated() && !"anonymousUser".equals(sessionAuth.getPrincipal())) {
                    String name = sessionAuth.getName();
                    return name == null || name.isBlank() ? null : name.trim();
                }
            }
        }
        return null;
    }

    private static String safeMsg(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= 256) return t;
        return t.substring(0, 256);
    }
}

