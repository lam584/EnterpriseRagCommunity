package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.entity.Book;
import com.example.FinalAssignments.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")
public class BookController {

    @Autowired
    private BookService bookService;

    @GetMapping
    public ResponseEntity<List<Book>> getBooks(
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String publisher) {
        List<Book> list = bookService.search(isbn, title, author, publisher);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    // 添加支持ID搜索的方法
    @GetMapping("/search")
    public ResponseEntity<List<Book>> searchBooks(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String publisher) {
        List<Book> list;
        if (id != null) {
            // 如果提供了ID，直接按ID搜索
            Optional<Book> book = bookService.findById(id);
            list = book.isPresent() ? List.of(book.get()) : List.of();
        } else {
            // 否则按其他条件搜索
            list = bookService.search(isbn, title, author, publisher);
        }
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    // 添加高级搜索端点
    @GetMapping("/advanced-search")
    public ResponseEntity<List<Book>> advancedSearch(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false, defaultValue = "true") Boolean idExact,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false, defaultValue = "false") Boolean isbnExact,
            @RequestParam(required = false) String title,
            @RequestParam(required = false, defaultValue = "false") Boolean titleExact,
            @RequestParam(required = false) String author,
            @RequestParam(required = false, defaultValue = "false") Boolean authorExact,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false, defaultValue = "false") Boolean publisherExact,
            @RequestParam(required = false) String edition,
            @RequestParam(required = false, defaultValue = "false") Boolean editionExact,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") Boolean categoryExact,
            @RequestParam(required = false) String shelvesCode,
            @RequestParam(required = false, defaultValue = "false") Boolean shelvesCodeExact,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) String printTimes,
            @RequestParam(required = false, defaultValue = "false") Boolean printTimesExact,
            @RequestParam(required = false) String status) {

        List<Book> list = bookService.advancedSearch(
                id, idExact, isbn, isbnExact, title, titleExact, author, authorExact, publisher, publisherExact,
                edition, editionExact, category, categoryExact, shelvesCode, shelvesCodeExact,
                priceMin, priceMax, printTimes, printTimesExact, status);

        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBookById(@PathVariable Long id) {
        Optional<Book> book = bookService.findById(id);
        if (book.isPresent()) {
            return new ResponseEntity<>(book.get(), HttpStatus.OK);
        } else {
            Map<String, String> res = new HashMap<>();
            res.put("message", "图书不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping
    public ResponseEntity<?> createBook(@Valid @RequestBody Book book) {
        Book saved = bookService.save(book);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBook(@PathVariable Long id, @Valid @RequestBody Book bookDetails) {
        Optional<Book> exist = bookService.findById(id);
        if (!exist.isPresent()) {
            Map<String, String> res = new HashMap<>();
            res.put("message", "图书不存在");
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
        bookDetails.setId(id);
        Book updated = bookService.save(bookDetails);
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBook(@PathVariable Long id) {
        bookService.delete(id);
        Map<String, String> res = new HashMap<>();
        res.put("message", "删除成功");
        return new ResponseEntity<>(res, HttpStatus.OK);
    }
}
