package com.example.EnterpriseRagCommunity.service.impl;

// 修改批次: 1 | 修改依据: src/main/java/com/example/EnterpriseRagCommunity/entity/access/UserRolesEntity.java, src/main/java/com/example/EnterpriseRagCommunity/dto/access/UserRolesCreateDTO.java
import com.example.EnterpriseRagCommunity.dto.access.UserRolesCreateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.UserRoleService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Service
@Transactional
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRolesRepository userRoleRepository;
    private final UsersRepository usersRepository;

    @Autowired
    public UserRoleServiceImpl(UserRolesRepository userRoleRepository, UsersRepository usersRepository) {
        this.userRoleRepository = userRoleRepository;
        this.usersRepository = usersRepository;
    }

    private Long resolveTenantIdOrNull(UserRolesCreateDTO dto) {
        if (dto != null && dto.getTenantId() != null) {
            return dto.getTenantId();
        }
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            String email = auth.getName();
            return usersRepository.findByEmailAndIsDeletedFalse(email)
                    .map(UsersEntity::getTenantId)
                    .map(t -> t.getId())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    // Basic mapping helpers - 对齐新UserRolesEntity字段
    private UserRolesCreateDTO toDto(UserRolesEntity entity) { // 对齐: 参数和返回类型改为新类
        if (entity == null) return null;
        UserRolesCreateDTO dto = new UserRolesCreateDTO();
        dto.setId(entity.getId());
        // dto.setCreatedAt(entity.getCreatedAt()); // createdAt 是 @JsonIgnore 字段，前端不传
        // dto.setUpdatedAt(entity.getUpdatedAt()); // updatedAt 是 @JsonIgnore 字段，前端不传
        dto.setRoles(entity.getRoles()); // 对齐: 旧name → 新roles
        dto.setNotes(entity.getNotes()); // 对齐: 旧description → 新notes
        dto.setTenantId(entity.getTenantId()); // 对齐: 直接使用tenantId字段
        // 权限字段直接映射
        dto.setCanLogin(entity.getCanLogin());
        dto.setCanViewAnnouncement(entity.getCanViewAnnouncement());
        dto.setCanViewHelpArticles(entity.getCanViewHelpArticles());
        dto.setCanResetOwnPassword(entity.getCanResetOwnPassword());
        dto.setCanComment(entity.getCanComment());
        return dto;
    }

    private void updateEntityFromDto(UserRolesCreateDTO dto, UserRolesEntity entity) { // 对齐: 参数类型改为新类
        entity.setRoles(dto.getRoles()); // 对齐: 旧name → 新roles
        entity.setNotes(dto.getNotes()); // 对齐: 旧description → 新notes
        entity.setTenantId(dto.getTenantId()); // 对齐: 直接设置tenantId
        // 权限字段直接映射
        entity.setCanLogin(dto.getCanLogin());
        entity.setCanViewAnnouncement(dto.getCanViewAnnouncement());
        entity.setCanViewHelpArticles(dto.getCanViewHelpArticles());
        entity.setCanResetOwnPassword(dto.getCanResetOwnPassword());
        entity.setCanComment(dto.getCanComment());
    }

    @Override
    public UserRolesCreateDTO create(UserRolesCreateDTO dto) { // 对齐: 参数和返回类型改为新类
        Long tenantId = resolveTenantIdOrNull(dto);
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        // 确保 entity 与唯一性检查使用解析后的 tenantId
        dto.setTenantId(tenantId);

        // Uniqueness by (tenantId, roles) - 对齐新字段名
        if (userRoleRepository.existsByTenantIdAndRoles(dto.getTenantId(), dto.getRoles())) { // 对齐: 旧existsByNameAndTenant_Id → 新existsByTenantIdAndRoles
            throw new IllegalArgumentException("Role name already exists in this tenant");
        }
        UserRolesEntity entity = new UserRolesEntity(); // 对齐: 创建新实体实例
        updateEntityFromDto(dto, entity);
        UserRolesEntity saved = userRoleRepository.save(entity); // 对齐: 保存新实体
        return toDto(saved);
    }

    @Override
    public UserRolesCreateDTO update(Long id, UserRolesCreateDTO dto) { // 修改签名，接收 id 和 dto
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        UserRolesEntity entity = userRoleRepository.findById(id) // 使用传入的 id 查询实体
                .orElseThrow(() -> new EntityNotFoundException("UserRole not found: " + id));

        // tenantId 
        Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : entity.getTenantId();
        if (tenantId == null) {
            tenantId = resolveTenantIdOrNull(dto);
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        // roles 
        String newRoles = dto.getRoles() != null ? dto.getRoles() : entity.getRoles();
        if (newRoles == null || newRoles.isBlank()) {
            throw new IllegalArgumentException("roles is required");
        }

        // 
        if (userRoleRepository.existsByTenantIdAndRolesAndIdNot(tenantId, newRoles, entity.getId())) {
            throw new IllegalArgumentException("Role name already exists in this tenant");
        }

        dto.setTenantId(tenantId);
        dto.setRoles(newRoles);

        updateEntityFromDto(dto, entity);
        UserRolesEntity saved = userRoleRepository.save(entity); // 对齐: 保存新实体
        return toDto(saved);
    }

    @Override
    public void delete(Long id) {
        UserRolesEntity entity = userRoleRepository.findById(id) // 对齐: 查询新实体
                .orElseThrow(() -> new EntityNotFoundException("UserRole not found: " + id));
        userRoleRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRolesCreateDTO getById(Long id) { // 对齐: 返回类型改为新类
        UserRolesEntity entity = userRoleRepository.findById(id) // 对齐: 查询新实体
                .orElseThrow(() -> new EntityNotFoundException("UserRole not found: " + id));
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserRolesCreateDTO> list(Pageable pageable) { // 对齐: 返回类型改为新类
        return userRoleRepository.findAll(pageable).map(this::toDto); // 对齐: 映射到新DTO
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRolesCreateDTO> listAll() {
        return userRoleRepository.findAll().stream().map(this::toDto).toList();
    }
}
