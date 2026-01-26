-- Semantic translate config + history (admin-managed, DB-backed)

CREATE TABLE IF NOT EXISTS semantic_translate_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  system_prompt VARCHAR(512) NOT NULL,
  prompt_template LONGTEXT NOT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  max_content_chars INT NOT NULL DEFAULT 8000,
  history_enabled TINYINT(1) NOT NULL DEFAULT 1,
  history_keep_days INT NULL,
  history_keep_rows INT NULL,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译配置（LLM）';

CREATE INDEX idx_semantic_translate_config_updated_at ON semantic_translate_config(updated_at);

CREATE TABLE IF NOT EXISTS semantic_translate_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  source_type VARCHAR(16) NOT NULL COMMENT 'POST|COMMENT',
  source_id BIGINT NOT NULL,
  target_lang VARCHAR(32) NOT NULL,

  source_hash CHAR(64) NOT NULL,
  config_hash CHAR(64) NOT NULL,

  source_title_excerpt VARCHAR(160) NULL,
  source_content_excerpt VARCHAR(512) NULL,

  translated_title VARCHAR(512) NULL,
  translated_markdown LONGTEXT NOT NULL,

  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  latency_ms BIGINT NULL,
  prompt_version INT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译历史记录（带缓存键）';

CREATE UNIQUE INDEX uq_translate_cache_key
  ON semantic_translate_history(source_type, source_id, target_lang, source_hash, config_hash);

CREATE INDEX idx_translate_history_created_at ON semantic_translate_history(created_at);
CREATE INDEX idx_translate_history_user_id_created_at ON semantic_translate_history(user_id, created_at);
