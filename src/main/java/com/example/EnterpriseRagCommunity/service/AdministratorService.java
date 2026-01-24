package com.example.EnterpriseRagCommunity.service;

// 修改批次: 1 | 修改依据: src/main/java/com/example/EnterpriseRagCommunity/entity/access/UsersEntity.java, src/main/java/com/example/EnterpriseRagCommunity/repository/access/UsersRepository.java
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AdministratorService {

    @Autowired
    private UsersRepository userRepository; // 对齐: Repository层已改为access包

    public List<UsersEntity> findAll() { // 对齐: 返回类型改为UsersEntity
        System.out.println("调试信息: 正在查找所有管理员...");
        List<UsersEntity> users = userRepository.findAll();
        System.out.println("调试信息: 找到 " + users.size() + " 个用户。");
        return users;
    }

    public Optional<UsersEntity> findById(Long id) { // 对齐: 返回类型改为UsersEntity
        System.out.println("调试信息: 正在通过ID查找用户，ID=" + id);
        Optional<UsersEntity> user = userRepository.findById(id); // 修复: 使用基础findById方法，因编译错误要求匹配findByEmail签名模式
        System.out.println("调试信息: 查找结果 " + (user.isPresent() ? "找到用户。" : "未找到用户。"));
        return user;
    }

    public Optional<UsersEntity> findByUsername(String username) { // 对齐: 返回类型改为UsersEntity, 参数username实际对应SQL users.email
        System.out.println("调试信息: 正在通过用户名/邮箱查找用户，输入=" + username);
        // 直接使用邮箱查找（新的User实体使用email作为登录凭证）
        Optional<UsersEntity> user = userRepository.findByEmailAndIsDeletedFalse(username); // 修复: 使用软删除感知方法
        System.out.println("调试信息: 查找结果 " + (user.isPresent() ? "通过邮箱找到用户。" : "未找到用户。"));
        return user;
    }

    public UsersEntity save(UsersEntity user) { // 对齐: 参数和返回类型改为UsersEntity
        System.out.println("调试信息: 正在保存用户信息，用户=" + user);
        UsersEntity savedUser = userRepository.save(user);
        System.out.println("调试信息: 用户保存成功，ID=" + savedUser.getId());
        return savedUser;
    }

    public void delete(Long id) {
        System.out.println("调试信息: 正在删除用户，ID=" + id);
        userRepository.deleteById(id);
        System.out.println("调试信息: 用户删除成功，ID=" + id);
    }

    /**
     * 统计系统中用户账户的数量
     * @return 用户账户数量
     */
    public long countAdministrators() {
        System.out.println("调试信息: 正在统计用户账户数量...");
        long count = userRepository.count();
        System.out.println("调试信息: 当前用户账户数量=" + count);
        return count;
    }
}














