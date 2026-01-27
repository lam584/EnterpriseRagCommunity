-- Post risk tag generation config (admin-managed, DB-backed)

CREATE TABLE IF NOT EXISTS post_risk_tag_gen_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  system_prompt VARCHAR(512) NOT NULL,
  prompt_template LONGTEXT NOT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  max_count INT NOT NULL DEFAULT 10,
  max_content_chars INT NOT NULL DEFAULT 8000,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子风险标签生成配置（LLM）';

CREATE INDEX idx_post_risk_tag_gen_config_updated_at ON post_risk_tag_gen_config(updated_at);
