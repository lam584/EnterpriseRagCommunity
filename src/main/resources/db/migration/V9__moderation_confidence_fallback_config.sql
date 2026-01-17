-- Moderation confidence fallback config (single-row upsert style)

CREATE TABLE IF NOT EXISTS moderation_confidence_fallback_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,

  -- Rule layer
  rule_enabled TINYINT(1) NOT NULL DEFAULT 1,
  -- action when rule layer is triggered with at least this severity
  rule_high_action ENUM('REJECT','LLM','HUMAN') NOT NULL DEFAULT 'HUMAN',
  rule_medium_action ENUM('REJECT','LLM','HUMAN') NOT NULL DEFAULT 'LLM',
  rule_low_action ENUM('REJECT','LLM','HUMAN') NOT NULL DEFAULT 'LLM',

  -- Embedding similarity layer
  vec_enabled TINYINT(1) NOT NULL DEFAULT 1,
  -- distance <= threshold => hit
  vec_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.2000,
  vec_hit_action ENUM('REJECT','LLM','HUMAN') NOT NULL DEFAULT 'HUMAN',
  vec_miss_action ENUM('REJECT','LLM','HUMAN') NOT NULL DEFAULT 'LLM',

  -- LLM layer
  llm_enabled TINYINT(1) NOT NULL DEFAULT 1,
  -- LLM returns risk score [0..1]. If score >= reject_threshold -> REJECT.
  llm_reject_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.7500,
  -- If score between [human_threshold, reject_threshold) -> HUMAN.
  llm_human_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.5000,
  -- If score < human_threshold -> APPROVE.

  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL
);

CREATE INDEX idx_moderation_confidence_fallback_updated_at ON moderation_confidence_fallback_config(updated_at);
