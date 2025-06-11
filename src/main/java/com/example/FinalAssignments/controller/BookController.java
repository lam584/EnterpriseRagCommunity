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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")  // 生产环境请改为具体域名
public class BookController {

    private static final Logger logger = LoggerFactory.getLogger(BookController.class);

    private final BookService bookService;
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // 1. 列表（所有图书）
    @GetMapping("")
    public ResponseEntity<List<BookDTO>> listAll() {
        logger.info("开始获取所有图书列表");
        List<Book> all = bookService.findAll();
        logger.info("获取到 {} 本图书", all.size());
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
        logger.info("开始基本搜索，参数：id={}, isbn={}, title={}, author={}, publisher={}", id, isbn, title, author, publisher);
        List<Book> results;
        if (id != null) {
            results = bookService.findById(id).map(List::of).orElse(List.of());
            logger.info("根据 ID 搜索结果：{}", results.size());
        } else {
            results = bookService.search(isbn, title, author, publisher);
            logger.info("根据其他条件搜索结果：{}", results.size());
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
        logger.info("开始高级搜索，参数：id={}, idExact={}, isbn={}, isbnExact={}, title={}, titleExact={}, author={}, authorExact={}, publisher={}, publisherExact={}, edition={}, editionExact={}, category={}, categoryExact={}, shelvesCode={}, shelvesCodeExact={}, priceMin={}, priceMax={}, printTimes={}, printTimesExact={}, status={}",
                id, idExact, isbn, isbnExact, title, titleExact, author, authorExact, publisher, publisherExact, edition, editionExact, category, categoryExact, shelvesCode, shelvesCodeExact, priceMin, priceMax, printTimes, printTimesExact, status);
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
        logger.info("高级搜索结果：{} 本图书", list.size());
        return ResponseEntity.ok(BookDTO.Converter.fromEntityList(list));
    }

    // 4. 根据 ID 取详情
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        logger.info("开始根据 ID 获取图书详情，ID={}", id);
        Optional<Book> opt = bookService.findById(id);
        if (opt.isEmpty()) {
            logger.warn("图书不存在，ID={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","图书不存在"));
        }
        logger.info("图书详情获取成功，ID={}", id);
        return ResponseEntity.ok(BookDTO.Converter.fromEntity(opt.get()));
    }

    // 5. 新增
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BookDTO dto) {
        logger.info("开始新增图书，数据：{}", dto);
        Book toSave = BookDTO.Converter.toEntity(dto);
        Book saved = bookService.save(toSave);
        logger.info("图书新增成功，ID={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookDTO.Converter.fromEntity(saved));
    }

    // 6. 更新
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody BookDTO dto
    ) {
        logger.info("开始更新图书，ID={}，数据：{}", id, dto);
        Optional<Book> opt = bookService.findById(id);
        if (opt.isEmpty()) {
            logger.warn("图书不存在，无法更新，ID={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","图书不存在"));
        }
        Book entity = opt.get();
        BookDTO.Converter.updateEntity(dto, entity);
        Book updated = bookService.save(entity);
        logger.info("图书更新成功，ID={}", updated.getId());
        return ResponseEntity.ok(BookDTO.Converter.fromEntity(updated));
    }

    // 7. 删除
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        logger.info("开始删除图书，ID={}", id);
        bookService.delete(id);
        logger.info("图书删除成功，ID={}", id);
        return ResponseEntity.ok(Map.of("message","删除成功"));
    }
}