package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payment_bills")
public class PaymentBill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 支付金额 */
    @Column(name = "amount_paid", nullable = false, precision = 8, scale = 2)
    private BigDecimal amountPaid;

    /** 实际应付 */
    @Column(name = "total_paid", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalPaid;

    /** 找零 */
    @Column(name = "change_given", nullable = false, precision = 8, scale = 2)
    private BigDecimal changeGiven;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    /** 管理员 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Administrator admin;

    /** 读者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reader_id", nullable = false)
    private Reader reader;

    /** 备注 */
    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}