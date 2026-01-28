-- Post AI summary config + history + per-post summary state (admin-managed, DB-backed)

CREATE TABLE IF NOT EXISTS post_summary_gen_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  prompt_template LONGTEXT NOT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  max_content_chars INT NOT NULL DEFAULT 4000,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子摘要生成配置（LLM）';

CREATE INDEX idx_post_summary_gen_config_updated_at ON post_summary_gen_config(updated_at);

CREATE TABLE IF NOT EXISTS post_summary_gen_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  post_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'SUCCESS|FAILED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  applied_max_content_chars INT NOT NULL,
  latency_ms BIGINT NULL,
  prompt_version INT NULL,
  error_message LONGTEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子摘要生成日志';

CREATE INDEX idx_post_summary_gen_history_created_at ON post_summary_gen_history(created_at);
CREATE INDEX idx_post_summary_gen_history_post_id_created_at ON post_summary_gen_history(post_id, created_at);

CREATE TABLE IF NOT EXISTS post_ai_summary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'SUCCESS|FAILED|PENDING',
  summary_title VARCHAR(512) NULL,
  summary_text LONGTEXT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  applied_max_content_chars INT NULL,
  latency_ms BIGINT NULL,
  generated_at DATETIME(3) NULL,
  error_message LONGTEXT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子当前AI摘要状态';

CREATE UNIQUE INDEX uq_post_ai_summary_post_id ON post_ai_summary(post_id);
CREATE INDEX idx_post_ai_summary_updated_at ON post_ai_summary(updated_at);
