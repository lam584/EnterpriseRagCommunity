package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "book_shelves")
public class BookShelf {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 书架编码 */
    @Column(name = "shelf_code", nullable = false, unique = true, length = 255)
    private String shelfCode;

    /** 书架位置描述 */
    @Column(name = "location_description", nullable = false, length = 255)
    private String locationDescription;

    /** 容量 */
    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}