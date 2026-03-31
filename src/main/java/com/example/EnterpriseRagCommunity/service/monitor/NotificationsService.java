package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 站内通知服务：面向当前登录用户。
 */
public interface NotificationsService {

    Page<NotificationsEntity> listMyNotifications(Optional<String> type, Optional<Boolean> unreadOnly, Pageable pageable);

    long countMyUnread();

    NotificationsEntity markMyNotificationRead(Long id);

    int markMyNotificationsRead(List<Long> ids);

    void deleteMyNotification(Long id);

    NotificationsEntity createNotification(Long userId, String type, String title, String content);
}

