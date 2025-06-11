package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.BookShelf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

// 该接口用于定义书架实体的数据库操作
@Repository
public interface BookShelfRepository extends JpaRepository<BookShelf, Long> {
    // 根据书架代码查找书架信息
    Optional<BookShelf> findByShelfCode(String shelfCode);

    // 检查是否存在指定书架代码的书架
    boolean existsByShelfCode(String shelfCode);
}
