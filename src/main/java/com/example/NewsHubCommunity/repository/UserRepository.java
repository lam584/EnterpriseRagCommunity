package com.example.NewsHubCommunity.repository;

import com.example.NewsHubCommunity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAccount(String account);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    /**
     * 基础搜索方法，根据ID、账号、手机号或邮箱搜索用户
     * @param id 用户ID
     * @param account 账号
     * @param phone 手机号
     * @param email 邮箱
     * @return 符合条件的用户列表
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:id IS NULL OR u.id = :id) AND " +
           "(:account IS NULL OR u.account LIKE %:account%) AND " +
           "(:phone IS NULL OR u.phone LIKE %:phone%) AND " +
           "(:email IS NULL OR u.email LIKE %:email%)")
    List<User> search(@Param("id") Long id,
                      @Param("account") String account,
                      @Param("phone") String phone,
                      @Param("email") String email);

    /**
     * 高级搜索方法，根据ID、账号、手机号、邮箱、性别、角色和时间范围搜索用户
     * @param id 用户ID
     * @param account 账号
     * @param phone 手机号
     * @param email 邮箱
     * @param sex 性别
     * @param role 角色
     * @param startDate 开始日期（创建时间）
     * @param endDate 结束日期（创建时间）
     * @return 符合条件的用户列表
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:id IS NULL OR u.id = :id) AND " +
           "(:account IS NULL OR u.account LIKE %:account%) AND " +
           "(:phone IS NULL OR u.phone LIKE %:phone%) AND " +
           "(:email IS NULL OR u.email LIKE %:email%) AND " +
           "(:sex IS NULL OR u.sex = :sex) AND " +
           "(:role IS NULL OR u.role.roles = :role) AND " +
           "(:startDate IS NULL OR u.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR u.createdAt <= :endDate)")
    List<User> search(@Param("id") Long id,
                      @Param("account") String account,
                      @Param("phone") String phone,
                      @Param("email") String email,
                      @Param("sex") String sex,
                      @Param("role") String role,
                      @Param("startDate") LocalDateTime startDate,
                      @Param("endDate") LocalDateTime endDate);
}