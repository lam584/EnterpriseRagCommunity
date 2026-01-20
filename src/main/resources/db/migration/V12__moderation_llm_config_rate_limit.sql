-- Extend moderation_llm_config with rate limit / concurrency options

ALTER TABLE moderation_llm_config
  ADD COLUMN max_concurrent INT NULL COMMENT 'Max concurrent LLM requests for auto runner' AFTER auto_run,
  ADD COLUMN min_delay_ms INT NULL COMMENT 'Minimum delay between LLM requests submission (ms)' AFTER max_concurrent,
  ADD COLUMN qps DOUBLE NULL COMMENT 'Approx global QPS limit (0/NULL means unlimited)' AFTER min_delay_ms;
