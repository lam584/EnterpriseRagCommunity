package com.example.NewsHubCommunity.service;

import com.example.NewsHubCommunity.dto.UserRoleDTOs.CreateUserRoleDTO;
import com.example.NewsHubCommunity.dto.UserRoleDTOs.UpdateUserRoleDTO;
import com.example.NewsHubCommunity.dto.UserRoleDTOs.UserRoleDTO;
import com.example.NewsHubCommunity.entity.UserRole;
import com.example.NewsHubCommunity.repository.UserRoleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserRole Service 接口 + 实现（合并在同一个文件中）
 */
public interface UserRoleService {

    /**
     * 创建一个新的角色
     */
    UserRoleDTO create(CreateUserRoleDTO dto);

    /**
     * 更新已有角色
     */
    UserRoleDTO update(UpdateUserRoleDTO dto);

    /**
     * 删除角色
     */
    void delete(Long id);

    /**
     * 根据 ID 查询单个角色
     */
    UserRoleDTO getById(Long id);

    /**
     * 分页查询所有角色
     */
    Page<UserRoleDTO> list(Pageable pageable);
}

/**
 * 默认的 Service 实现（Spring Bean）
 */
@Service
@Transactional
class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository repo;

    public UserRoleServiceImpl(UserRoleRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserRoleDTO create(CreateUserRoleDTO dto) {
        UserRole entity = dto.toEntity();
        UserRole saved = repo.save(entity);
        return UserRoleDTO.fromEntity(saved);
    }

    @Override
    public UserRoleDTO update(UpdateUserRoleDTO dto) {
        UserRole entity = repo.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("UserRole not found: " + dto.getId()));
        dto.applyToEntity(entity);
        UserRole saved = repo.save(entity);
        return UserRoleDTO.fromEntity(saved);
    }

    @Override
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new EntityNotFoundException("UserRole not found: " + id);
        }
        repo.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRoleDTO getById(Long id) {
        UserRole entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("UserRole not found: " + id));
        return UserRoleDTO.fromEntity(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserRoleDTO> list(Pageable pageable) {
        return repo.findAll(pageable)
                .map(UserRoleDTO::fromEntity);
    }
}