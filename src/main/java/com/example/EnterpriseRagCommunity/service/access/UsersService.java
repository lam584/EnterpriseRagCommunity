package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final UsersRepository usersRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommentsRepository commentsRepository;
    private final PostsRepository postsRepository;

    @Transactional
    public UsersEntity create(UsersCreateDTO dto) {
        UsersEntity entity = new UsersEntity();
        // entity.setTenantId(null); // Tenant handling to be added if needed
        entity.setEmail(dto.getEmail());
        entity.setUsername(dto.getUsername());
        entity.setPasswordHash(passwordEncoder.encode(dto.getPasswordHash()));
        entity.setStatus(dto.getStatus());
        entity.setIsDeleted(dto.getIsDeleted() != null ? dto.getIsDeleted() : false);
        entity.setMetadata(dto.getMetadata());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        UsersEntity saved = usersRepository.save(entity);
        if (dto.getRoleIds() != null) {
            assignRoles(saved.getId(), dto.getRoleIds());
        }
        return saved;
    }

    @Transactional
    public UsersEntity update(UsersUpdateDTO dto) {
        UsersEntity entity = usersRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (StringUtils.hasText(dto.getEmail())) {
            entity.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getUsername())) {
            entity.setUsername(dto.getUsername());
        }
        if (StringUtils.hasText(dto.getPasswordHash())) {
            entity.setPasswordHash(passwordEncoder.encode(dto.getPasswordHash()));
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getIsDeleted() != null) {
            entity.setIsDeleted(dto.getIsDeleted());
        }
        if (dto.getMetadata() != null) {
            entity.setMetadata(dto.getMetadata());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        return usersRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        UsersEntity entity = usersRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        entity.setIsDeleted(true);
        entity.setUpdatedAt(LocalDateTime.now());
        usersRepository.save(entity);
    }

    /**
     * Hard delete: physically remove the user record.
     *
     * Notes:
     * - This will delete role link rows first to avoid FK constraints.
     * - Caller should ensure the user is already soft-deleted if that's your policy.
     */
    @Transactional
    public void hardDelete(Long id) {
        if (id == null) {
            throw new RuntimeException("User id is required");
        }

        UsersEntity entity = usersRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Optional policy: only allow hard delete after soft delete
        if (!Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new RuntimeException("User must be soft-deleted before hard delete");
        }

        // Fail fast with an actionable message instead of a FK exception (comments/posts reference author_id)
        long postRefs = postsRepository.countByAuthorId(id);
        long commentRefs = commentsRepository.countByAuthorId(id);
        if (postRefs > 0 || commentRefs > 0) {
            throw new IllegalStateException(
                    "无法永久删除：该用户仍有关联内容（posts=" + postRefs + ", comments=" + commentRefs + "）。" +
                            "建议保留用户为软删除，或先处理/迁移这些内容后再删除。"
            );
        }

        userRoleLinksRepository.deleteByUserId(id);
        usersRepository.delete(entity);
    }

    public Page<UsersEntity> query(UsersQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPageNum() - 1,
                queryDTO.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<UsersEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (queryDTO.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId").get("id"), queryDTO.getTenantId()));
            }
            if (StringUtils.hasText(queryDTO.getEmail())) {
                predicates.add(cb.like(root.get("email"), "%" + queryDTO.getEmail() + "%"));
            }
            if (StringUtils.hasText(queryDTO.getUsername())) {
                predicates.add(cb.like(root.get("username"), "%" + queryDTO.getUsername() + "%"));
            }
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(queryDTO.getStatus()));
            }
            if (queryDTO.getLastLoginFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastLoginAt"), queryDTO.getLastLoginFrom()));
            }
            if (queryDTO.getLastLoginTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("lastLoginAt"), queryDTO.getLastLoginTo()));
            }
            if (queryDTO.getCreatedAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), queryDTO.getCreatedAfter()));
            }
            if (queryDTO.getCreatedBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), queryDTO.getCreatedBefore()));
            }
            
            if (Boolean.TRUE.equals(queryDTO.getIncludeDeleted())) {
                // Include deleted
            } else {
                predicates.add(cb.equal(root.get("isDeleted"), false));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return usersRepository.findAll(spec, pageable);
    }
    
    public UsersEntity getById(Long id) {
         return usersRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        // Verify user exists
        if (userId == null || !usersRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }

        if (roleIds == null) {
            throw new IllegalArgumentException("roleIds must not be null");
        }
        if (roleIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("roleIds must not contain null");
        }

        // De-duplicate to avoid redundant inserts
        List<Long> normalizedRoleIds = roleIds.stream().distinct().collect(Collectors.toList());

        // Remove existing roles first (empty list means clear all roles)
        List<UserRoleLinksEntity> existingLinks = userRoleLinksRepository.findByUserId(userId);
        userRoleLinksRepository.deleteAll(existingLinks);

        if (normalizedRoleIds.isEmpty()) {
            touchUserAccess(userId);
            return;
        }

        // Add new roles (不再校验 roleId 是否存在于 user_roles)
        List<UserRoleLinksEntity> newLinks = normalizedRoleIds.stream().map(roleId -> {
            UserRoleLinksEntity link = new UserRoleLinksEntity();
            link.setUserId(userId);
            link.setRoleId(roleId);
            return link;
        }).collect(Collectors.toList());

        userRoleLinksRepository.saveAll(newLinks);
        touchUserAccess(userId);
    }

    private void touchUserAccess(Long userId) {
        if (userId == null) return;
        try {
            UsersEntity u = usersRepository.findById(userId).orElse(null);
            if (u == null) return;
            u.setUpdatedAt(LocalDateTime.now());
            usersRepository.save(u);
        } catch (Exception ignored) {
        }
    }

    public List<UserRoleLinksEntity> getUserRoles(Long userId) {
        return userRoleLinksRepository.findByUserId(userId);
    }
}
