CREATE TABLE qa_message_sources (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id BIGINT UNSIGNED NOT NULL COMMENT '答案消息ID',
    source_index INT NOT NULL COMMENT '来源序号（从 1 开始）',
    post_id BIGINT UNSIGNED NULL COMMENT '帖子ID（RAG 命中来源）',
    chunk_index INT NULL COMMENT '分片序号（RAG 命中来源）',
    score DOUBLE NULL COMMENT '相关性得分',
    title VARCHAR(512) NULL COMMENT '来源标题（可选）',
    url VARCHAR(512) NULL COMMENT '来源URL（可选）',
    UNIQUE KEY uk_qms_msg_idx (message_id, source_index),
    KEY idx_qms_msg (message_id),
    CONSTRAINT fk_qms_msg FOREIGN KEY (message_id) REFERENCES qa_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答答案来源（RAG 溯源）表';
