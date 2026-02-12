-- user_role_links：增加作用域/有效期等字段（为版块自治与临时授权做准备）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE user_role_links_new (
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    scope_type ENUM('GLOBAL','TENANT','BOARD') NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域类型',
    scope_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '作用域ID（GLOBAL 固定为 0）',
    expires_at DATETIME(3) NULL COMMENT '到期时间（NULL 表示永久）',
    assigned_by BIGINT UNSIGNED NULL COMMENT '授予人用户ID',
    assigned_reason VARCHAR(255) NULL COMMENT '授予原因/工单号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (user_id, role_id, scope_type, scope_id),
    KEY idx_url_role_id (role_id),
    CONSTRAINT fk_url_user_v2 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与角色关联表（含作用域/有效期）';

INSERT INTO user_role_links_new(user_id, role_id, scope_type, scope_id, created_at, updated_at)
SELECT user_id, role_id, 'GLOBAL', 0, NOW(3), NOW(3)
FROM user_role_links;

DROP TABLE user_role_links;
RENAME TABLE user_role_links_new TO user_role_links;

SET FOREIGN_KEY_CHECKS = 1;
