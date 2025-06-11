package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

// 该接口用于定义书籍实体的数据库操作
@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
    // 根据 ISBN 部分匹配查找书籍
    List<Book> findByIsbnContaining(String isbn);

    // 根据标题部分匹配查找书籍
    List<Book> findByTitleContaining(String title);

    // 根据作者部分匹配查找书籍
    List<Book> findByAuthorContaining(String author);

    // 根据出版社部分匹配查找书籍
    List<Book> findByPublisherContaining(String publisher);

    // 统计指定书架上的书籍数量
    Long countByShelfId(Long shelfId);
}
