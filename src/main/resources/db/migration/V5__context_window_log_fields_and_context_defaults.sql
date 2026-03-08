SET @has_budget_tokens := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'context_windows'
      AND COLUMN_NAME = 'budget_tokens'
);
SET @sql_budget_tokens := IF(
        @has_budget_tokens = 0,
        'ALTER TABLE context_windows ADD COLUMN budget_tokens INT NULL COMMENT ''上下文预算Token数'' AFTER total_tokens',
        'SELECT 1'
                         );
PREPARE stmt_budget_tokens FROM @sql_budget_tokens;
EXECUTE stmt_budget_tokens;
DEALLOCATE PREPARE stmt_budget_tokens;

SET @has_selected_items := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'context_windows'
      AND COLUMN_NAME = 'selected_items'
);
SET @sql_selected_items := IF(
        @has_selected_items = 0,
        'ALTER TABLE context_windows ADD COLUMN selected_items INT NULL COMMENT ''入选条数'' AFTER budget_tokens',
        'SELECT 1'
                           );
PREPARE stmt_selected_items FROM @sql_selected_items;
EXECUTE stmt_selected_items;
DEALLOCATE PREPARE stmt_selected_items;

SET @has_dropped_items := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'context_windows'
      AND COLUMN_NAME = 'dropped_items'
);
SET @sql_dropped_items := IF(
        @has_dropped_items = 0,
        'ALTER TABLE context_windows ADD COLUMN dropped_items INT NULL COMMENT ''剔除条数'' AFTER selected_items',
        'SELECT 1'
                          );
PREPARE stmt_dropped_items FROM @sql_dropped_items;
EXECUTE stmt_dropped_items;
DEALLOCATE PREPARE stmt_dropped_items;

UPDATE context_windows
SET
    budget_tokens = COALESCE(
            budget_tokens,
            CAST(JSON_UNQUOTE(JSON_EXTRACT(chunk_ids, '$.stats.budgetTokens')) AS UNSIGNED)
                    ),
    selected_items = COALESCE(
            selected_items,
            JSON_LENGTH(JSON_EXTRACT(chunk_ids, '$.items')),
            JSON_LENGTH(JSON_EXTRACT(chunk_ids, '$.ids')),
            0
                     ),
    dropped_items = COALESCE(dropped_items, 0)
WHERE budget_tokens IS NULL
   OR selected_items IS NULL
   OR dropped_items IS NULL;

INSERT INTO app_settings(k, v, updated_at)
SELECT
    'retrieval.context.config.json',
    '{
  "enabled": true,
  "policy": "TOPK",
  "maxItems": 6,
  "maxContextTokens": 12000,
  "reserveAnswerTokens": 2000,
  "perItemMaxTokens": 2000,
  "maxPromptChars": 200000,
  "contextTokenBudget": 3000,
  "minScore": null,
  "maxSamePostItems": 2,
  "requireTitle": false,
  "alpha": 1.0,
  "beta": 1.0,
  "gamma": 1.0,
  "ablationMode": "REL_IMP_RED",
  "crossSourceDedup": true,
  "dedupByPostId": true,
  "dedupByTitle": false,
  "dedupByContentHash": true,
  "sectionTitle": "以下为从社区帖子检索到的参考资料（仅供参考，回答时请结合用户问题，不要编造不存在的来源）：",
  "itemHeaderTemplate": "[{i}] post_id={postId} chunk={chunkIndex} score={score}\\n标题：{title}\\n",
  "separator": "\\n\\n",
  "showPostId": true,
  "showChunkIndex": true,
  "showScore": true,
  "showTitle": true,
  "extraInstruction": "回答时尽量在相关句末添加 [编号] 引用；如资料不足请明确说明。",
  "logEnabled": true,
  "logSampleRate": 1.0,
  "logMaxDays": 30
}',
    NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM app_settings WHERE k = 'retrieval.context.config.json'
);

UPDATE app_settings
SET
    v = CAST(
            JSON_MERGE_PATCH(
                    CAST('{
  "enabled": true,
  "policy": "TOPK",
  "maxItems": 6,
  "maxContextTokens": 12000,
  "reserveAnswerTokens": 2000,
  "perItemMaxTokens": 2000,
  "maxPromptChars": 200000,
  "contextTokenBudget": 3000,
  "minScore": null,
  "maxSamePostItems": 2,
  "requireTitle": false,
  "alpha": 1.0,
  "beta": 1.0,
  "gamma": 1.0,
  "ablationMode": "REL_IMP_RED",
  "crossSourceDedup": true,
  "dedupByPostId": true,
  "dedupByTitle": false,
  "dedupByContentHash": true,
  "sectionTitle": "以下为从社区帖子检索到的参考资料（仅供参考，回答时请结合用户问题，不要编造不存在的来源）：",
  "itemHeaderTemplate": "[{i}] post_id={postId} chunk={chunkIndex} score={score}\\n标题：{title}\\n",
  "separator": "\\n\\n",
  "showPostId": true,
  "showChunkIndex": true,
  "showScore": true,
  "showTitle": true,
  "extraInstruction": "回答时尽量在相关句末添加 [编号] 引用；如资料不足请明确说明。",
  "logEnabled": true,
  "logSampleRate": 1.0,
  "logMaxDays": 30
}' AS JSON),
                    CASE
                        WHEN JSON_VALID(v) THEN CAST(v AS JSON)
                        ELSE JSON_OBJECT()
                        END
            ) AS CHAR
        ),
    updated_at = NOW(3)
WHERE k = 'retrieval.context.config.json';
