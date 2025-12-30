-- LLM moderation config (single-row upsert style)

CREATE TABLE IF NOT EXISTS moderation_llm_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_template LONGTEXT NOT NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(4,3) NULL,
  max_tokens INT NULL,
  threshold DECIMAL(6,4) NULL,
  auto_run TINYINT(1) NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
);

CREATE INDEX idx_moderation_llm_config_updated_at ON moderation_llm_config(updated_at);

