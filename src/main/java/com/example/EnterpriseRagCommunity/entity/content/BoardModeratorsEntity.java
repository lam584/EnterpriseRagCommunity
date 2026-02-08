package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Entity
@Table(name = "board_moderators")
@IdClass(BoardModeratorsEntity.BoardModeratorsPk.class)
public class BoardModeratorsEntity {

    @Data
    @NoArgsConstructor
    public static class BoardModeratorsPk implements Serializable {
        private Long boardId;
        private Long userId;
    }

    @Id
    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
