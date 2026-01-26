-- Add allowed target languages list for translate preferences UI

ALTER TABLE semantic_translate_config
  ADD COLUMN allowed_target_langs LONGTEXT NULL
  COMMENT '前台可选的目标语言列表（JSON 数组）';

