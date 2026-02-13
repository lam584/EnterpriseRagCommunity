/*
  V10：全局日志中心（访问日志 + 归档/清理默认配置）

  说明：
  - access_logs：记录每个 /api 请求的安全访问日志（用于取证与追溯）。
  - *_archive：用于“归档到归档表”模式，避免直接删除造成证据缺失。
  - app_settings：新增日志保留策略配置项，默认关闭自动清理/归档任务。
*/

CREATE TABLE access_logs (
                           id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                           tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                           user_id BIGINT UNSIGNED NULL COMMENT '用户ID（若可识别）',
                           username VARCHAR(191) NULL COMMENT '用户账号（邮箱/用户名）',
                           method VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
                           path VARCHAR(512) NOT NULL COMMENT '请求路径（不含域名）',
                           query_string VARCHAR(1024) NULL COMMENT '查询串（已脱敏/裁剪）',
                           status_code INT NULL COMMENT '响应状态码',
                           latency_ms INT NULL COMMENT '耗时（毫秒）',
                           client_ip VARCHAR(64) NULL COMMENT '网络源地址（解析 Forwarded/XFF）',
                           client_port INT NULL COMMENT '网络源端口',
                           server_ip VARCHAR(64) NULL COMMENT '网络目标地址（本机）',
                           server_port INT NULL COMMENT '网络目标端口（本机）',
                           scheme VARCHAR(16) NULL COMMENT 'scheme（http/https）',
                           host VARCHAR(255) NULL COMMENT 'Host',
                           request_id VARCHAR(64) NULL COMMENT '请求ID（关联）',
                           trace_id VARCHAR(64) NULL COMMENT '链路ID（关联）',
                           user_agent VARCHAR(512) NULL COMMENT 'User-Agent（裁剪）',
                           referer VARCHAR(512) NULL COMMENT 'Referer（裁剪）',
                           details JSON NULL COMMENT '扩展详情（JSON）',
                           created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                           KEY idx_access_created (created_at),
                           KEY idx_access_user (user_id),
                           KEY idx_access_client_ip (client_ip),
                           KEY idx_access_request_id (request_id),
                           KEY idx_access_trace_id (trace_id),
                           KEY idx_access_path (path(191)),
                           CONSTRAINT fk_access_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                           CONSTRAINT fk_access_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HTTP访问日志（安全取证）';

CREATE TABLE access_logs_archive (
                                   id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                   tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                                   user_id BIGINT UNSIGNED NULL COMMENT '用户ID（若可识别）',
                                   username VARCHAR(191) NULL COMMENT '用户账号（邮箱/用户名）',
                                   method VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
                                   path VARCHAR(512) NOT NULL COMMENT '请求路径（不含域名）',
                                   query_string VARCHAR(1024) NULL COMMENT '查询串（已脱敏/裁剪）',
                                   status_code INT NULL COMMENT '响应状态码',
                                   latency_ms INT NULL COMMENT '耗时（毫秒）',
                                   client_ip VARCHAR(64) NULL COMMENT '网络源地址（解析 Forwarded/XFF）',
                                   client_port INT NULL COMMENT '网络源端口',
                                   server_ip VARCHAR(64) NULL COMMENT '网络目标地址（本机）',
                                   server_port INT NULL COMMENT '网络目标端口（本机）',
                                   scheme VARCHAR(16) NULL COMMENT 'scheme（http/https）',
                                   host VARCHAR(255) NULL COMMENT 'Host',
                                   request_id VARCHAR(64) NULL COMMENT '请求ID（关联）',
                                   trace_id VARCHAR(64) NULL COMMENT '链路ID（关联）',
                                   user_agent VARCHAR(512) NULL COMMENT 'User-Agent（裁剪）',
                                   referer VARCHAR(512) NULL COMMENT 'Referer（裁剪）',
                                   details JSON NULL COMMENT '扩展详情（JSON）',
                                   created_at DATETIME(3) NOT NULL COMMENT '原始创建时间',
                                   archived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '归档时间',
                                   KEY idx_access_arc_created (created_at),
                                   KEY idx_access_arc_user (user_id),
                                   KEY idx_access_arc_client_ip (client_ip),
                                   KEY idx_access_arc_request_id (request_id),
                                   KEY idx_access_arc_trace_id (trace_id),
                                   KEY idx_access_arc_archived_at (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HTTP访问日志归档表';

CREATE TABLE audit_logs_archive (
                                  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                                  tenant_id BIGINT UNSIGNED NULL COMMENT '租户ID',
                                  actor_user_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID',
                                  action VARCHAR(64) NOT NULL COMMENT '动作名称',
                                  entity_type VARCHAR(64) NOT NULL COMMENT '实体类型',
                                  entity_id BIGINT UNSIGNED NULL COMMENT '实体ID',
                                  result ENUM('SUCCESS','FAIL') NOT NULL COMMENT '结果',
                                  details JSON NULL COMMENT '详情（JSON）',
                                  created_at DATETIME(3) NOT NULL COMMENT '原始创建时间',
                                  archived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '归档时间',
                                  KEY idx_audit_arc_entity (entity_type, entity_id),
                                  KEY idx_audit_arc_actor (actor_user_id),
                                  KEY idx_audit_arc_created (created_at),
                                  KEY idx_audit_arc_archived (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志归档表';

INSERT IGNORE INTO app_settings(k, v) VALUES ('monitor.logs.retention.enabled', 'false');
INSERT IGNORE INTO app_settings(k, v) VALUES ('monitor.logs.retention.keepDays', '90');
INSERT IGNORE INTO app_settings(k, v) VALUES ('monitor.logs.retention.mode', 'ARCHIVE_TABLE');

