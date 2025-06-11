package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.BookLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 该接口用于定义借书记录实体的数据库操作
@Repository
public interface BookLoanRepository extends JpaRepository<BookLoan, Long> {
    // 根据读者 ID 查找借书记录
    List<BookLoan> findByReaderId(Long readerId);

    // 根据书籍 ID 查找借书记录
    List<BookLoan> findByBookId(Long bookId);

    // 根据借书状态查找记录
    List<BookLoan> findByStatus(String status);

    // 根据管理员 ID 查找借书记录
    List<BookLoan> findByAdministratorId(Long administratorId);

    // 查找在指定时间范围内开始的借书记录
    List<BookLoan> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    // 根据读者 ID 和状态查找借书记录
    List<BookLoan> findByReaderIdAndStatus(Long readerId, String status);

    // 统计指定读者的借书记录数量
    long countByReaderId(Long readerId);
}
