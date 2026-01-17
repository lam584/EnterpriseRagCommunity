-- Moderation similarity (VEC) config (single-row upsert style)

CREATE TABLE IF NOT EXISTS moderation_similarity_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
);

CREATE INDEX idx_moderation_similarity_config_updated_at ON moderation_similarity_config(updated_at);
