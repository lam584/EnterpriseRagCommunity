package com.example.FinalAssignments.repository;

import com.example.FinalAssignments.entity.Reader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReaderRepository extends JpaRepository<Reader, Long> {
    
    @Query("SELECT r FROM Reader r WHERE " +
           "(:account IS NULL OR r.account LIKE %:account%) AND " +
           "(:phone IS NULL OR r.phone LIKE %:phone%) AND " +
           "(:email IS NULL OR r.email LIKE %:email%)")
    List<Reader> search(@Param("account") String account, 
                        @Param("phone") String phone, 
                        @Param("email") String email);

    // 添加根据账号查询用户的方法
    Optional<Reader> findByAccount(String account);

    // 添加根据邮箱查询用户的方法
    Optional<Reader> findByEmail(String email);

    // 添加根据手机号查询用户的方法
    Optional<Reader> findByPhone(String phone);
}
