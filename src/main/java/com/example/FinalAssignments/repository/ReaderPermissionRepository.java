package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.ReaderPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 该接口用于定义读者权限实体的数据库操作
@Repository
public interface ReaderPermissionRepository extends JpaRepository<ReaderPermission, Long> {
    // 根据角色名称查找读者权限信息
    ReaderPermission findByRoles(String roles);
}
