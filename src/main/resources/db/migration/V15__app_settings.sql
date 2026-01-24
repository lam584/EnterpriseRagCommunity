-- Global application settings (DB-backed, runtime configurable from admin UI).

CREATE TABLE app_settings (
  k VARCHAR(64) NOT NULL COMMENT '设置键',
  v VARCHAR(255) NOT NULL COMMENT '设置值(字符串)',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用级设置（全局）';

-- Default role assigned on user registration (AuthController.register).
-- Role IDs are inferred from role_permissions/user_role_links, and role_id=1 is reserved for USER.
INSERT IGNORE INTO app_settings(k, v) VALUES ('default_register_role_id', '1');

