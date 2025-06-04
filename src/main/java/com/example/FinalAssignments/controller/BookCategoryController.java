package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.entity.BookCategory;
import com.example.FinalAssignments.service.BookCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class BookCategoryController {

    @Autowired
    private BookCategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<BookCategory>> getAllCategories() {
        List<BookCategory> list = categoryService.findAll();
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        Optional<BookCategory> cat = categoryService.findById(id);
        if (cat.isPresent()) {
            return new ResponseEntity<>(cat.get(), HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "分类不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<BookCategory> createCategory(@RequestBody BookCategory category) {
        BookCategory saved = categoryService.save(category);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody BookCategory details) {
        Optional<BookCategory> exist = categoryService.findById(id);
        if (!exist.isPresent()) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "分类不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
        details.setId(id);
        BookCategory updated = categoryService.save(details);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        Map<String, String> res = new HashMap<>();
        res.put("message", "删除成功");
        return new ResponseEntity<>(res, HttpStatus.OK);
    }
}
