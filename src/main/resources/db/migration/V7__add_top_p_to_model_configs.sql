/*
  V7：为模型配置增加 TOP-P（MySQL 8.0）

  目标：
  - 为各类“模型配置表单”增加可配置的 TOP-P 参数（top_p）
  - 为旧数据补齐合理的默认值（仅当字段为空时写入，不覆盖用户配置）

  说明：
  - 上游 OpenAI 兼容接口参数名为 top_p
  - 本项目中多数配置表单都存于数据库（ai_gen_task_config / moderation_llm_config / ...）
*/

-- 1) ai_gen_task_config：生成/摘要/翻译/标签等通用配置
ALTER TABLE ai_gen_task_config
  ADD COLUMN top_p DECIMAL(4,3) NULL COMMENT 'TOP-P（0~1）' AFTER temperature;

-- 2) moderation_llm_config：内容审核配置（文本/视觉）
ALTER TABLE moderation_llm_config
  ADD COLUMN top_p DECIMAL(4,3) NULL COMMENT '文本审核 TOP-P（0~1）' AFTER temperature,
  ADD COLUMN vision_top_p DECIMAL(4,3) NULL COMMENT '视觉审核 TOP-P（0~1）' AFTER vision_temperature;

-- 3) post_suggestion_gen_history：历史记录补充 top_p（便于排查输出波动）
ALTER TABLE post_suggestion_gen_history
  ADD COLUMN top_p DECIMAL(4,3) NULL COMMENT 'TOP-P（0~1）' AFTER temperature;

-- 4) 补齐默认值：ai_gen_task_config（仅当 top_p 为空时写入）
UPDATE ai_gen_task_config
SET top_p = CASE
  WHEN group_code = 'POST_SUGGESTION' AND sub_type = 'TITLE' THEN 0.900
  WHEN group_code = 'POST_SUGGESTION' AND sub_type = 'TOPIC_TAG' THEN 0.800
  WHEN group_code = 'POST_SUMMARY' AND sub_type = 'DEFAULT' THEN 0.700
  WHEN group_code = 'SEMANTIC_TRANSLATE' AND sub_type = 'DEFAULT' THEN 0.400
  WHEN group_code = 'POST_LANG_LABEL' AND sub_type = 'DEFAULT' THEN 0.200
  WHEN group_code = 'POST_RISK_TAG' AND sub_type = 'DEFAULT' THEN 0.600
  ELSE top_p
END
WHERE top_p IS NULL
  AND (
    (group_code = 'POST_SUGGESTION' AND sub_type IN ('TITLE','TOPIC_TAG'))
    OR (group_code = 'POST_SUMMARY' AND sub_type = 'DEFAULT')
    OR (group_code = 'SEMANTIC_TRANSLATE' AND sub_type = 'DEFAULT')
    OR (group_code = 'POST_LANG_LABEL' AND sub_type = 'DEFAULT')
    OR (group_code = 'POST_RISK_TAG' AND sub_type = 'DEFAULT')
  );

-- 5) 补齐默认值：moderation_llm_config（仅当 top_p 为空时写入）
UPDATE moderation_llm_config
SET
  top_p = COALESCE(top_p, 0.200),
  vision_top_p = COALESCE(vision_top_p, top_p, 0.200)
WHERE id = 1;
