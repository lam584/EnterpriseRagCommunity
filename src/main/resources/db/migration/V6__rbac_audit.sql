-- RBAC 审计与高权限 step-up 邮箱用途扩展
SET NAMES utf8mb4;

-- 1) 扩展 email_verifications.purpose 枚举：新增 ADMIN_STEP_UP
ALTER TABLE email_verifications
    MODIFY COLUMN purpose ENUM(
        'VERIFY_EMAIL',
        'PASSWORD_RESET',
        'REGISTER',
        'LOGIN_2FA',
        'LOGIN_2FA_PREFERENCE',
        'CHANGE_PASSWORD',
        'CHANGE_EMAIL',
        'CHANGE_EMAIL_OLD',
        'TOTP_ENABLE',
        'TOTP_DISABLE',
        'ADMIN_STEP_UP'
    ) NOT NULL COMMENT '用途';

-- 2) RBAC 变更审计表
CREATE TABLE rbac_audit_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    actor_user_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID',
    action VARCHAR(64) NOT NULL COMMENT '动作(例如 PERMISSION_CREATE/ROLE_MATRIX_REPLACE/USER_ROLES_ASSIGN)',
    target_type VARCHAR(64) NOT NULL COMMENT '目标类型(permissions/role_permissions/user_role_links 等)',
    target_id VARCHAR(191) NULL COMMENT '目标标识(可为 id 或复合键字符串)',
    reason VARCHAR(255) NULL COMMENT '变更原因(建议必填)',
    diff_json LONGTEXT NULL COMMENT '变更摘要(JSON)',
    request_ip VARCHAR(64) NULL COMMENT '请求IP',
    user_agent VARCHAR(255) NULL COMMENT 'UA 信息',
    request_id VARCHAR(128) NULL COMMENT '请求ID(可选)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    KEY idx_rbac_audit_actor_time (actor_user_id, created_at),
    KEY idx_rbac_audit_target_time (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC 变更审计日志';

