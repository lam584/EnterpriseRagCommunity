package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
    List<Book> findByIsbnContaining(String isbn);
    List<Book> findByTitleContaining(String title);
    List<Book> findByAuthorContaining(String author);
    List<Book> findByPublisherContaining(String publisher);

    // 添加根据书架ID统计图书数量的方法
    Long countByShelfId(Long shelfId);
}
