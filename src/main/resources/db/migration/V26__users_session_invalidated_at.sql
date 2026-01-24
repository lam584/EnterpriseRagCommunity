ALTER TABLE users
    ADD COLUMN session_invalidated_at DATETIME(3) NULL COMMENT '强制所有会话重新登录时间点' AFTER updated_at;

