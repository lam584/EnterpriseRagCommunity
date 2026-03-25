-- Restore embedding fields on moderation_similarity_config for environments migrated
-- from versions where these columns were temporarily removed.
-- MySQL does not support "ADD COLUMN IF NOT EXISTS" in all deployment combinations,
-- so we guard DDL by checking information_schema first.

SET @db_name = DATABASE();

SET @has_embedding_model = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name
      AND TABLE_NAME = 'moderation_similarity_config'
      AND COLUMN_NAME = 'embedding_model'
);

SET @sql_model = IF(
    @has_embedding_model = 0,
    'ALTER TABLE moderation_similarity_config ADD COLUMN embedding_model VARCHAR(128) NULL COMMENT ''向量模型名称'' AFTER enabled',
    'SELECT 1'
);
PREPARE stmt_model FROM @sql_model;
EXECUTE stmt_model;
DEALLOCATE PREPARE stmt_model;

SET @has_embedding_dims = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name
      AND TABLE_NAME = 'moderation_similarity_config'
      AND COLUMN_NAME = 'embedding_dims'
);

SET @sql_dims = IF(
    @has_embedding_dims = 0,
    'ALTER TABLE moderation_similarity_config ADD COLUMN embedding_dims INT NOT NULL DEFAULT 0 COMMENT ''向量维度'' AFTER embedding_model',
    'SELECT 1'
);
PREPARE stmt_dims FROM @sql_dims;
EXECUTE stmt_dims;
DEALLOCATE PREPARE stmt_dims;

UPDATE moderation_similarity_config
SET embedding_dims = 0
WHERE embedding_dims IS NULL;
