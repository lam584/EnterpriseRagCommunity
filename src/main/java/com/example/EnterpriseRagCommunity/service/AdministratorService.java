package com.example.EnterpriseRagCommunity.service;

// 修改批次: 1 | 修改依据: src/main/java/com/example/EnterpriseRagCommunity/entity/access/UsersEntity.java, src/main/java/com/example/EnterpriseRagCommunity/repository/access/UsersRepository.java
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AdministratorService {
    private static final Logger logger = LoggerFactory.getLogger(AdministratorService.class);

    @Autowired
    private UsersRepository userRepository; // 对齐: Repository层已改为access包

    public List<UsersEntity> findAll() { // 对齐: 返回类型改为UsersEntity
        logger.debug("正在查找所有管理员...");
        List<UsersEntity> users = userRepository.findAll();
        logger.debug("找到 {} 个用户。", users.size());
        return users;
    }

    public Optional<UsersEntity> findById(Long id) { // 对齐: 返回类型改为UsersEntity
        logger.debug("正在通过ID查找用户，id={}", id);
        Optional<UsersEntity> user = userRepository.findById(id); // 修复: 使用基础findById方法，因编译错误要求匹配findByEmail签名模式
        logger.debug("查找结果 found={}", user.isPresent());
        return user;
    }

    public Optional<UsersEntity> findByUsername(String username) { // 对齐: 返回类型改为UsersEntity, 参数username实际对应SQL users.email
        logger.debug("正在通过用户名/邮箱查找用户，input={}", username);
        // 直接使用邮箱查找（新的User实体使用email作为登录凭证）
        Optional<UsersEntity> user = userRepository.findByEmailAndIsDeletedFalse(username); // 修复: 使用软删除感知方法
        logger.debug("查找结果 found={}", user.isPresent());
        return user;
    }

    public UsersEntity save(UsersEntity user) { // 对齐: 参数和返回类型改为UsersEntity
        logger.debug("正在保存用户信息，userId={}", user.getId());
        UsersEntity savedUser = userRepository.save(user);
        logger.debug("用户保存成功，id={}", savedUser.getId());
        return savedUser;
    }

    public void delete(Long id) {
        logger.debug("正在删除用户，id={}", id);
        userRepository.deleteById(id);
        logger.debug("用户删除成功，id={}", id);
    }

    /**
     * 统计系统中用户账户的数量
     * @return 用户账户数量
     */
    public long countAdministrators() {
        logger.debug("正在统计用户账户数量...");
        long count = userRepository.count();
        logger.debug("当前用户账户数量={}", count);
        return count;
    }
}













