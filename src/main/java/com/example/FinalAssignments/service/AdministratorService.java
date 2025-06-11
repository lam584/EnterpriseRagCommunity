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
        System.out.println("调试信息: 正在查找所有管理员...");
        List<Administrator> administrators = administratorRepository.findAll();
        System.out.println("调试信息: 找到 " + administrators.size() + " 个管理员。");
        return administrators;
    }

    public Optional<Administrator> findById(Long id) {
        System.out.println("调试信息: 正在通过ID查找管理员，ID=" + id);
        Optional<Administrator> administrator = administratorRepository.findById(id);
        System.out.println("调试信息: 查找结果 " + (administrator.isPresent() ? "找到管理员。" : "未找到管理员。"));
        return administrator;
    }

    public Optional<Administrator> findByUsername(String username) {
        System.out.println("调试信息: 正在通过用户名查找管理员，用户名=" + username);
        // 先尝试使用账号查找
        Optional<Administrator> adminByAccount = administratorRepository.findByAccount(username);
        if (adminByAccount.isPresent()) {
            System.out.println("调试信息: 通过账号找到管理员。");
            return adminByAccount;
        }

        // 如果账号查找失败，再尝试使用邮箱查找
        System.out.println("调试信息: 未通过账号找到管理员，尝试通过邮箱查找...");
        Optional<Administrator> adminByEmail = administratorRepository.findByEmail(username);
        System.out.println("调试信息: 查找结果 " + (adminByEmail.isPresent() ? "通过邮箱找到管理员。" : "未找到管理员。"));
        return adminByEmail;
    }

    public Administrator save(Administrator administrator) {
        System.out.println("调试信息: 正在保存管理员信息，管理员=" + administrator);
        Administrator savedAdministrator = administratorRepository.save(administrator);
        System.out.println("调试信息: 管理员保存成功，ID=" + savedAdministrator.getId());
        return savedAdministrator;
    }

    public void delete(Long id) {
        System.out.println("调试信息: 正在删除管理员，ID=" + id);
        administratorRepository.deleteById(id);
        System.out.println("调试信息: 管理员删除成功，ID=" + id);
    }

    /**
     * 统计系统中管理员账户的数量
     * @return 管理员账户数量
     */
    public long countAdministrators() {
        System.out.println("调试信息: 正在统计管理员账户数量...");
        long count = administratorRepository.count();
        System.out.println("调试信息: 当前管理员账户数量=" + count);
        return count;
    }
}
