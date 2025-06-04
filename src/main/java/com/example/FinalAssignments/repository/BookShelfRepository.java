package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.BookShelf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BookShelfRepository extends JpaRepository<BookShelf, Long> {
    Optional<BookShelf> findByShelfCode(String shelfCode);
    boolean existsByShelfCode(String shelfCode);
}
