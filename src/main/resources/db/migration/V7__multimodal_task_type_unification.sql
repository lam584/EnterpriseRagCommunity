ALTER TABLE moderation_llm_config
  CHANGE COLUMN vision_prompt_code multimodal_prompt_code VARCHAR(128) NULL COMMENT '多模态审核提示词编码';

ALTER TABLE moderation_llm_config
  DROP COLUMN text_prompt_code;

UPDATE moderation_llm_config
SET multimodal_prompt_code = 'MODERATION_MULTIMODAL'
WHERE multimodal_prompt_code = 'MODERATION_VISION';

UPDATE prompts
SET prompt_code = 'MODERATION_MULTIMODAL',
    name = '多模态审核'
WHERE prompt_code = 'MODERATION_VISION';

DELETE FROM prompts
WHERE prompt_code = 'MODERATION_TEXT';

DELETE FROM llm_routing_policies
WHERE env = 'default'
  AND task_type IN ('MULTIMODAL_CHAT', 'MULTIMODAL_MODERATION');

INSERT INTO llm_routing_policies
  (env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms, label, category, sort_index)
SELECT
  'default',
  'MULTIMODAL_CHAT',
  COALESCE(
    (SELECT strategy FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_CHAT' LIMIT 1),
    (SELECT strategy FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_CHAT' LIMIT 1),
    'WEIGHTED_RR'
  ),
  COALESCE(
    (SELECT max_attempts FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_CHAT' LIMIT 1),
    (SELECT max_attempts FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_CHAT' LIMIT 1),
    2
  ),
  COALESCE(
    (SELECT failure_threshold FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_CHAT' LIMIT 1),
    (SELECT failure_threshold FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_CHAT' LIMIT 1),
    2
  ),
  COALESCE(
    (SELECT cooldown_ms FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_CHAT' LIMIT 1),
    (SELECT cooldown_ms FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_CHAT' LIMIT 1),
    30000
  ),
  '多模态聊天',
  'TEXT_GEN',
  10;

INSERT INTO llm_routing_policies
  (env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms, label, category, sort_index)
SELECT
  'default',
  'MULTIMODAL_MODERATION',
  COALESCE(
    (SELECT strategy FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_MODERATION' LIMIT 1),
    (SELECT strategy FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_MODERATION' LIMIT 1),
    'PRIORITY_FALLBACK'
  ),
  COALESCE(
    (SELECT max_attempts FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_MODERATION' LIMIT 1),
    (SELECT max_attempts FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_MODERATION' LIMIT 1),
    2
  ),
  COALESCE(
    (SELECT failure_threshold FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_MODERATION' LIMIT 1),
    (SELECT failure_threshold FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_MODERATION' LIMIT 1),
    2
  ),
  COALESCE(
    (SELECT cooldown_ms FROM llm_routing_policies WHERE env = 'default' AND task_type = 'IMAGE_MODERATION' LIMIT 1),
    (SELECT cooldown_ms FROM llm_routing_policies WHERE env = 'default' AND task_type = 'TEXT_MODERATION' LIMIT 1),
    30000
  ),
  '多模态审核',
  'TEXT_GEN',
  30;

DELETE FROM llm_routing_policies
WHERE env = 'default'
  AND task_type IN ('TEXT_CHAT', 'IMAGE_CHAT', 'TEXT_MODERATION', 'IMAGE_MODERATION');

DELETE txt
FROM llm_models txt
JOIN llm_models img
  ON img.env = txt.env
 AND img.provider_id = txt.provider_id
 AND img.model_name = txt.model_name
 AND img.purpose IN ('IMAGE_CHAT', 'MULTIMODAL_CHAT')
WHERE txt.purpose = 'TEXT_CHAT';

UPDATE llm_models
SET purpose = 'MULTIMODAL_CHAT'
WHERE purpose IN ('TEXT_CHAT', 'IMAGE_CHAT');

DELETE txt
FROM llm_models txt
JOIN llm_models img
  ON img.env = txt.env
 AND img.provider_id = txt.provider_id
 AND img.model_name = txt.model_name
 AND img.purpose IN ('IMAGE_MODERATION', 'MULTIMODAL_MODERATION')
WHERE txt.purpose = 'TEXT_MODERATION';

UPDATE llm_models
SET purpose = 'MULTIMODAL_MODERATION'
WHERE purpose IN ('TEXT_MODERATION', 'IMAGE_MODERATION');