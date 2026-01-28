-- Add case_type to moderation_queue so REPORT tasks can coexist with CONTENT tasks
ALTER TABLE moderation_queue
  ADD COLUMN case_type ENUM('CONTENT','REPORT') NOT NULL DEFAULT 'CONTENT' AFTER id;

ALTER TABLE moderation_queue
  DROP INDEX uk_mq_target,
  ADD UNIQUE KEY uk_mq_case_target (case_type, content_type, content_id);

-- Add configurable threshold: when report count reaches N, route to HUMAN directly
ALTER TABLE moderation_confidence_fallback_config
  ADD COLUMN report_human_threshold INT NOT NULL DEFAULT 5;

