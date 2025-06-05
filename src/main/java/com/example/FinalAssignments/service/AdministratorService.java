package com.example.FinalAssignments.service;

import com.example.FinalAssignments.entity.Administrator;
import com.example.FinalAssignments.repository.AdministratorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AdministratorService {

    @Autowired
    private AdministratorRepository administratorRepository;

    public List<Administrator> findAll() {
        return administratorRepository.findAll();
    }

    public Optional<Administrator> findById(Long id) {
        return administratorRepository.findById(id);
    }

    public Optional<Administrator> findByUsername(String username) {
        // 先尝试使用账号查找
        Optional<Administrator> adminByAccount = administratorRepository.findByAccount(username);
        if (adminByAccount.isPresent()) {
            return adminByAccount;
        }

        // 如果账号查找失败，再尝试使用邮箱查找
        return administratorRepository.findByEmail(username);
    }

    public Administrator save(Administrator administrator) {
        return administratorRepository.save(administrator);
    }

    public void delete(Long id) {
        administratorRepository.deleteById(id);
    }

    /**
     * 统计系统中管理员账户的数量
     * @return 管理员账户数量
     */
    public long countAdministrators() {
        return administratorRepository.count();
    }
}
