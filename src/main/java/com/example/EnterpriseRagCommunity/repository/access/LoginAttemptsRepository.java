package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.LoginAttemptsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginAttemptsRepository extends JpaRepository<LoginAttemptsEntity, Long>, JpaSpecificationExecutor<LoginAttemptsEntity> {
    List<LoginAttemptsEntity> findByUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);
    List<LoginAttemptsEntity> findByIpOrderByOccurredAtDesc(String ip, Pageable pageable);
}
