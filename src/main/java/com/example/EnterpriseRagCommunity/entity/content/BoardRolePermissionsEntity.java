package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.BoardRolePermissionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Entity
@Table(name = "board_role_permissions")
@IdClass(BoardRolePermissionsEntity.BoardRolePermissionsPk.class)
public class BoardRolePermissionsEntity {

    @Data
    @NoArgsConstructor
    public static class BoardRolePermissionsPk implements Serializable {
        private Long boardId;
        private Long roleId;
        private BoardRolePermissionType perm;
    }

    @Id
    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "perm", nullable = false)
    private BoardRolePermissionType perm;
}
