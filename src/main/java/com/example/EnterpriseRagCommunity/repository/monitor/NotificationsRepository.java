package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationsRepository extends JpaRepository<NotificationsEntity, Long>, JpaSpecificationExecutor<NotificationsEntity> {

    // 查询指定用户的未读消息（read_at IS NULL）
    List<NotificationsEntity> findByUserIdAndReadAtIsNull(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);
}
