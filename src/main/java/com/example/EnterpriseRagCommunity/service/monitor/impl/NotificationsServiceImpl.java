package com.example.EnterpriseRagCommunity.service.monitor.impl;

import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.NotificationsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationsServiceImpl implements NotificationsService {

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    @Override
    public Page<NotificationsEntity> listMyNotifications(Optional<String> type, Optional<Boolean> unreadOnly, Pageable pageable) {
        Long me = currentUserIdOrThrow();

        Specification<NotificationsEntity> spec = (root, _query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("userId"), me));
            type.filter(t -> !t.isBlank()).ifPresent(t -> ps.add(cb.equal(root.get("type"), t)));
            unreadOnly.ifPresent(u -> {
                if (u) ps.add(cb.isNull(root.get("readAt")));
            });
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
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        Long me = currentUserIdOrThrow();

        NotificationsEntity e = notificationsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (!me.equals(e.getUserId())) throw new org.springframework.security.access.AccessDeniedException("无权限");

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
    public NotificationsEntity deleteMyNotification(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        Long me = currentUserIdOrThrow();

        NotificationsEntity e = notificationsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (!me.equals(e.getUserId())) throw new org.springframework.security.access.AccessDeniedException("无权限");

        notificationsRepository.delete(e);
        return e;
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
