package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.PaymentBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 该接口用于定义付款账单实体的数据库操作
@Repository
public interface PaymentBillRepository extends JpaRepository<PaymentBill, Long> {
    // 根据读者 ID 查找付款账单
    List<PaymentBill> findByReaderId(Long readerId);

    // 根据管理员 ID 查找付款账单
    List<PaymentBill> findByAdminId(Long adminId);

    // 查找在指定日期范围内的付款账单
    List<PaymentBill> findByPaymentDateBetween(LocalDateTime startDate, LocalDateTime endDate);
}
