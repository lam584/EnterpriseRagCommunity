-- V5: 热度分重算日志

CREATE TABLE IF NOT EXISTS hot_score_recompute_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    window_type VARCHAR(32) NOT NULL COMMENT '重算窗口类型: H24/D7/D30/M3/M6/Y1/ALL/ALL_WINDOWS',
    started_at DATETIME(3) NOT NULL COMMENT '重算开始时间',
    finished_at DATETIME(3) NOT NULL COMMENT '重算结束时间',
    duration_ms BIGINT NOT NULL COMMENT '计算耗时(毫秒)',
    changed_count INT NOT NULL DEFAULT 0 COMMENT '发生变化的数据条数',
    increased_count INT NOT NULL DEFAULT 0 COMMENT '热度上升条数',
    decreased_count INT NOT NULL DEFAULT 0 COMMENT '热度下降条数',
    unchanged_count INT NOT NULL DEFAULT 0 COMMENT '热度不变条数',
    increased_score_delta DOUBLE NOT NULL DEFAULT 0 COMMENT '热度总上升值',
    decreased_score_delta DOUBLE NOT NULL DEFAULT 0 COMMENT '热度总下降值(正数)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '日志创建时间',
    KEY idx_hsrl_window_type (window_type),
    KEY idx_hsrl_created_at (created_at),
    KEY idx_hsrl_finished_at (finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热度分重算日志';
