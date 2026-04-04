package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.AdministratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理员设置管理器
 * 同时负责:
 * 1. 存储是否需要初始化管理员的状态，避免每次登录都查询数据库
 * 2. 应用启动时检查是否存在管理员账户，如果不存在，则设置初始化状态为需要初始设置
 */
@Component
@Order(1) // 确保在其他 ApplicationRunner 之前运行
@RequiredArgsConstructor
public class AdminSetupManager implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdminSetupManager.class);

    private final AtomicBoolean setupRequired = new AtomicBoolean(false);
    private final AdministratorService administratorService;

    /**
     * 获取是否需要初始设置
     */
    public boolean isSetupRequired() {
        return setupRequired.get();
    }

    /**
     * 设置初始化状态
     */
    public void setSetupRequired(boolean required) {
        setupRequired.set(required);
    }

    /**
     * 应用启动时自动运行，检查管理员账户
     */
    @Override
    public void run(ApplicationArguments args) {
        // 检查数据库中是否存在管理员账户
        long adminCount = administratorService.countAdministrators();

        if (adminCount < 1) {
            // 如果没有管理员账户，设置状态为需要初始设置
            setSetupRequired(true);
            logger.info("未检测到管理员账户，系统需要初始化设置");
        } else {
            setSetupRequired(false);
            logger.info("检测到 {} 个管理员账户，无需初始化设置", adminCount);
        }
    }
}
