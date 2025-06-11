package com.example.FinalAssignments.service;

import com.example.FinalAssignments.entity.ReaderPermission;
import com.example.FinalAssignments.repository.ReaderPermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReaderPermissionService {

    @Autowired
    private ReaderPermissionRepository readerPermissionRepository;

    public List<ReaderPermission> findAll() {
        System.out.println("[调试] 正在查询所有读者权限信息");
        return readerPermissionRepository.findAll();
    }

    public Optional<ReaderPermission> findById(Long id) {
        System.out.println("[调试] 正在根据ID查询读者权限信息，ID: " + id);
        return readerPermissionRepository.findById(id);
    }

    public ReaderPermission findByRoles(String roles) {
        System.out.println("[调试] 正在根据角色查询读者权限信息，角色: " + roles);
        return readerPermissionRepository.findByRoles(roles);
    }

    public ReaderPermission save(ReaderPermission readerPermission) {
        System.out.println("[调试] 正在保存读者权限信息，权限信息: " + readerPermission);
        // 设置创建和更新时间
        LocalDateTime now = LocalDateTime.now();

        if (readerPermission.getId() == null) {
            System.out.println("[调试] 新建权限，设置创建时间");
            // 新建时设置创建时间
            readerPermission.setCreatedAt(now);
        } else {
            System.out.println("[调试] 更新权限信息，权限ID: " + readerPermission.getId());
            // 更新时保留原创建时间
            Optional<ReaderPermission> existingPermission = readerPermissionRepository.findById(readerPermission.getId());
            if (existingPermission.isPresent()) {
                readerPermission.setCreatedAt(existingPermission.get().getCreatedAt());
            } else {
                readerPermission.setCreatedAt(now);
            }
        }
        // 更新时间总是设置为当前时间
        readerPermission.setUpdatedAt(now);

        System.out.println("[调试] 保存成功，权限信息: " + readerPermission);
        return readerPermissionRepository.save(readerPermission);
    }

    public void delete(Long id) {
        System.out.println("[调试] 正在删除读者权限信息，权限ID: " + id);
        readerPermissionRepository.deleteById(id);
        System.out.println("[调试] 删除成功，权限ID: " + id);
    }
}
