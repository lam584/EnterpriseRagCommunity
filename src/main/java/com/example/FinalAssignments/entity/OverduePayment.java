package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "overdue_payments")
public class OverduePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 读者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reader_id", nullable = false)
    private Reader reader;

    /** 借阅订单 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_order_id", nullable = false)
    private BookLoan loanOrder;

    @Column(name = "overdue_days", nullable = false)
    private Integer overdueDays;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "is_cleared", nullable = false)
    private Boolean isCleared;

    /** 支付账单 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_bill_id")
    private PaymentBill paymentBill;

    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount;

    @Column(name = "repaid_date")
    private LocalDateTime repaidDate;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}