package com.example.FinalAssignments.service;

import com.example.FinalAssignments.entity.Administrator;
import com.example.FinalAssignments.entity.Book;
import com.example.FinalAssignments.entity.BookCategory;
import com.example.FinalAssignments.entity.BookShelf;
import com.example.FinalAssignments.repository.AdministratorRepository;
import com.example.FinalAssignments.repository.BookCategoryRepository;
import com.example.FinalAssignments.repository.BookRepository;
import com.example.FinalAssignments.repository.BookShelfRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    private static final Logger logger = LoggerFactory.getLogger(BookService.class);

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCategoryRepository categoryRepository;

    @Autowired
    private BookShelfRepository shelfRepository;

    @Autowired
    private AdministratorRepository administratorRepository;

    public List<Book> findAll() {
        logger.info("[调试] 正在查询所有图书信息");
        return bookRepository.findAll();
    }

    public Optional<Book> findById(Long id) {
        logger.info("[调试] 正在根据ID查询图书信息，ID: {}", id);
        return bookRepository.findById(id);
    }

    /**
     * 基本搜索方法，向后兼容
     */
    public List<Book> search(String isbn, String title, String author, String publisher) {
        logger.info("[调试] 正在执行基本搜索，参数: ISBN={}, 标题={}, 作者={}, 出版社={}", isbn, title, author, publisher);
        return advancedSearch(null, false, isbn, false, title, false, author, false, publisher, false,
                null, false, null, false, null, false, null, null, null, false, null);
    }

    /**
     * 高级搜索方法，支持更多条件和精确匹配
     */
    public List<Book> advancedSearch(
            Long id, boolean idExact,
            String isbn, boolean isbnExact,
            String title, boolean titleExact,
            String author, boolean authorExact,
            String publisher, boolean publisherExact,
            String edition, boolean editionExact,
            String category, boolean categoryExact,
            String shelvesCode, boolean shelvesCodeExact,
            BigDecimal priceMin, BigDecimal priceMax,
            String printTimes, boolean printTimesExact,
            String status) {

        logger.info("[调试] 正在执行高级搜索，参数: ID={}, ISBN={}, 标题={}, 作者={}, 出版社={}, 分类={}, 书架编码={}, 最低价格={}, 最高价格={}, 印次={}, 状态={}",
                id, isbn, title, author, publisher, category, shelvesCode, priceMin, priceMax, printTimes, status);

        return bookRepository.findAll((Specification<Book>) (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 处理ID搜索
            if (id != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), id));
            }

            // 处理ISBN搜索
            if (isbn != null && !isbn.isEmpty()) {
                if (isbnExact) {
                    predicates.add(criteriaBuilder.equal(root.get("isbn"), isbn));
                } else {
                    predicates.add(criteriaBuilder.like(root.get("isbn"), "%" + isbn + "%"));
                }
            }

            // 处理标题搜索
            if (title != null && !title.isEmpty()) {
                if (titleExact) {
                    predicates.add(criteriaBuilder.equal(root.get("title"), title));
                } else {
                    predicates.add(criteriaBuilder.like(root.get("title"), "%" + title + "%"));
                }
            }

            // 处理作者搜索
            if (author != null && !author.isEmpty()) {
                if (authorExact) {
                    predicates.add(criteriaBuilder.equal(root.get("author"), author));
                } else {
                    predicates.add(criteriaBuilder.like(root.get("author"), "%" + author + "%"));
                }
            }

            // 处理出版社搜索
            if (publisher != null && !publisher.isEmpty()) {
                if (publisherExact) {
                    predicates.add(criteriaBuilder.equal(root.get("publisher"), publisher));
                } else {
                    predicates.add(criteriaBuilder.like(root.get("publisher"), "%" + publisher + "%"));
                }
            }

            // 处理版次搜索
            if (edition != null && !edition.isEmpty()) {
                if (editionExact) {
                    predicates.add(criteriaBuilder.equal(root.get("edition"), edition));
                } else {
                    predicates.add(criteriaBuilder.like(root.get("edition"), "%" + edition + "%"));
                }
            }

            // 处理分类搜索
            if (category != null && !category.isEmpty()) {
                Join<Book, BookCategory> categoryJoin = root.join("category");
                if (categoryExact) {
                    predicates.add(criteriaBuilder.equal(categoryJoin.get("name"), category));
                } else {
                    predicates.add(criteriaBuilder.like(categoryJoin.get("name"), "%" + category + "%"));
                }
            }

            // 处理书架搜索
            if (shelvesCode != null && !shelvesCode.isEmpty()) {
                Join<Book, BookShelf> shelfJoin = root.join("shelf");
                if (shelvesCodeExact) {
                    predicates.add(criteriaBuilder.equal(shelfJoin.get("shelfCode"), shelvesCode));
                } else {
                    predicates.add(criteriaBuilder.like(shelfJoin.get("shelfCode"), "%" + shelvesCode + "%"));
                }
            }

            // 处理价格范围搜索
            if (priceMin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), priceMin));
            }

            if (priceMax != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), priceMax));
            }

            // 处理印次搜索
            if (printTimes != null && !printTimes.isEmpty()) {
                if (printTimesExact) {
                    predicates.add(criteriaBuilder.equal(root.get("printTimes"), printTimes));
                } else {
                    predicates.add(criteriaBuilder.like(root.get("printTimes"), "%" + printTimes + "%"));
                }
            }

            // 处理状态搜索
            if (status != null && !status.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }

    @Transactional
    public Book save(Book book) {
        logger.info("[调试] 正在保存图书信息，图书信息: {}", book);
        // 获取当前登录的管理员
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.info("当前认证信息: {}", authentication);

        if (authentication == null) {
            logger.error("保存图书失败: 认证对象为空");
            throw new RuntimeException("未获取到登录信息，请重新登录");
        }

        if (!authentication.isAuthenticated() || authentication.getName().equals("anonymousUser")) {
            logger.error("保存图书失败: 用户未认证或是匿名用户, authentication={}", authentication);
            throw new RuntimeException("用户未登录或会话已过期，请重新登录");
        }

        String username = authentication.getName();
        logger.info("获取到的用户名: {}", username);

        Optional<Administrator> admin = administratorRepository.findByAccount(username);
        logger.info("根据用户名查询管理员结果: {}", admin.isPresent() ? "找到管理员" : "未找到管理员");

        if (admin.isEmpty()) {
            logger.error("保存图书失败: 未找到用户名为{}的管理员", username);
            throw new RuntimeException("无法获取管理员信息，请联系系统管理员");
        }

        // 如果是新书，设置创建信息
        if (book.getId() == null) {
            book.setCreatedAt(LocalDateTime.now());
        }

        // 始终更新最后修改信息
        book.setUpdatedAt(LocalDateTime.now());
        book.setAdministrator(admin.get());
        logger.info("设置图书管理员ID: {}", admin.get().getId());

        logger.info("[调试] 保存成功，图书信息: {}", book);
        return bookRepository.save(book);
    }

    @Transactional
    public void delete(Long id) {
        logger.info("[调试] 正在删除图书信息，图书ID: {}", id);
        bookRepository.deleteById(id);
        logger.info("[调试] 删除成功，图书ID: {}", id);
    }
}
