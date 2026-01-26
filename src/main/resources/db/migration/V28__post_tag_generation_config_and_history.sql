-- Post tag generation (topic tags) config + history (admin-managed, DB-backed)

CREATE TABLE IF NOT EXISTS post_tag_gen_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  system_prompt VARCHAR(512) NOT NULL,
  prompt_template LONGTEXT NOT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  default_count INT NOT NULL DEFAULT 5,
  max_count INT NOT NULL DEFAULT 10,
  max_content_chars INT NOT NULL DEFAULT 4000,
  history_enabled TINYINT(1) NOT NULL DEFAULT 1,
  history_keep_days INT NULL,
  history_keep_rows INT NULL,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子主题标签生成配置（LLM）';

CREATE INDEX idx_post_tag_gen_config_updated_at ON post_tag_gen_config(updated_at);

CREATE TABLE IF NOT EXISTS post_tag_gen_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  board_name VARCHAR(128) NULL,
  title_excerpt VARCHAR(256) NULL,
  requested_count INT NOT NULL,
  applied_max_content_chars INT NOT NULL,
  content_len INT NOT NULL,
  content_excerpt VARCHAR(512) NULL,
  tags_json JSON NOT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  latency_ms BIGINT NULL,
  prompt_version INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子主题标签生成历史记录';

CREATE INDEX idx_post_tag_gen_history_created_at ON post_tag_gen_history(created_at);
CREATE INDEX idx_post_tag_gen_history_user_id_created_at ON post_tag_gen_history(user_id, created_at);

