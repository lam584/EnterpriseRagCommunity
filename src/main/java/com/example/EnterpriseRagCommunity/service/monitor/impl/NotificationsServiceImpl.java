package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.NotificationsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationsServiceImpl implements NotificationsService {
    private final NotificationsRepository notificationsRepository;
    private final AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
                },
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    private NotificationsEntity getOwnedNotificationOrThrow(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        Long me = currentUserIdOrThrow();
        NotificationsEntity e = notificationsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (!me.equals(e.getUserId())) throw new org.springframework.security.access.AccessDeniedException("无权限");
        return e;
    }

    @Override
    public Page<NotificationsEntity> listMyNotifications(String type, Boolean unreadOnly, Pageable pageable) {
        Long me = currentUserIdOrThrow();

        Specification<NotificationsEntity> spec = (root, _query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("userId"), me));
            if (type != null && !type.isBlank()) {
                ps.add(cb.equal(root.get("type"), type));
            }
            if (Boolean.TRUE.equals(unreadOnly)) {
                ps.add(cb.isNull(root.get("readAt")));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        return notificationsRepository.findAll(spec, pageable);
    }

    @Override
    public long countMyUnread() {
        Long me = currentUserIdOrThrow();
        return notificationsRepository.countByUserIdAndReadAtIsNull(me);
    }

    @Override
    @Transactional
    public NotificationsEntity markMyNotificationRead(Long id) {
        NotificationsEntity e = getOwnedNotificationOrThrow(id);

        if (e.getReadAt() == null) {
            e.setReadAt(LocalDateTime.now());
            notificationsRepository.save(e);
        }
        return e;
    }

    @Override
    @Transactional
    public int markMyNotificationsRead(List<Long> ids) {
        Long me = currentUserIdOrThrow();
        if (ids == null || ids.isEmpty()) return 0;

        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            if (id == null) continue;
            NotificationsEntity e = notificationsRepository.findById(id).orElse(null);
            if (e == null) continue;
            if (!me.equals(e.getUserId())) continue;
            if (e.getReadAt() != null) continue;
            e.setReadAt(now);
            notificationsRepository.save(e);
            updated++;
        }
        return updated;
    }

    @Override
    @Transactional
    public void deleteMyNotification(Long id) {
        NotificationsEntity e = getOwnedNotificationOrThrow(id);
        notificationsRepository.delete(e);
    }

    @Override
    @Transactional
    public NotificationsEntity createNotification(Long userId, String type, String title, String content) {
        if (userId == null) throw new IllegalArgumentException("userId 不能为空");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type 不能为空");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title 不能为空");

        NotificationsEntity e = new NotificationsEntity();
        e.setUserId(userId);
        e.setType(type);
        e.setTitle(title);
        e.setContent(content);
        e.setReadAt(null);
        e.setCreatedAt(LocalDateTime.now());
        return notificationsRepository.save(e);
    }
}
