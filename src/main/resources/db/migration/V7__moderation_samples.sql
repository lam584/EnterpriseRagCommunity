-- Moderation similar detection samples (authority in MySQL, indexed in Elasticsearch)
-- MySQL 8.0, InnoDB, utf8mb4

CREATE TABLE IF NOT EXISTS moderation_samples (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    category ENUM('AD_SAMPLE','HISTORY_VIOLATION') NOT NULL COMMENT '样本类别',

    -- Optional back reference to existing content
    ref_content_type ENUM('POST','COMMENT') NULL COMMENT '关联内容类型(可选)',
    ref_content_id BIGINT UNSIGNED NULL COMMENT '关联内容ID(可选)',

    -- Raw and normalized text
    raw_text LONGTEXT NOT NULL COMMENT '原始文本(回显/溯源)',
    normalized_text LONGTEXT NOT NULL COMMENT '规范化文本(去噪/截断后,用于embedding与检索)',

    -- Hash for dedup / caching (SHA-256 hex recommended)
    text_hash VARCHAR(64) NOT NULL COMMENT '规范化文本hash(建议SHA-256 hex,用于去重/缓存)',

    -- Risk/metadata
    risk_level INT NOT NULL DEFAULT 0 COMMENT '风险/违规则等级(0表示未知)',
    labels JSON NULL COMMENT '风险标签(JSON数组)',
    source ENUM('HUMAN','RULE','LLM','IMPORT') NOT NULL DEFAULT 'HUMAN' COMMENT '样本来源',

    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    UNIQUE KEY uk_ms_text_hash (text_hash),
    KEY idx_ms_enabled (enabled, id),
    KEY idx_ms_category (category, enabled, id),
    KEY idx_ms_ref (ref_content_type, ref_content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似检测样本库(权威源:MySQL; 检索:Elasticsearch)';

-- Optional: improve audit querying by time / candidate
ALTER TABLE moderation_similar_hits
    ADD KEY idx_msh_matched_at (matched_at),
    ADD KEY idx_msh_candidate (candidate_id, matched_at);
