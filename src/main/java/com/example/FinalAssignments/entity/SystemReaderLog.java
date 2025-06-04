package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "system_readers_logs",
        indexes = @Index(name = "idx_reader_logs_level_time", columnList = "level, created_at")
)
public class SystemReaderLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String level;

    @Column(nullable = false, length = 255)
    private String message;

    @Lob
    @Column(nullable = false)
    private String context;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "readers_agent", nullable = false, length = 255)
    private String readersAgent;

    /** 读者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "readers_id", nullable = false)
    private Reader reader;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}