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
        return readerPermissionRepository.findAll();
    }

    public Optional<ReaderPermission> findById(Long id) {
        return readerPermissionRepository.findById(id);
    }

    public ReaderPermission findByRoles(String roles) {
        return readerPermissionRepository.findByRoles(roles);
    }

    public ReaderPermission save(ReaderPermission readerPermission) {
        // 设置创建和更新时间
        LocalDateTime now = LocalDateTime.now();

        if (readerPermission.getId() == null) {
            // 新建时设置创建时间
            readerPermission.setCreatedAt(now);
        } else {
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

        return readerPermissionRepository.save(readerPermission);
    }

    public void delete(Long id) {
        readerPermissionRepository.deleteById(id);
    }
}
