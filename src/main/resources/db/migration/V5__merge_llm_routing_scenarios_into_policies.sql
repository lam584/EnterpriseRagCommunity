-- V5：合并 LLM 路由场景元数据（llm_routing_scenarios）到 llm_routing_policies，并删除 llm_routing_scenarios 表
--
-- 背景：
-- - 之前路由“策略”与路由“场景元数据（label/category/sort）”分表存储，管理端需要同时读取两张表。
-- - 本迁移将元数据收敛到 llm_routing_policies，减少 1 张配置表数量。
--
-- 迁移策略：
-- 1) 为 llm_routing_policies 增加 label/category/sort_index（幂等）。
-- 2) 将 llm_routing_scenarios 的元数据回填到所有 env 的 policies 行（仅在目标列为空时回填）。
-- 3) 若某个 task_type 在 env=default 的 policies 不存在，则创建默认 policy 行并写入元数据。
-- 4) 删除 llm_routing_scenarios（幂等）。
--
-- 注意：本迁移不改变路由决策逻辑字段（strategy/max_attempts/...），仅改变元数据来源。

-- 1) 为 llm_routing_policies 增加元数据列（幂等）
SET @col_label_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'llm_routing_policies'
    AND column_name = 'label'
);
SET @sql = IF(@col_label_exists = 0,
  'ALTER TABLE llm_routing_policies ADD COLUMN label VARCHAR(128) NULL COMMENT ''显示名称''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_category_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'llm_routing_policies'
    AND column_name = 'category'
);
SET @sql = IF(@col_category_exists = 0,
  'ALTER TABLE llm_routing_policies ADD COLUMN category VARCHAR(32) NULL COMMENT ''类别(TEXT_GEN/EMBEDDING/RERANK)''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_sort_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'llm_routing_policies'
    AND column_name = 'sort_index'
);
SET @sql = IF(@col_sort_exists = 0,
  'ALTER TABLE llm_routing_policies ADD COLUMN sort_index INT NOT NULL DEFAULT 0 COMMENT ''排序索引''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 将 llm_routing_scenarios 回填到 llm_routing_policies（如果场景表存在）
SET @table_exists = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'llm_routing_scenarios'
);

SET @sql = IF(@table_exists = 0,
  'SELECT 1',
  '
    UPDATE llm_routing_policies p
    JOIN llm_routing_scenarios s
      ON p.task_type = s.task_type
    SET
      p.label = IF(p.label IS NULL OR TRIM(p.label) = '''', s.label, p.label),
      p.category = IF(p.category IS NULL OR TRIM(p.category) = '''', s.category, p.category),
      p.sort_index = IF(p.sort_index IS NULL OR p.sort_index = 0, s.sort_index, p.sort_index)
    WHERE
      (p.label IS NULL OR TRIM(p.label) = '''')
      OR (p.category IS NULL OR TRIM(p.category) = '''')
      OR (p.sort_index IS NULL OR p.sort_index = 0)
  '
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 为 env=default 补齐缺失的 policy 行（仅当场景表存在）
SET @sql = IF(@table_exists = 0,
  'SELECT 1',
  '
    INSERT INTO llm_routing_policies (
      env, task_type,
      strategy, max_attempts, failure_threshold, cooldown_ms,
      probe_enabled, probe_interval_ms, probe_path,
      version, updated_at, updated_by,
      label, category, sort_index
    )
    SELECT
      ''default'',
      s.task_type,
      ''WEIGHTED_RR'',
      2,
      2,
      30000,
      0,
      NULL,
      NULL,
      0,
      NOW(3),
      NULL,
      s.label,
      s.category,
      s.sort_index
    FROM llm_routing_scenarios s
    LEFT JOIN llm_routing_policies p
      ON p.env = ''default'' AND p.task_type = s.task_type
    WHERE p.task_type IS NULL
  '
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 删除 llm_routing_scenarios（幂等）
SET @sql = IF(@table_exists = 0,
  'SELECT 1',
  'DROP TABLE llm_routing_scenarios'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

