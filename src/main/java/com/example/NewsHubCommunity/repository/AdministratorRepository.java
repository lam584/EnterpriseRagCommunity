package com.example.NewsHubCommunity.repository;


import com.example.NewsHubCommunity.entity.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {
    Optional<Administrator> findByAccount(String account);
    Optional<Administrator> findByEmail(String email);
    Optional<Administrator> findByPhone(String phone);
}