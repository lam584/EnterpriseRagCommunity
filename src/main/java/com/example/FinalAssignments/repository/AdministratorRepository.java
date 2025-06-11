package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// 该接口用于定义管理员实体的数据库操作
@Repository
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {
    // 根据管理员账号查找管理员信息
    Optional<Administrator> findByAccount(String account);

    // 根据管理员邮箱查找管理员信息
    Optional<Administrator> findByEmail(String email);
}
