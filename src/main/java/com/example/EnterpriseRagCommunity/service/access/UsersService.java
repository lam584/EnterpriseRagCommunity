package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.UsersCreateDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersQueryDTO;
import com.example.EnterpriseRagCommunity.dto.access.UsersUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final UsersRepository usersRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final RolesRepository rolesRepository;
    private final RbacAuditService rbacAuditService;
    private final AuditLogWriter auditLogWriter;
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
        return update(dto, null);
    }

    @Transactional
    public UsersEntity update(UsersUpdateDTO dto, Long actorUserId) {
        UsersEntity entity = usersRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ensureSelfUnavailableActionAllowed(entity.getId(), actorUserId, willBecomeUnavailable(entity, dto));

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
        delete(id, null);
    }

    @Transactional
    public void delete(Long id, Long actorUserId) {
        ensureSelfUnavailableActionAllowed(id, actorUserId, true);
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
        hardDelete(id, null);
    }

    @Transactional
    public void hardDelete(Long id, Long actorUserId) {
        if (id == null) {
            throw new RuntimeException("User id is required");
        }

        ensureSelfUnavailableActionAllowed(id, actorUserId, true);

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
            
            if (!Boolean.TRUE.equals(queryDTO.getIncludeDeleted())) {
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
    public UsersEntity banUser(Long userId, Long actorUserId, String actorName, String reason, String sourceType, Long sourceId) {
        if (userId == null) throw new IllegalArgumentException("userId 不能为空");
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new IllegalArgumentException("reason 不能为空");

        ensureSelfUnavailableActionAllowed(userId, actorUserId, true);

        UsersEntity entity = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (Boolean.TRUE.equals(entity.getIsDeleted()) || entity.getStatus() == AccountStatus.DELETED) {
            throw new IllegalStateException("用户已删除，不能封禁");
        }

        entity.setStatus(AccountStatus.DISABLED);
        entity.setSessionInvalidatedAt(LocalDateTime.now());
        bumpAccessVersion(entity);
        entity.setMetadata(upsertBanMetadata(entity.getMetadata(), true, actorUserId, actorName, r, sourceType, sourceId));
        entity.setUpdatedAt(LocalDateTime.now());
        UsersEntity saved = usersRepository.save(entity);

        auditLogWriter.write(
                actorUserId,
                actorName,
                "USER_BAN",
                "USERS",
                userId,
                AuditResult.SUCCESS,
                "封禁用户",
                "ban-" + UUID.randomUUID(),
                Map.of(
                        "reason", r,
                        "sourceType", sourceType,
                        "sourceId", sourceId
                )
        );
        return saved;
    }

    @Transactional
    public UsersEntity unbanUser(Long userId, Long actorUserId, String actorName, String reason) {
        if (userId == null) throw new IllegalArgumentException("userId 不能为空");
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new IllegalArgumentException("reason 不能为空");

        UsersEntity entity = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (Boolean.TRUE.equals(entity.getIsDeleted()) || entity.getStatus() == AccountStatus.DELETED) {
            throw new IllegalStateException("用户已删除，不能解封");
        }

        if (entity.getStatus() == AccountStatus.DISABLED) {
            entity.setStatus(AccountStatus.ACTIVE);
        }
        entity.setSessionInvalidatedAt(LocalDateTime.now());
        bumpAccessVersion(entity);
        entity.setMetadata(upsertBanMetadata(entity.getMetadata(), false, actorUserId, actorName, r, null, null));
        entity.setUpdatedAt(LocalDateTime.now());
        UsersEntity saved = usersRepository.save(entity);

        auditLogWriter.write(
                actorUserId,
                actorName,
                "USER_UNBAN",
                "USERS",
                userId,
                AuditResult.SUCCESS,
                "解封用户",
                "unban-" + UUID.randomUUID(),
                Map.of("reason", r)
        );
        return saved;
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
        if (roleIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("roleIds must not contain null");
        }

        // De-duplicate to avoid redundant inserts
        List<Long> normalizedRoleIds = roleIds.stream().distinct().collect(Collectors.toList());

        if (!normalizedRoleIds.isEmpty()) {
            List<RolesEntity> found = rolesRepository.findAllById(normalizedRoleIds);
            Set<Long> foundIds = found.stream().map(RolesEntity::getRoleId).collect(Collectors.toSet());
            List<Long> missing = normalizedRoleIds.stream().filter(id -> !foundIds.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("roleIds 包含不存在的角色ID: " + missing);
            }
        }

        // Remove existing roles first (empty list means clear all roles)
        List<UserRoleLinksEntity> existingLinks = userRoleLinksRepository.findByUserId(userId);
        List<Long> beforeRoleIds = existingLinks.stream().map(UserRoleLinksEntity::getRoleId).filter(Objects::nonNull).distinct().toList();
        userRoleLinksRepository.deleteAll(existingLinks);

        if (normalizedRoleIds.isEmpty()) {
            touchUserAccess(userId);
            rbacAuditService.record("USER_ROLES_ASSIGN", "user_role_links", "userId=" + userId, beforeRoleIds, List.of());
            return;
        }

        // Add new roles (不再校验 roleId 是否存在于 user_roles)
        List<UserRoleLinksEntity> newLinks = normalizedRoleIds.stream().map(roleId -> {
            UserRoleLinksEntity link = new UserRoleLinksEntity();
            link.setUserId(userId);
            link.setRoleId(roleId);
            link.setScopeType("GLOBAL");
            link.setScopeId(0L);
            return link;
        }).collect(Collectors.toList());

        userRoleLinksRepository.saveAll(newLinks);
        touchUserAccess(userId);
        rbacAuditService.record("USER_ROLES_ASSIGN", "user_role_links", "userId=" + userId, beforeRoleIds, normalizedRoleIds);
    }

    private void touchUserAccess(Long userId) {
        if (userId == null) return;
        try {
            UsersEntity u = usersRepository.findById(userId).orElse(null);
            if (u == null) return;
            bumpAccessVersion(u);
            u.setUpdatedAt(LocalDateTime.now());
            usersRepository.save(u);
        } catch (Exception ignored) {
        }
    }

    private static void bumpAccessVersion(UsersEntity u) {
        long v = u.getAccessVersion() == null ? 0L : u.getAccessVersion();
        u.setAccessVersion(v + 1L);
    }

    private static Map<String, Object> upsertBanMetadata(
            Map<String, Object> input,
            boolean active,
            Long actorUserId,
            String actorName,
            String reason,
            String sourceType,
            Long sourceId
    ) {
        Map<String, Object> meta = input == null ? new LinkedHashMap<>() : new LinkedHashMap<>(input);
        Map<String, Object> ban = new LinkedHashMap<>();
        Object existing = meta.get("ban");
        if (existing instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                ban.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        ban.put("active", active);
        if (active) {
            ban.put("bannedAt", LocalDateTime.now().toString());
            ban.put("bannedById", actorUserId);
            ban.put("bannedBy", actorName);
            ban.put("reason", reason);
            if (sourceType != null && !sourceType.isBlank()) ban.put("sourceType", sourceType.trim());
            if (sourceId != null) ban.put("sourceId", sourceId);
        } else {
            ban.put("unbannedAt", LocalDateTime.now().toString());
            ban.put("unbannedById", actorUserId);
            ban.put("unbannedBy", actorName);
            ban.put("unbanReason", reason);
        }
        meta.put("ban", ban);
        return meta;
    }

    public List<UserRoleLinksEntity> getUserRoles(Long userId) {
        return userRoleLinksRepository.findByUserId(userId);
    }

    private void ensureSelfUnavailableActionAllowed(Long targetUserId, Long actorUserId, boolean makeUnavailable) {
        if (!makeUnavailable || targetUserId == null || actorUserId == null || !Objects.equals(targetUserId, actorUserId)) {
            return;
        }
        long availableUserCount = usersRepository.countByIsDeletedFalse();
        if (availableUserCount <= 1) {
            throw new IllegalStateException("系统仅剩最后一个未软删除账号，不能删除、禁用或封禁当前账号");
        }
    }

    private boolean willBecomeUnavailable(UsersEntity entity, UsersUpdateDTO dto) {
        boolean nextDeleted = dto.getIsDeleted() != null ? dto.getIsDeleted() : Boolean.TRUE.equals(entity.getIsDeleted());
        AccountStatus nextStatus = dto.getStatus() != null ? dto.getStatus() : entity.getStatus();
        return nextDeleted || nextStatus == AccountStatus.DISABLED || nextStatus == AccountStatus.DELETED;
    }
}
