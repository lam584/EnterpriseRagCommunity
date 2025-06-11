package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// 该接口用于定义管理员权限实体的数据库操作
@Repository
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {
    // 根据角色名称查找管理员权限信息
    Optional<AdminPermission> findByRoles(String roles);

    // 检查是否存在指定角色的权限
    boolean existsByRoles(String roles);
}
