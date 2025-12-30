package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsController {

    @Autowired
    private NotificationsService notificationsService;

    @GetMapping
    public ResponseEntity<Page<NotificationsEntity>> listMyNotifications(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
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
    public ResponseEntity<NotificationsEntity> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationsService.markMyNotificationRead(id));
    }

    @PatchMapping("/read")
    public ResponseEntity<Map<String, Integer>> markReadBatch(@RequestBody Map<String, Object> body) {
        Object idsObj = body == null ? null : body.get("ids");
        if (!(idsObj instanceof List<?> list)) {
            return ResponseEntity.badRequest().body(Map.of("updated", 0));
        }
        List<Long> ids = list.stream().filter(x -> x instanceof Number).map(x -> ((Number) x).longValue()).toList();
        int updated = notificationsService.markMyNotificationsRead(ids);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        notificationsService.deleteMyNotification(id);
        return ResponseEntity.noContent().build();
    }
}

