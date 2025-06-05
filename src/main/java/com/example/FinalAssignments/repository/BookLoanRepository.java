package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.BookLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookLoanRepository extends JpaRepository<BookLoan, Long> {
    List<BookLoan> findByReaderId(Long readerId);
    List<BookLoan> findByBookId(Long bookId);
    List<BookLoan> findByStatus(String status);
    List<BookLoan> findByAdministratorId(Long administratorId);
    List<BookLoan> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<BookLoan> findByReaderIdAndStatus(Long readerId, String status);
    long countByReaderId(Long readerId);
}
