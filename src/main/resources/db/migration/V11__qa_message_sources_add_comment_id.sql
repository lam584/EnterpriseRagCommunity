ALTER TABLE qa_message_sources
    ADD COLUMN comment_id BIGINT UNSIGNED NULL COMMENT '评论ID（评论区来源）' AFTER post_id;

CREATE INDEX idx_qms_comment ON qa_message_sources (comment_id);

ALTER TABLE qa_message_sources
    ADD CONSTRAINT fk_qms_comment FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE SET NULL;
