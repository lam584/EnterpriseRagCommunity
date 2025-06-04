package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.PaymentBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentBillRepository extends JpaRepository<PaymentBill, Long> {
    List<PaymentBill> findByReaderId(Long readerId);
    List<PaymentBill> findByAdminId(Long adminId);
    List<PaymentBill> findByPaymentDateBetween(LocalDateTime startDate, LocalDateTime endDate);
}
