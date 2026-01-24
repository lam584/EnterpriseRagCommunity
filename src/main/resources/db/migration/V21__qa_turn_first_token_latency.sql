SET NAMES utf8mb4;

ALTER TABLE qa_turns
    ADD COLUMN first_token_latency_ms INT NULL COMMENT '首字延迟（毫秒）' AFTER latency_ms;

