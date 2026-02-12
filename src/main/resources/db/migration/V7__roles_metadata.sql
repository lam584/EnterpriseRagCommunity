-- 角色元数据表：统一 roleId -> roleName 的权威来源
SET NAMES utf8mb4;

CREATE TABLE roles (
    role_id BIGINT UNSIGNED PRIMARY KEY COMMENT '角色ID',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名',
    description VARCHAR(255) NULL COMMENT '角色说明',
    risk_level ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW' COMMENT '风险级别',
    builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    immutable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否不可变更(防止锁死/夺权)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_roles_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色元数据';

-- 从 role_permissions 回填（若存在多种 role_name，取字典序最小）
INSERT IGNORE INTO roles(role_id, role_name, builtin, immutable)
SELECT
    rp.role_id,
    MIN(rp.role_name) AS role_name,
    CASE WHEN rp.role_id = 1 THEN 1 ELSE 0 END AS builtin,
    CASE WHEN rp.role_id = 1 THEN 1 ELSE 0 END AS immutable
FROM role_permissions rp
WHERE rp.role_name IS NOT NULL AND rp.role_name <> ''
GROUP BY rp.role_id;

-- 确保 USER(1) 存在
INSERT IGNORE INTO roles(role_id, role_name, builtin, immutable)
VALUES (1, 'USER', 1, 1);

