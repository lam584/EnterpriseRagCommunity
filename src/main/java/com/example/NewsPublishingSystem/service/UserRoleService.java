package com.example.NewsPublishingSystem.service;


import com.example.NewsPublishingSystem.dto.PermissionDTOs;
import com.example.NewsPublishingSystem.entity.UserRole;
import com.example.NewsPublishingSystem.repository.UserRoleRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// ========== Service 接口 ==========
public interface UserRoleService {
    PermissionDTOs create(PermissionDTOs dto);
    PermissionDTOs update(PermissionDTOs dto);
    void delete(Long id);
    PermissionDTOs getById(Long id);
    Page<PermissionDTOs> list(Pageable pageable);
}

// ========== Service 实现 ==========
@Service
@Transactional
class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository repo;

    public UserRoleServiceImpl(UserRoleRepository repo) {
        this.repo = repo;
    }

    @Override
    public PermissionDTOs create(PermissionDTOs dto) {
        UserRole entity = new UserRole();
        BeanUtils.copyProperties(dto, entity);
        entity = repo.save(entity);
        PermissionDTOs result = new PermissionDTOs();
        BeanUtils.copyProperties(entity, result);
        return result;
    }

    @Override
    public PermissionDTOs update(PermissionDTOs dto) {
        UserRole entity = repo.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        BeanUtils.copyProperties(dto, entity);
        // 修改后重新 fetch
        return getById(entity.getId());
    }

    @Override
    public void delete(Long id) {
        repo.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionDTOs getById(Long id) {
        UserRole entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        PermissionDTOs dto = new PermissionDTOs();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PermissionDTOs> list(Pageable pageable) {
        Page<UserRole> page = repo.findAll(pageable);
        return page.map(entity -> {
            PermissionDTOs dto = new PermissionDTOs();
            BeanUtils.copyProperties(entity, dto);
            return dto;
        });
    }
}