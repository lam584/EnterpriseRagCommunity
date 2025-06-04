package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.BookCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookCategoryRepository extends JpaRepository<BookCategory, Long> {
    BookCategory findByName(String name);
    boolean existsByName(String name);
}
