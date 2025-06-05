//java/com/example/FinalAssignments/service/ReaderService.java
package com.example.FinalAssignments.service;

import com.example.FinalAssignments.entity.Reader;
import com.example.FinalAssignments.repository.BookLoanRepository;
import com.example.FinalAssignments.repository.ReaderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReaderService {
    // 注入 BookLoanRepository
    @Autowired
    private BookLoanRepository loanRepo;

    public long countLoansByReaderId(Long readerId) {
        return loanRepo.countByReaderId(readerId);
    }
    @Autowired
    private ReaderRepository readerRepository;
    
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<Reader> findAll() {
        return readerRepository.findAll();
    }
    
    public Optional<Reader> findById(Long id) {
        return readerRepository.findById(id);
    }

    /**
     * 根据账号查询读者
     * @param account 账号
     * @return 读者对象，如果不存在则返回空
     */
    public Optional<Reader> findByAccount(String account) {
        return readerRepository.findByAccount(account);
    }

    /**
     * 根据邮箱查询读者
     * @param email 邮箱
     * @return 读者对象，如果不存在则返回空
     */
    public Optional<Reader> findByEmail(String email) {
        return readerRepository.findByEmail(email);
    }

    /**
     * 根据手机号查询读者
     * @param phone 手机号
     * @return 读者对象，如果不存在则返回空
     */
    public Optional<Reader> findByPhone(String phone) {
        return readerRepository.findByPhone(phone);
    }

    /**
     * 检查读者信息是否存在重复
     * @param reader 读者对象
     * @throws IllegalArgumentException 如果存在重复信息，抛出此异常
     */
    private void checkDuplicateInfo(Reader reader) {
        // 如果是更新操作，需要排除自身ID进行比较
        if (reader.getId() != null) {
            // 更新操作只在必要时检查（即当字段有值且��现有记录不同时）
            Optional<Reader> existingReader = readerRepository.findById(reader.getId());
            if (existingReader.isPresent()) {
                Reader current = existingReader.get();
                // 只有当账号变更时才检查账号唯一性
                if (reader.getAccount() != null && !reader.getAccount().equals(current.getAccount())) {
                    readerRepository.findByAccount(reader.getAccount()).ifPresent(r -> {
                        throw new IllegalArgumentException("账号 " + reader.getAccount() + " 已存在");
                    });
                }

                // 只有当邮箱变更时才检查邮箱唯一性
                if (reader.getEmail() != null && !reader.getEmail().equals(current.getEmail())) {
                    readerRepository.findByEmail(reader.getEmail()).ifPresent(r -> {
                        throw new IllegalArgumentException("邮箱 " + reader.getEmail() + " 已存在");
                    });
                }

                // 只有当手机号变更时才检查手机号唯一性
                if (reader.getPhone() != null && !reader.getPhone().equals(current.getPhone())) {
                    readerRepository.findByPhone(reader.getPhone()).ifPresent(r -> {
                        throw new IllegalArgumentException("手机号 " + reader.getPhone() + " 已存在");
                    });
                }
            }
        } else {
            // 新增操作，检查所有唯一字段
            // 检查账号是否存在
            readerRepository.findByAccount(reader.getAccount()).ifPresent(r -> {
                throw new IllegalArgumentException("账号 " + reader.getAccount() + " 已存在");
            });

            // 检查邮箱是否存在
            readerRepository.findByEmail(reader.getEmail()).ifPresent(r -> {
                throw new IllegalArgumentException("邮箱 " + reader.getEmail() + " 已存在");
            });

            // 检查手机号是否存在
            readerRepository.findByPhone(reader.getPhone()).ifPresent(r -> {
                throw new IllegalArgumentException("手机号 " + reader.getPhone() + " 已存在");
            });
        }
    }

    public Reader save(Reader reader) {
        // 在保存前检查重复信息
        checkDuplicateInfo(reader);

        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();

        // 如果是新建读者（ID为空），则设置创建时间和活动状态，并加密密码
        if (reader.getId() == null) {
            reader.setCreatedAt(now);
            // 默认设置读者为活动状态
            if (reader.getIsActive() == null) {
                reader.setIsActive(true);
            }
            // 检查密码是否为null或空，如果是，则设置一个默认密码
            if (reader.getPassword() == null || reader.getPassword().isEmpty()) {
                throw new IllegalArgumentException("新建读者时密码不能为空");
            }
            // 加密密码
            reader.setPassword(passwordEncoder.encode(reader.getPassword()));
        } else {
            // 如果是更新读者，保留原来的创建时间
            Optional<Reader> existingReader = readerRepository.findById(reader.getId());
            if (existingReader.isPresent()) {
                reader.setCreatedAt(existingReader.get().getCreatedAt());

                // 如果密码没有变化（等于null或空字符串），使用原密码
                if (reader.getPassword() == null || reader.getPassword().isEmpty()) {
                    reader.setPassword(existingReader.get().getPassword());
                } else {
                    // 否则加密新密码
                    reader.setPassword(passwordEncoder.encode(reader.getPassword()));
                }
            } else {
                // 如果找不到原记录，则设置为当前时间
                reader.setCreatedAt(now);
                // 检查密码是否为null或空
                if (reader.getPassword() == null || reader.getPassword().isEmpty()) {
                    throw new IllegalArgumentException("新建读者时密码不能��空");
                } else {
                    // 加密密码
                    reader.setPassword(passwordEncoder.encode(reader.getPassword()));
                }
            }
        }

        // 每次保存都更新更新时间
        reader.setUpdatedAt(now);

        return readerRepository.save(reader);
    }

    public void delete(Long id) {
        readerRepository.deleteById(id);
    }

    public List<Reader> search(Long id, String account, String phone, String email,
                               String sex, String role, LocalDateTime  startDate, LocalDateTime  endDate) {
        return readerRepository.search(id, account, phone, email, sex, role, startDate, endDate);
    }
    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 密码是否匹配
     */
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}