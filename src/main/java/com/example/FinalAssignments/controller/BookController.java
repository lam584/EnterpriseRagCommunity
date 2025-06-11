package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.dto.BookDTO;
import com.example.FinalAssignments.entity.Book;
import com.example.FinalAssignments.service.BookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")  // 生产环境请改为具体域名
public class BookController {

    private final BookService bookService;
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // 1. 列表（所有图书）
    @GetMapping("")
    public ResponseEntity<List<BookDTO>> listAll() {
        List<Book> all = bookService.findAll();
        List<BookDTO> dtos = BookDTO.Converter.fromEntityList(all);
        return ResponseEntity.ok(dtos);
    }

    // 2. 基本搜索（兼容 /search 和 /query）
    @GetMapping({"/search", "/query"})
    public ResponseEntity<List<BookDTO>> search(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String publisher
    ) {
        List<Book> results;
        if (id != null) {
            results = bookService.findById(id).map(List::of).orElse(List.of());
        } else {
            results = bookService.search(isbn, title, author, publisher);
        }
        List<BookDTO> dtos = BookDTO.Converter.fromEntityList(results);
        return ResponseEntity.ok(dtos);
    }

    // 3. 高级搜索
    @GetMapping("/advanced-search")
    public ResponseEntity<List<BookDTO>> advancedSearch(
            @RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "false") boolean idExact,
            @RequestParam(required = false) String isbn,
            @RequestParam(defaultValue = "false") boolean isbnExact,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "false") boolean titleExact,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "false") boolean authorExact,
            @RequestParam(required = false) String publisher,
            @RequestParam(defaultValue = "false") boolean publisherExact,
            @RequestParam(required = false) String edition,
            @RequestParam(defaultValue = "false") boolean editionExact,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean categoryExact,
            @RequestParam(required = false) String shelvesCode,
            @RequestParam(defaultValue = "false") boolean shelvesCodeExact,
            @RequestParam(required = false) java.math.BigDecimal priceMin,
            @RequestParam(required = false) java.math.BigDecimal priceMax,
            @RequestParam(required = false) String printTimes,
            @RequestParam(defaultValue = "false") boolean printTimesExact,
            @RequestParam(required = false) String status
    ) {
        List<Book> list = bookService.advancedSearch(
                id, idExact,
                isbn, isbnExact,
                title, titleExact,
                author, authorExact,
                publisher, publisherExact,
                edition, editionExact,
                category, categoryExact,
                shelvesCode, shelvesCodeExact,
                priceMin, priceMax,
                printTimes, printTimesExact,
                status
        );
        return ResponseEntity.ok(BookDTO.Converter.fromEntityList(list));
    }

    // 4. 根据 ID 取详情
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Optional<Book> opt = bookService.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","图书不存在"));
        }
        return ResponseEntity.ok(BookDTO.Converter.fromEntity(opt.get()));
    }

    // 5. 新增
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BookDTO dto) {
        // DTO -> Entity（包含 category.id、shelf.id、administrator.id）
        Book toSave = BookDTO.Converter.toEntity(dto);
        Book saved = bookService.save(toSave);
        // Entity -> DTO 返回给前端
        return ResponseEntity.status(HttpStatus.CREATED).body(BookDTO.Converter.fromEntity(saved));
    }

    // 6. 更新
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody BookDTO dto
    ) {
        Optional<Book> opt = bookService.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","图书不存在"));
        }
        Book entity = opt.get();
        // 只更新前端 DTO 提交的非空字段
        BookDTO.Converter.updateEntity(dto, entity);
        Book updated = bookService.save(entity);
        return ResponseEntity.ok(BookDTO.Converter.fromEntity(updated));
    }

    // 7. 删除
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.ok(Map.of("message","删除成功"));
    }
}