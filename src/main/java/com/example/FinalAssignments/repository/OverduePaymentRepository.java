package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.OverduePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OverduePaymentRepository extends JpaRepository<OverduePayment, Long> {
    List<OverduePayment> findByReaderId(Long readerId);
    List<OverduePayment> findByLoanOrderId(Long loanOrderId);
    List<OverduePayment> findByIsCleared(Boolean isCleared);
    List<OverduePayment> findByDueDateBefore(LocalDateTime date);
    List<OverduePayment> findByPaymentBillId(Long paymentBillId);
}
