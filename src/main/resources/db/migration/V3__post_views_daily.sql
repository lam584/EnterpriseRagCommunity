-- 新增：帖子按自然日聚合的浏览量表（用于热榜与数据展示）
-- 口径：Asia/Shanghai 自然日；每次浏览 +1（不去重）

CREATE TABLE post_views_daily (
    post_id BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
    day DATE NOT NULL COMMENT '自然日(YYYY-MM-DD)',
    view_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当天浏览次数',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (post_id, day),
    KEY idx_pvd_day (day),
    CONSTRAINT fk_pvd_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子浏览量-按天聚合表';

