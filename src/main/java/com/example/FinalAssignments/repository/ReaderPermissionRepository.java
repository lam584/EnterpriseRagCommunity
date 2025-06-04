package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.ReaderPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReaderPermissionRepository extends JpaRepository<ReaderPermission, Long> {
    // 可以根据需要添加自定义查询方法
    ReaderPermission findByRoles(String roles);
}
