-- users：RBAC 权限版本号（用于会话权限刷新/节流）
SET NAMES utf8mb4;

ALTER TABLE users
    ADD COLUMN access_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'RBAC 权限版本号';

CREATE INDEX idx_users_access_version ON users(access_version);

