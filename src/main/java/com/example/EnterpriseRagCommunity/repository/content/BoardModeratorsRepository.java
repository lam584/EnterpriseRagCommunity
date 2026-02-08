package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.BoardModeratorsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoardModeratorsRepository extends JpaRepository<BoardModeratorsEntity, BoardModeratorsEntity.BoardModeratorsPk> {

    @Query("select m.userId from BoardModeratorsEntity m where m.boardId = :boardId")
    List<Long> findUserIdsByBoardId(@Param("boardId") Long boardId);

    @Query("select m.boardId from BoardModeratorsEntity m where m.userId = :userId")
    List<Long> findBoardIdsByUserId(@Param("userId") Long userId);

    boolean existsByBoardIdAndUserId(Long boardId, Long userId);

    void deleteByBoardId(Long boardId);
}
