package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.BoardRolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.BoardRolePermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoardRolePermissionsRepository extends JpaRepository<BoardRolePermissionsEntity, BoardRolePermissionsEntity.BoardRolePermissionsPk> {

    @Query("select r.roleId from BoardRolePermissionsEntity r where r.boardId = :boardId and r.perm = :perm")
    List<Long> findRoleIdsByBoardIdAndPerm(@Param("boardId") Long boardId, @Param("perm") BoardRolePermissionType perm);

    void deleteByBoardId(Long boardId);
}
