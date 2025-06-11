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
        System.out.println("[调试] 正在查询所有书架信息");
        return bookShelfRepository.findAll();
    }

    /**
     * 根据ID查找书架
     * @param id 书架ID
     * @return 书架对象
     */
    public Optional<BookShelf> findById(Long id) {
        System.out.println("[调试] 正在根据ID查询书架信息，ID: " + id);
        return bookShelfRepository.findById(id);
    }

    /**
     * 根据书架编码查找书架
     * @param shelfCode 书架编码
     * @return 书架对象
     */
    public Optional<BookShelf> findByShelfCode(String shelfCode) {
        System.out.println("[调试] 正在根据书架编码查询书架信息，书架编码: " + shelfCode);
        return bookShelfRepository.findByShelfCode(shelfCode);
    }

    /**
     * 保存或更新书架
     * @param bookShelf 书架对象
     * @return 保存后的书架对象
     */
    @Transactional
    public BookShelf saveBookShelf(BookShelf bookShelf) {
        System.out.println("[调试] 正在保存书架信息，书架信息: " + bookShelf);
        if (bookShelf.getId() == null) {
            System.out.println("[调试] 新建书架，设置创建时间");
            // 新增书架时设置创建时间
            bookShelf.setCreatedAt(LocalDateTime.now());
        }
        System.out.println("[调试] 更新书架信息，书架ID: " + bookShelf.getId());
        bookShelf.setUpdatedAt(LocalDateTime.now());
        System.out.println("[调试] 保存成功，书架信息: " + bookShelf);
        return bookShelfRepository.save(bookShelf);
    }

    /**
     * 删除书架
     * @param id 书架ID
     * @return 是否成功删除
     */
    @Transactional
    public boolean deleteBookShelf(Long id) {
        System.out.println("[调试] 正在删除书架信息，书架ID: " + id);
        // 检查书架上是否有图书
        Long bookCount = bookRepository.countByShelfId(id);
        if (bookCount > 0) {
            System.out.println("[调试] 删除失败，书架上还有图书，书架ID: " + id);
            // 书架上还有图书，不能删除
            return false;
        }
        bookShelfRepository.deleteById(id);
        System.out.println("[调试] 删除成功，书架ID: " + id);
        return true;
    }

    /**
     * 检查书架编码是否已存在
     * @param shelfCode 书架编码
     * @return 是否存在
     */
    public boolean isShelfCodeExists(String shelfCode) {
        System.out.println("[调试] 正在检查书架编码是否存在，书架编码: " + shelfCode);
        return bookShelfRepository.existsByShelfCode(shelfCode);
    }

    /**
     * 检查书架是否可用（未超出容量）
     * @param shelfId 书架ID
     * @return 是否可用
     */
    public boolean isShelfAvailable(Long shelfId) {
        System.out.println("[调试] 正在检查书架是否可用，书架ID: " + shelfId);
        Optional<BookShelf> shelfOpt = bookShelfRepository.findById(shelfId);
        if (shelfOpt.isEmpty()) {
            System.out.println("[调试] 书架不可用，书架ID: " + shelfId);
            return false;
        }

        BookShelf shelf = shelfOpt.get();
        Long currentBooks = bookRepository.countByShelfId(shelfId);
        boolean available = currentBooks < shelf.getCapacity();
        System.out.println("[调试] 书架是否可用: " + available);
        return available;
    }
}
