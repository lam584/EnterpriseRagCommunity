-- Add locking + claim fields for moderation_queue to support concurrent runners and prevent duplicate processing

ALTER TABLE moderation_queue
  ADD COLUMN locked_by VARCHAR(64) NULL COMMENT 'Auto runner lock owner (instance id)' AFTER assigned_to,
  ADD COLUMN locked_at DATETIME(3) NULL COMMENT 'Auto runner lock timestamp (lease start)' AFTER locked_by,
  ADD COLUMN finished_at DATETIME(3) NULL COMMENT 'When the moderation task reached a terminal state or HUMAN' AFTER locked_at,
  ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version' AFTER finished_at;

CREATE INDEX idx_mq_status_stage ON moderation_queue(status, current_stage, priority, created_at);
CREATE INDEX idx_mq_status_assignee ON moderation_queue(status, assigned_to);
CREATE INDEX idx_mq_locked_at ON moderation_queue(locked_at);
