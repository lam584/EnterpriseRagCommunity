package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.OverduePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 该接口用于定义逾期付款实体的数据库操作
@Repository
public interface OverduePaymentRepository extends JpaRepository<OverduePayment, Long> {
    // 根据读者 ID 查找逾期付款记录
    List<OverduePayment> findByReaderId(Long readerId);

    // 根据借书订单 ID 查找逾期付款记录
    List<OverduePayment> findByLoanOrderId(Long loanOrderId);

    // 根据是否已清除状态查找逾期付款记录
    List<OverduePayment> findByIsCleared(Boolean isCleared);

    // 查找到期日期早于指定日期的逾期付款记录
    List<OverduePayment> findByDueDateBefore(LocalDateTime date);

    // 根据付款账单 ID 查找逾期付款记录
    List<OverduePayment> findByPaymentBillId(Long paymentBillId);
}
