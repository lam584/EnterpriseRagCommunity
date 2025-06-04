package com.example.FinalAssignments.service;

import com.example.FinalAssignments.entity.BookCategory;
import com.example.FinalAssignments.repository.BookCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookCategoryService {

    @Autowired
    private BookCategoryRepository categoryRepository;

    public List<BookCategory> findAll() {
        return categoryRepository.findAll();
    }

    public Optional<BookCategory> findById(Long id) {
        return categoryRepository.findById(id);
    }

    @Transactional
    public BookCategory save(BookCategory category) {
        LocalDateTime now = LocalDateTime.now();
        if (category.getId() == null) {
            category.setCreatedAt(now);
        }
        category.setUpdatedAt(now);
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
