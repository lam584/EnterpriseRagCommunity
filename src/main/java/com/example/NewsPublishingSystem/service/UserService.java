package com.example.NewsPublishingSystem.service;


import com.example.NewsPublishingSystem.entity.User;
import com.example.NewsPublishingSystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository UserRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<User> findAll() {
        System.out.println("[调试] 正在查询所有读者信息");
        return UserRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        System.out.println("[调试] 正在根据ID查询读者信息，ID: " + id);
        return UserRepository.findById(id);
    }

    /**
     * 根据账号查询读者
     * @param account 账号
     * @return 读者对象，如果不存在则返回空
     */
    public Optional<User> findByAccount(String account) {
        System.out.println("[调试] 正在根据账号查询读者信息，账号: " + account);
        return UserRepository.findByAccount(account);
    }

    /**
     * 根据邮箱查询读者
     * @param email 邮箱
     * @return 读者对象，如果不存在则返回空
     */
    public Optional<User> findByEmail(String email) {
        System.out.println("[调试] 正在根据邮箱查询读者信息，邮箱: " + email);
        return UserRepository.findByEmail(email);
    }

    /**
     * 根据手机号查询读者
     * @param phone 手机号
     * @return 读者对象，如果不存在则返回空
     */
    public Optional<User> findByPhone(String phone) {
        System.out.println("[调试] 正在根据手机号查询读者信息，手机号: " + phone);
        return UserRepository.findByPhone(phone);
    }

    /**
     * 检查读者信息是否存在重复
     * @param User 读者对象
     * @throws IllegalArgumentException 如果存在重复信息，抛出此异常
     */
    private void checkDuplicateInfo(User User) {
        System.out.println("[调试] 正在检查读者信息是否存在重复，读者信息: " + User);
        // 如果是更新操作，需要排除自身ID进行比较
        if (User.getId() != null) {
            System.out.println("[调试] 更新操作，读者ID: " + User.getId());
            // 更新操作只在必要时检查（即当字段有值且与现有记录不同时）
            Optional<User> existingUser = UserRepository.findById(User.getId());
            if (existingUser.isPresent()) {
                User current = existingUser.get();
                // 只有当账号变更时才检查账号唯一性
                if (User.getAccount() != null && !User.getAccount().equals(current.getAccount())) {
                    UserRepository.findByAccount(User.getAccount()).ifPresent(r -> {
                        throw new IllegalArgumentException("账号 " + User.getAccount() + " 已存在");
                    });
                }

                // 只有当邮箱变更时才检查邮箱唯一性
                if (User.getEmail() != null && !User.getEmail().equals(current.getEmail())) {
                    UserRepository.findByEmail(User.getEmail()).ifPresent(r -> {
                        throw new IllegalArgumentException("邮箱 " + User.getEmail() + " 已存在");
                    });
                }

                // 只有当手机号变更时才检查手机号唯一性
                if (User.getPhone() != null && !User.getPhone().equals(current.getPhone())) {
                    UserRepository.findByPhone(User.getPhone()).ifPresent(r -> {
                        throw new IllegalArgumentException("手机号 " + User.getPhone() + " 已存在");
                    });
                }
            }
        } else {
            System.out.println("[调试] 新增操作，检查所有唯一字段");
            // 新增操作，检查所有唯一字段
            // 检查账号是否存在
            UserRepository.findByAccount(User.getAccount()).ifPresent(r -> {
                throw new IllegalArgumentException("账号 " + User.getAccount() + " 已存在");
            });

            // 检查邮箱是否存在
            UserRepository.findByEmail(User.getEmail()).ifPresent(r -> {
                throw new IllegalArgumentException("邮箱 " + User.getEmail() + " 已存在");
            });

            // 检查手机号是否存在
            UserRepository.findByPhone(User.getPhone()).ifPresent(r -> {
                throw new IllegalArgumentException("手机号 " + User.getPhone() + " 已存在");
            });
        }
    }

    public User save(User User) {
        System.out.println("[调试] 正在保存读者信息，读者信息: " + User);
        // 在保存前检查重复信息
        checkDuplicateInfo(User);

        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();

        // 如果是新建读者（ID为空），则设置创建时间和活动状态，并加密密码
        if (User.getId() == null) {
            System.out.println("[调试] 新建读者，设置创建时间和活动状态");
            User.setCreatedAt(now);
            // 默认设置读者为活动状态
            User.setActive(true);
            // 检查密码是否为null或空，如果是，则设置一个默认密码
            if (User.getPassword() == null || User.getPassword().isEmpty()) {
                throw new IllegalArgumentException("新建读者时密码不能为空");
            }
            // 加密密码
            User.setPassword(passwordEncoder.encode(User.getPassword()));
        } else {
            System.out.println("[调试] 更新读者信息，读者ID: " + User.getId());
            // 如果是更新读者，保留原来的创建时间
            Optional<User> existingUser = UserRepository.findById(User.getId());
            if (existingUser.isPresent()) {
                User.setCreatedAt(existingUser.get().getCreatedAt());

                // 如果密码没有变化（等于null或空字符串），使用原密码
                if (User.getPassword() == null || User.getPassword().isEmpty()) {
                    User.setPassword(existingUser.get().getPassword());
                } else {
                    // 否则加密新密码
                    User.setPassword(passwordEncoder.encode(User.getPassword()));
                }
            } else {
                // 如果找不到原记录，则设置为当前时间
                User.setCreatedAt(now);
                // 检查密码是否为null或空
                if (User.getPassword() == null || User.getPassword().isEmpty()) {
                    throw new IllegalArgumentException("新建读者时密码不能为空");
                } else {
                    // 加密密码
                    User.setPassword(passwordEncoder.encode(User.getPassword()));
                }
            }
        }

        // 每次保存都更新更新时间
        User.setUpdatedAt(now);
        System.out.println("[调试] 保存成功，读者信息: " + User);
        return UserRepository.save(User);
    }

    public void delete(Long id) {
        System.out.println("[调试] 正在删除读者信息，读者ID: " + id);
        UserRepository.deleteById(id);
        System.out.println("[调试] 删除成功，读者ID: " + id);
    }

    public List<User> searchBasic(Long id, String account, String phone, String email,
                                    String sex, String role, LocalDateTime startDate, LocalDateTime endDate) {
        System.out.println("[调试] 正在执行基本搜索，参数: ID=" + id + ", 账号=" + account + ", 手机号=" + phone + ", 邮箱=" + email);
        return UserRepository.search(id, account, phone, email);
    }

    public List<User> search(Long id, String account, String phone, String email,
                               String sex, String role,
                               LocalDateTime startDate, LocalDateTime endDate) {
        System.out.println("[调试] 正在执行高级搜索，参数: ID=" + id + ", 账号=" + account + ", 手机号=" + phone + ", 邮箱=" + email + ", 性别=" + sex + ", 角色=" + role);
        return UserRepository.search(id, account, phone, email, sex, role, startDate, endDate);
    }

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 密码是否匹配
     */
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        System.out.println("[调试] 正在验证密码，原始密码: " + rawPassword);
        boolean result = passwordEncoder.matches(rawPassword, encodedPassword);
        System.out.println("[调试] 验证结果: " + result);
        return result;
    }

    /**
     * 计算用户相关的借阅记录数量
     * @param userId 用户ID
     * @return 借阅记录数量
     */
    public long countLoansByUserId(Long userId) {
        System.out.println("[调试] 正在统计用户的借阅记录数量，用户ID: " + userId);
        // 这里应该调用相应的Repository方法来查询借阅记录数量
        // 由于上下文中没有提供具体实现，这里先返回0，表示没有借阅记录
        // 在实际应用中，应该替换为真正的数据库查询代码
        return 0;
    }
}