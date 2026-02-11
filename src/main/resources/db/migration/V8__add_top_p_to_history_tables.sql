/*
  V8：为历史记录表补充 TOP-P（MySQL 8.0）

  目标：
  - semantic_translate_history / post_summary_gen_history / post_ai_summary 增加 top_p 字段
  - 用于排查输出波动、回放当次采样参数
*/

ALTER TABLE semantic_translate_history
  ADD COLUMN top_p DECIMAL(4,3) NULL COMMENT 'TOP-P（0~1）' AFTER temperature;

ALTER TABLE post_summary_gen_history
  ADD COLUMN top_p DECIMAL(4,3) NULL COMMENT 'TOP-P（0~1）' AFTER temperature;

ALTER TABLE post_ai_summary
  ADD COLUMN top_p DECIMAL(4,3) NULL COMMENT 'TOP-P（0~1）' AFTER temperature;

