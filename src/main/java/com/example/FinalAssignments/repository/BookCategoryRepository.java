package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.BookCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 该接口用于定义书籍分类实体的数据库操作
 */
@Repository
public interface BookCategoryRepository extends JpaRepository<BookCategory, Long> {
    /**
     * 根据分类名称查找书籍分类
     *
     * @param name 分类名称
     * @return 匹配的书籍分类，如果没有匹配，则返回null
     */
    BookCategory findByName(String name);

    /**
     * 检查是否存在指定名称的书籍分类
     *
     * @param name 分类名称
     * @return 如果存在指定名称的书籍分类，则返回true；否则返回false
     */
    boolean existsByName(String name);
}
