package com.example.FinalAssignments.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Pattern(regexp = "\\d{13}", message = "ISBN必须为13位数字")
    @Column(nullable = false, length = 100)
    private String isbn;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String author;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String publisher;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String edition;

    /** 定价 */
    @NotNull
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** 分类 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BookCategory category;

    /** 书架 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shelves_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BookShelf shelf;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(name = "print_times", nullable = false, length = 255)
    private String printTimes;

    /** 操作管理员 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrator_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "permission"})
    private Administrator administrator;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

