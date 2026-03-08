package com.example.EnterpriseRagCommunity.service;

// 修改批次: 1 | 修改依据: src/main/java/com/example/EnterpriseRagCommunity/entity/access/UsersEntity.java, src/main/java/com/example/EnterpriseRagCommunity/repository/access/UsersRepository.java
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UsersRepository userRepository; // 对齐: Repository层已改为access包

    public List<UsersEntity> findAll() { // 对齐: 返回类型改为UsersEntity
        return userRepository.findAll();
    }

    public Optional<UsersEntity> findById(Long id) { // 对齐: 返回类型改为UsersEntity
        return userRepository.findById(id); // 修复: 使用基础findById方法，因编译错误要求匹配findByEmail签名模式
    }

    private Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Basic search that accepts legacy parameters and maps them to the new schema.
     * account -> fuzzy match on email or displayName
     * email   -> fuzzy match on email
     * role    -> fuzzy match on role name
     * startDate/endDate -> createdAt range (Instant based)
     */
    public List<UsersEntity> searchBasic(Long id,
                                  String account,
                                  String phone, // ignored in new schema
                                  String email,
                                  String sex,   // ignored in new schema
                                  String role,
                                  LocalDateTime startDate,
                                  LocalDateTime endDate) {
        return searchInternal(id, account, email, role, startDate, endDate);
    }

    /**
     * Advanced search - same mapping but kept for backward compatibility of controller.
     */
    public List<UsersEntity> search(Long id,
                             String account,
                             String phone, // ignored
                             String email,
                             String sex,   // ignored
                             String role,
                             LocalDateTime startDate,
                             LocalDateTime endDate) {
        return searchInternal(id, account, email, role, startDate, endDate);
    }

    private List<UsersEntity> searchInternal(Long id, // 对齐: 返回类型改为UsersEntity
                                      String account,
                                      String email,
                                      String role,
                                      LocalDateTime startDate,
                                      LocalDateTime endDate) {
        List<Specification<UsersEntity>> specs = new ArrayList<>(); // 对齐: Specification泛型改为UsersEntity

        if (id != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("id"), id));
        }
        if (account != null && !account.isBlank()) {
            String like = "%" + account.trim() + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like.toLowerCase()), // 对齐: SQL users.email → Entity.email
                    cb.like(cb.lower(root.get("username")), like.toLowerCase()) // 对齐: 旧displayName → 新username (展示名)
            ));
        }
        if (email != null && !email.isBlank()) {
            String like = "%" + email.trim() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("email")), like.toLowerCase())); // 对齐: SQL users.email → Entity.email
        }
        // 角色查询逻辑保持不变，假设UserRolesEntity关联已正确建立
        if (role != null && !role.isBlank()) {
            String like = "%" + role.trim() + "%";
            specs.add((root, query, cb) -> {
                query.distinct(true);
                var rolesJoin = root.join("roles"); // 假设关联关系已正确映射
                return cb.like(cb.lower(rolesJoin.get("name")), like.toLowerCase());
            });
        }
        Instant start = toInstant(startDate);
        Instant end = toInstant(endDate);
        if (start != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start)); // 对齐: SQL users.created_at → Entity.createdAt
        }
        if (end != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end)); // 对齐: SQL users.created_at → Entity.createdAt
        }

        Specification<UsersEntity> where = Specification.allOf(specs);
        return userRepository.findAll(where);
    }

    /**
     * Placeholder for compatibility; no loan concept in the new schema here.
     */
    // Placeholder for compatibility; no loan concept in the new schema here.
    public long countLoansByUserId(Long userId) { // 对齐: 保持方法签名不变，因上层可能依赖
        return 0L;
    }
}