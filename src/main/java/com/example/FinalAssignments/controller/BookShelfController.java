package com.example.FinalAssignments.controller;

import com.example.FinalAssignments.entity.BookShelf;
import com.example.FinalAssignments.service.BookShelfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/shelves")
@CrossOrigin(origins = "*") // 允许跨域请求，实际生产环境中应限制来源
public class BookShelfController {

    @Autowired
    private BookShelfService bookShelfService;

    /**
     * 获取所有书架
     * @return 书架列表
     */
    @GetMapping
    public ResponseEntity<List<BookShelf>> getAllShelves() {
        List<BookShelf> shelves = bookShelfService.findAllShelves();
        return new ResponseEntity<>(shelves, HttpStatus.OK);
    }

    /**
     * 根据ID获取书架
     * @param id 书架ID
     * @return 书架对象
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getShelfById(@PathVariable Long id) {
        Optional<BookShelf> shelf = bookShelfService.findById(id);
        if (shelf.isPresent()) {
            return new ResponseEntity<>(shelf.get(), HttpStatus.OK);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("message", "书架不存在");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 根据书架编码获取书架
     * @param shelfCode 书架编码
     * @return 书架对象
     */
    @GetMapping("/code/{shelfCode}")
    public ResponseEntity<?> getShelfByCode(@PathVariable String shelfCode) {
        Optional<BookShelf> shelf = bookShelfService.findByShelfCode(shelfCode);
        if (shelf.isPresent()) {
            return new ResponseEntity<>(shelf.get(), HttpStatus.OK);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("message", "书架不存在");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 添加新书架
     * @param bookShelf 书架对象
     * @return 保存后的书架对象
     */
    @PostMapping
    public ResponseEntity<?> addShelf(@RequestBody BookShelf bookShelf) {
        // 检查书架编码是否已存在
        if (bookShelfService.isShelfCodeExists(bookShelf.getShelfCode())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "书架编码已存在");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        BookShelf savedShelf = bookShelfService.saveBookShelf(bookShelf);
        return new ResponseEntity<>(savedShelf, HttpStatus.CREATED);
    }

    /**
     * 更新书架信息
     * @param id 书架ID
     * @param shelfDetails 更新的书架详情
     * @return 更新后的书架对象
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShelf(@PathVariable Long id, @RequestBody BookShelf shelfDetails) {
        Optional<BookShelf> existingShelf = bookShelfService.findById(id);
        if (!existingShelf.isPresent()) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "书架不存在");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        // 检查更新后的书架编码是否与其他书架冲突
        if (!shelfDetails.getShelfCode().equals(existingShelf.get().getShelfCode()) &&
            bookShelfService.isShelfCodeExists(shelfDetails.getShelfCode())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "书架编码已被其他书架使用");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 保留原书架ID
        shelfDetails.setId(id);
        BookShelf updatedShelf = bookShelfService.saveBookShelf(shelfDetails);
        return new ResponseEntity<>(updatedShelf, HttpStatus.OK);
    }

    /**
     * 删除书架
     * @param id 书架ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShelf(@PathVariable Long id) {
        boolean isDeleted = bookShelfService.deleteBookShelf(id);
        Map<String, String> response = new HashMap<>();

        if (isDeleted) {
            response.put("message", "书架删除成功");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            response.put("message", "书架不存在或该书架上存在图书，无法删除");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 检查书架编码是否可用
     * @param shelfCode 书架编码
     * @return 是否可用
     */
    @GetMapping("/check-code/{shelfCode}")
    public ResponseEntity<Map<String, Boolean>> checkShelfCodeAvailability(@PathVariable String shelfCode) {
        boolean exists = bookShelfService.isShelfCodeExists(shelfCode);
        Map<String, Boolean> response = new HashMap<>();
        response.put("available", !exists);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
