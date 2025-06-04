package com.example.FinalAssignments.service;

import com.example.FinalAssignments.entity.BookShelf;
import com.example.FinalAssignments.repository.BookRepository;
import com.example.FinalAssignments.repository.BookShelfRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookShelfService {

    @Autowired
    private BookShelfRepository bookShelfRepository;

    @Autowired
    private BookRepository bookRepository;

    /**
     * 获取所有书架
     * @return 书架列表
     */
    public List<BookShelf> findAllShelves() {
        return bookShelfRepository.findAll();
    }

    /**
     * 根据ID查找书架
     * @param id 书架ID
     * @return 书架对象
     */
    public Optional<BookShelf> findById(Long id) {
        return bookShelfRepository.findById(id);
    }

    /**
     * 根据书架编码查找书架
     * @param shelfCode 书架编码
     * @return 书架对象
     */
    public Optional<BookShelf> findByShelfCode(String shelfCode) {
        return bookShelfRepository.findByShelfCode(shelfCode);
    }

    /**
     * 保存或更新书架
     * @param bookShelf 书架对象
     * @return 保存后的书架对象
     */
    @Transactional
    public BookShelf saveBookShelf(BookShelf bookShelf) {
        if (bookShelf.getId() == null) {
            // 新增书架时设置创建时间
            bookShelf.setCreatedAt(LocalDateTime.now());
        }
        bookShelf.setUpdatedAt(LocalDateTime.now());
        return bookShelfRepository.save(bookShelf);
    }

    /**
     * 删除书架
     * @param id 书架ID
     * @return 是否成功删除
     */
    @Transactional
    public boolean deleteBookShelf(Long id) {
        // 检查书架上是否有图书
        Long bookCount = bookRepository.countByShelfId(id);
        if (bookCount > 0) {
            // 书架上还有图书，不能删除
            return false;
        }

        bookShelfRepository.deleteById(id);
        return true;
    }

    /**
     * 检查书架编码是否已存在
     * @param shelfCode 书架编码
     * @return 是否存在
     */
    public boolean isShelfCodeExists(String shelfCode) {
        return bookShelfRepository.existsByShelfCode(shelfCode);
    }

    /**
     * 检查书架是否可用（未超出容量）
     * @param shelfId 书架ID
     * @return 是否可用
     */
    public boolean isShelfAvailable(Long shelfId) {
        Optional<BookShelf> shelfOpt = bookShelfRepository.findById(shelfId);
        if (shelfOpt.isEmpty()) {
            return false;
        }

        BookShelf shelf = shelfOpt.get();
        Long currentBooks = bookRepository.countByShelfId(shelfId);

        return currentBooks < shelf.getCapacity();
    }
}
