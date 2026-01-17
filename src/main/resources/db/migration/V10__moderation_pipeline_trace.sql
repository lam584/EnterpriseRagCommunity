-- Moderation pipeline trace tables: one run + step details for RULE -> VEC -> LLM

CREATE TABLE IF NOT EXISTS moderation_pipeline_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  queue_id BIGINT NOT NULL,
  content_type VARCHAR(16) NOT NULL,
  content_id BIGINT NOT NULL,

  status VARCHAR(16) NOT NULL, -- RUNNING/SUCCESS/FAIL
  final_decision VARCHAR(16) NULL, -- APPROVE/REJECT/HUMAN

  trace_id VARCHAR(64) NOT NULL,

  started_at DATETIME(3) NOT NULL,
  ended_at DATETIME(3) NULL,
  total_ms BIGINT NULL,

  error_code VARCHAR(64) NULL,
  error_message VARCHAR(512) NULL,

  llm_model VARCHAR(128) NULL,
  llm_threshold DECIMAL(6,4) NULL,

  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  UNIQUE KEY uk_moderation_pipeline_run_trace_id (trace_id),
  KEY idx_moderation_pipeline_run_queue_id (queue_id),
  KEY idx_moderation_pipeline_run_content (content_type, content_id),
  KEY idx_moderation_pipeline_run_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS moderation_pipeline_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL,

  stage VARCHAR(16) NOT NULL, -- RULE/VEC/LLM
  step_order INT NOT NULL,

  decision VARCHAR(32) NULL, -- PASS/HIT/MISS/APPROVE/REJECT/HUMAN/SKIP/ERROR
  score DECIMAL(10,6) NULL,
  threshold DECIMAL(10,6) NULL,

  details_json JSON NULL,

  started_at DATETIME(3) NOT NULL,
  ended_at DATETIME(3) NULL,
  cost_ms BIGINT NULL,

  error_code VARCHAR(64) NULL,
  error_message VARCHAR(512) NULL,

  UNIQUE KEY uk_moderation_pipeline_step_run_stage (run_id, stage),
  KEY idx_moderation_pipeline_step_run_id (run_id),
  CONSTRAINT fk_moderation_pipeline_step_run_id FOREIGN KEY (run_id) REFERENCES moderation_pipeline_run(id) ON DELETE CASCADE
);
