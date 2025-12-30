package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QaSessionsRepository extends JpaRepository<QaSessionsEntity, Long>, JpaSpecificationExecutor<QaSessionsEntity> {
    Page<QaSessionsEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<QaSessionsEntity> findByIdAndUserId(Long id, Long userId);

    @Query(value = "SELECT * FROM qa_sessions s WHERE s.user_id = :userId AND s.is_active = 1 AND MATCH(s.title) AGAINST(:q IN BOOLEAN MODE)", nativeQuery = true)
    Page<QaSessionsEntity> searchByTitleFulltext(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);
}
