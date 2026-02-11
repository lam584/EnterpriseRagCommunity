/*
  V6：为模型配置增加“启用深度思考”开关（MySQL 8.0）

  说明：
  - enable_thinking 用于控制是否开启上游模型的“思考/推理”输出（如 Qwen 的 /think 或 DashScope enable_thinking）
  - 默认值为 0（关闭），以避免在未明确开启时产生额外耗时/成本
*/

ALTER TABLE ai_gen_task_config
  ADD COLUMN enable_thinking TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用深度思考'
  AFTER temperature;

ALTER TABLE moderation_llm_config
  ADD COLUMN enable_thinking TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用深度思考(文本审核)'
  AFTER max_tokens;

ALTER TABLE moderation_llm_config
  ADD COLUMN vision_enable_thinking TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用深度思考(图片审核)'
  AFTER vision_max_tokens;
