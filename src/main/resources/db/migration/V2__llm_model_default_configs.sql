/*
  V2：LLM 相关默认配置与数据迁移（MySQL 8.0）

  目标：
  - 初始化（seed）系统运行所需的默认配置数据（审核、路由、模型池、语义增强提示词等）
  - 将旧任务类型拆分为更细粒度的 taskType/purpose（如：CHAT -> TEXT_CHAT/IMAGE_CHAT；MODERATION -> TEXT_MODERATION/IMAGE_MODERATION）

  幂等性说明：
  - 大部分写入使用 INSERT IGNORE / ON DUPLICATE KEY UPDATE / WHERE NOT EXISTS，重复执行不会产生重复数据
  - 数据迁移步骤包含 “复制 -> 删除旧 key” 的顺序；重复执行仍保持目标状态一致

  重要约定（与运行时逻辑一致）：
  - 多数“默认生成/翻译”等配置会将 model/provider_id 置为 NULL，用于表示“自动路由/负载均衡选择模型”
  - 审核（moderation_llm_config）默认使用“自动（均衡负载）”：model/provider_id 置为 NULL，走 TEXT_MODERATION 路由策略选择模型
*/

-- 1) 初始化：默认 LLM 审核提示词（表为空/指定主键不存在时才写入）
-- 表：moderation_llm_config（单行配置表）
-- 字段说明：
-- - prompt_template：文本审核提示词模板
-- - model/provider_id：文本审核默认模型与提供方
-- - temperature/max_tokens：文本审核采样参数
-- - threshold：风险阈值
-- - auto_run：是否启用自动审核
-- - vision_model/vision_provider_id：视觉审核默认模型与提供方

INSERT INTO moderation_llm_config (
  id,
  prompt_template,
  model,
  provider_id,
  temperature,
  max_tokens,
  threshold,
  auto_run,
  version,
  updated_by,
  vision_model,
  vision_provider_id
)
SELECT
  1,
  '你是一个内容审核助手。请审核以下内容是否包含违规信息（如色情、暴力、政治敏感、广告垃圾等）。\n\n待审核文本：\n{{text}}\n\n请输出 JSON 格式，包含字段：\n- safe: boolean (是否安全)\n- reason: string (如果不安全，请说明原因；如果安全，请留空)\n- labels: string[] (违规标签，如 porn, violence, ad 等)',
  NULL,
  NULL,
  0.2,
  NULL,
  0.75,
  0,
  0,
  NULL,
  NULL,
  NULL
WHERE NOT EXISTS (SELECT 1 FROM moderation_llm_config WHERE id = 1);

-- 2) LLM 路由策略（按 task_type/场景控制：策略、重试、熔断冷却等）
-- 表：llm_routing_policies
-- 字段说明：
-- - env：环境标识（默认 default）
-- - task_type：任务类型/场景（如 CHAT/MODERATION 等；后续会拆分为 TEXT_* / IMAGE_*）
-- - strategy：路由策略（如 WEIGHTED_RR 权重轮询、PRIORITY_FALLBACK 优先级回退）
-- - max_attempts：最大尝试次数
-- - failure_threshold：失败阈值（达到则进入冷却）
-- - cooldown_ms：冷却时间（毫秒）

INSERT IGNORE INTO llm_routing_policies(env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms)
VALUES
  ('default', 'CHAT', 'WEIGHTED_RR', 2, 2, 30000),
  ('default', 'MODERATION', 'PRIORITY_FALLBACK', 2, 2, 30000);

-- 3) LLM 路由场景元数据（用于管理端展示/排序/分组）
-- 表：llm_routing_scenarios
-- 字段说明：
-- - task_type：任务类型/场景（与路由策略、模型 purpose 对应）
-- - label：中文显示名称
-- - category：类别（TEXT_GEN/EMBEDDING/RERANK）
-- - sort_index：排序索引（越小越靠前）

INSERT INTO llm_routing_scenarios (task_type, label, category, sort_index) VALUES
('CHAT', '聊天', 'TEXT_GEN', 10),
('LANGUAGE_TAG_GEN', '语言标签', 'TEXT_GEN', 20),
('MODERATION', '内容审核', 'TEXT_GEN', 30),
('RISK_TAG_GEN', '风险标签', 'TEXT_GEN', 40),
('SUMMARY_GEN', '摘要生成', 'TEXT_GEN', 50),
('TITLE_GEN', '标题生成', 'TEXT_GEN', 60),
('TOPIC_TAG_GEN', '主题标签', 'TEXT_GEN', 70),
('TRANSLATION', '翻译', 'TEXT_GEN', 80),
('POST_EMBEDDING', '帖子向量化', 'EMBEDDING', 110),
('SIMILARITY_EMBEDDING', '相似检测向量化', 'EMBEDDING', 120),
('RERANK', '重排序', 'RERANK', 210);

-- 4) 迁移：将旧 MODERATION 场景拆分为 TEXT_MODERATION / IMAGE_MODERATION
-- 迁移思路：
-- - 新增两个 task_type（幂等 upsert）
-- - 将旧 MODERATION 的路由策略与模型池复制到两个新 task_type
-- - 删除旧 MODERATION 的路由策略与模型池，避免运行时歧义

INSERT INTO llm_routing_scenarios (task_type, label, category, sort_index) VALUES
('TEXT_MODERATION', '文本审核', 'TEXT_GEN', 30),
('IMAGE_MODERATION', '图片审核', 'TEXT_GEN', 31)
ON DUPLICATE KEY UPDATE
  label = VALUES(label),
  category = VALUES(category),
  sort_index = VALUES(sort_index);

DELETE FROM llm_routing_scenarios WHERE task_type = 'MODERATION';

INSERT IGNORE INTO llm_routing_policies (
  env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
)
SELECT
  env, 'TEXT_MODERATION', strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
FROM llm_routing_policies
WHERE task_type = 'MODERATION';

INSERT IGNORE INTO llm_routing_policies (
  env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
)
SELECT
  env, 'IMAGE_MODERATION', strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
FROM llm_routing_policies
WHERE task_type = 'MODERATION';

INSERT IGNORE INTO llm_models (
  env, provider_id, purpose, model_name,
  enabled, is_default, weight, priority, sort_index,
  max_concurrent, min_delay_ms, qps, price_config_id, metadata
)
SELECT
  env, provider_id, 'TEXT_MODERATION', model_name,
  enabled, is_default, weight, priority, sort_index,
  max_concurrent, min_delay_ms, qps, price_config_id, metadata
FROM llm_models
WHERE purpose = 'MODERATION';

INSERT IGNORE INTO llm_models (
  env, provider_id, purpose, model_name,
  enabled, is_default, weight, priority, sort_index,
  max_concurrent, min_delay_ms, qps, price_config_id, metadata
)
SELECT
  env, provider_id, 'IMAGE_MODERATION', model_name,
  enabled, is_default, weight, priority, sort_index,
  max_concurrent, min_delay_ms, qps, price_config_id, metadata
FROM llm_models
WHERE purpose = 'MODERATION';

DELETE FROM llm_routing_policies WHERE task_type = 'MODERATION';
DELETE FROM llm_models WHERE purpose = 'MODERATION';

-- 5) 迁移：将旧 CHAT 场景拆分为 TEXT_CHAT / IMAGE_CHAT
-- 迁移思路同上：复制策略与模型 -> 删除旧 key

INSERT INTO llm_routing_scenarios (task_type, label, category, sort_index) VALUES
('TEXT_CHAT', '文本聊天', 'TEXT_GEN', 10),
('IMAGE_CHAT', '图片聊天', 'TEXT_GEN', 11)
ON DUPLICATE KEY UPDATE
  label = VALUES(label),
  category = VALUES(category),
  sort_index = VALUES(sort_index);

DELETE FROM llm_routing_scenarios WHERE task_type = 'CHAT';

INSERT IGNORE INTO llm_routing_policies (
  env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
)
SELECT
  env, 'TEXT_CHAT', strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
FROM llm_routing_policies
WHERE task_type = 'CHAT';

INSERT IGNORE INTO llm_routing_policies (
  env, task_type, strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
)
SELECT
  env, 'IMAGE_CHAT', strategy, max_attempts, failure_threshold, cooldown_ms,
  probe_enabled, probe_interval_ms, probe_path, version, updated_at, updated_by
FROM llm_routing_policies
WHERE task_type = 'CHAT';

INSERT IGNORE INTO llm_models (
  env, provider_id, purpose, model_name,
  enabled, is_default, weight, priority, sort_index,
  max_concurrent, min_delay_ms, qps, price_config_id, metadata
)
SELECT
  env, provider_id, 'TEXT_CHAT', model_name,
  enabled, is_default, weight, priority, sort_index,
  max_concurrent, min_delay_ms, qps, price_config_id, metadata
FROM llm_models
WHERE purpose = 'CHAT';

DELETE FROM llm_routing_policies WHERE task_type = 'CHAT';
DELETE FROM llm_models WHERE purpose = 'CHAT';

UPDATE moderation_llm_config
SET
  vision_temperature = COALESCE(vision_temperature, temperature),
  vision_max_tokens = COALESCE(vision_max_tokens, max_tokens),
  vision_prompt_template = CASE
    WHEN vision_prompt_template IS NULL OR TRIM(vision_prompt_template) = '' THEN
      '你是一个严格的图片内容安全审核助手。请你同时完成“描述图片内容”与“判断是否违规”两项任务，并且必须只输出严格 JSON。

你会收到若干张图片（最多 10 张）。请综合所有图片，输出：
{
  "decision": "APPROVE|REJECT|HUMAN",
  "score": 0.0-1.0,
  "reasons": ["..."],
  "riskTags": ["..."],
  "description": "请用中文描述图片里有什么：人物、文字(尽量OCR)、场景、动作、关系、可能的广告/引流信息等"
}

注意：
- score 表示“图片整体违规风险概率(0~1)”，越大越可能违规；
- decision 仅能取 APPROVE/REJECT/HUMAN；
- 若图片含文字，请尽量转写关键文字；
- 如果你无法确定或图片不可读，decision=HUMAN。

关联的原文（供你理解上下文，不代表一定要参考）：
{{text}}'
    ELSE vision_prompt_template
  END;

-- 6) 补齐：视觉审核默认提示词（vision_prompt_template 为空时写入）
-- 说明：
-- - 视觉审核需要“描述图片内容 + 违规判断”，并要求严格 JSON 输出，便于后端解析与追溯

INSERT INTO llm_providers (
  env, provider_id, name, type, base_url,
  enabled, priority,
  default_chat_model, default_embedding_model,
  created_at, updated_at
)
VALUES (
  'default', 'aliyun', '阿里云 (DashScope)', 'OPENAI_COMPAT', 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  1, 10,
  'qwen3-235b-a22b', 'text-embedding-v4',
  NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  type = VALUES(type),
  base_url = VALUES(base_url),
  enabled = VALUES(enabled),
  priority = VALUES(priority),
  default_chat_model = CASE
    WHEN default_chat_model IS NULL OR default_chat_model = '' THEN VALUES(default_chat_model)
    ELSE default_chat_model
  END,
  default_embedding_model = CASE
    WHEN default_embedding_model IS NULL OR default_embedding_model = '' THEN VALUES(default_embedding_model)
    ELSE default_embedding_model
  END,
  updated_at = VALUES(updated_at);

-- 7) 初始化：默认 Provider（阿里云 DashScope OpenAI 兼容模式）
-- 表：llm_providers
-- 字段说明：
-- - provider_id：提供方唯一标识（如 aliyun）
-- - type/base_url：协议类型与网关地址（OPENAI_COMPAT）
-- - default_chat_model/default_embedding_model：该 provider 的默认模型（可为空；为空时会保留已有值）

INSERT INTO llm_models (env, provider_id, purpose, model_name, enabled, is_default, weight, created_at, updated_at)
VALUES
('default', 'aliyun', 'TEXT_CHAT', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'IMAGE_CHAT', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'RERANK', 'qwen3-rerank', 1, 1, 10, NOW(), NOW()),
('default', 'aliyun', 'POST_EMBEDDING', 'text-embedding-v4', 1, 1, 10, NOW(), NOW()),
('default', 'aliyun', 'SIMILARITY_EMBEDDING', 'text-embedding-v4', 1, 1, 10, NOW(), NOW()),
('default', 'aliyun', 'LANGUAGE_TAG_GEN', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'RISK_TAG_GEN', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'SUMMARY_GEN', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TITLE_GEN', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TOPIC_TAG_GEN', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TRANSLATION', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'LANGUAGE_TAG_GEN', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'RISK_TAG_GEN', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'SUMMARY_GEN', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TITLE_GEN', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TOPIC_TAG_GEN', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TRANSLATION', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'TEXT_MODERATION', 'qwen3-235b-a22b', 1, 0, 10, NOW(), NOW()),
('default', 'aliyun', 'IMAGE_MODERATION', 'qwen3-vl-235b-a22b-instruct', 1, 0, 10, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  enabled = VALUES(enabled),
  is_default = VALUES(is_default),
  weight = VALUES(weight),
  updated_at = VALUES(updated_at);

-- 8) 初始化：默认模型池（按 purpose/task_type 维度）
-- 表：llm_models
-- 字段说明：
-- - purpose：任务类型/场景（需与 llm_routing_scenarios.task_type 对齐）
-- - enabled/is_default/weight：是否启用、是否默认、权重（用于负载均衡/兜底）

INSERT INTO moderation_similarity_config (id, enabled, embedding_model, embedding_dims, max_input_chars, default_top_k, default_threshold, default_num_candidates)
VALUES (1, 1, NULL, 0, 0, 5, 0.15, 0)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
embedding_model = CASE
  WHEN embedding_model IS NULL OR TRIM(embedding_model) = '' THEN VALUES(embedding_model)
  ELSE embedding_model
END,
embedding_dims = VALUES(embedding_dims),
max_input_chars = VALUES(max_input_chars),
default_top_k = VALUES(default_top_k),
default_threshold = VALUES(default_threshold),
default_num_candidates = VALUES(default_num_candidates);

-- 9) 初始化：相似检测运行时配置（VEC 层）
-- 表：moderation_similarity_config（单行配置）
-- 字段说明：
-- - embedding_model：向量模型名称
-- - default_top_k/default_threshold/default_num_candidates：检索与阈值参数

INSERT INTO moderation_samples_index_config (
  id,
  index_name,
  ik_enabled,
  embedding_model,
  embedding_dims,
  default_top_k,
  default_threshold,
  version,
  updated_at,
  updated_by
)
SELECT
  1,
  'ad_violation_samples_v1',
  1,
  NULL,
  0,
  5,
  0.15,
  0,
  NOW(3),
  NULL
WHERE NOT EXISTS (SELECT 1 FROM moderation_samples_index_config);

-- 10) 初始化：相似检测样本库 ES 索引默认配置
-- 表：moderation_samples_index_config（单行配置）
-- 字段说明：
-- - index_name：ES 索引名称
-- - ik_enabled：是否启用 IK 分词（中文检索）
-- - embedding_model/default_top_k/default_threshold：召回与阈值参数

INSERT INTO ai_gen_task_config (
  group_code, sub_type,
  enabled, system_prompt, prompt_template, model, provider_id, temperature,
  default_count, max_count, max_content_chars,
  history_enabled, history_keep_days, history_keep_rows
)
VALUES ('POST_SUGGESTION', 'TITLE',
1,
'你是专业的中文社区运营编辑，擅长给帖子拟标题。',
'请为下面这段社区帖子内容生成 {{count}} 个中文标题候选。\n要求：\n- 每个标题不超过 30 个汉字\n- 风格适度多样（提问式/总结式/爆点式），但不要低俗\n- 标题之间不要重复\n- 只输出严格 JSON，不要输出任何解释文字，不要包裹 ```\n- JSON 格式：{"titles":["...","..."]}\n\n{{boardLine}}{{tagsLine}}帖子内容：\n{{content}}',
NULL, NULL, 0.7, 3, 5, 4000, 1, 30, 1000)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
system_prompt = VALUES(system_prompt),
prompt_template = VALUES(prompt_template),
model = VALUES(model),
provider_id = VALUES(provider_id),
temperature = VALUES(temperature),
default_count = VALUES(default_count),
max_count = VALUES(max_count),
max_content_chars = VALUES(max_content_chars),
history_enabled = VALUES(history_enabled),
history_keep_days = VALUES(history_keep_days),
history_keep_rows = VALUES(history_keep_rows);

-- 11) 初始化：帖子标题生成配置（LLM）
-- 表：post_suggestion_gen_config(kind='TITLE')
-- 说明：
-- - model/provider_id 默认 NULL：表示由路由策略自动选择模型（便于后续扩展与负载均衡）

INSERT INTO ai_gen_task_config (
  group_code, sub_type,
  enabled, system_prompt, prompt_template, model, provider_id, temperature,
  default_count, max_count, max_content_chars,
  history_enabled, history_keep_days, history_keep_rows
)
VALUES ('POST_SUGGESTION', 'TOPIC_TAG',
1,
'你是专业的中文社区运营编辑，擅长为帖子提炼主题标签。',
'请根据下面这段帖子内容生成 {{count}} 个中文主题标签。\n要求：\n- 标签应概括内容主题，优先使用常见领域词汇\n- 每个标签不超过 8 个汉字\n- 标签之间不要重复\n- 不要输出编号、不要输出解释文字\n- 只输出严格 JSON，不要包裹 ```\n- JSON 格式：{"tags":["...","..."]}\n\n{{boardLine}}{{titleLine}}{{tagsLine}}帖子内容：\n{{content}}',
NULL, NULL, 0.3, 5, 10, 4000, 1, 30, 1000)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
system_prompt = VALUES(system_prompt),
prompt_template = VALUES(prompt_template),
model = VALUES(model),
provider_id = VALUES(provider_id),
temperature = VALUES(temperature),
default_count = VALUES(default_count),
max_count = VALUES(max_count),
max_content_chars = VALUES(max_content_chars),
history_enabled = VALUES(history_enabled),
history_keep_days = VALUES(history_keep_days),
history_keep_rows = VALUES(history_keep_rows);

-- 12) 初始化：帖子主题标签生成配置（LLM）
-- 表：post_suggestion_gen_config(kind='TOPIC_TAG')
-- 说明：model/provider_id 默认 NULL -> 自动路由

INSERT INTO ai_gen_task_config (
  group_code, sub_type,
  enabled, system_prompt, prompt_template, model, provider_id, temperature, max_content_chars
)
VALUES ('POST_SUMMARY', 'DEFAULT',
1, '',
'请为以下社区帖子生成“帖子摘要”。\n\n要求：\n- 只输出严格 JSON，不要输出任何解释文字，不要包裹 ```；\n- JSON 字段：{"title":"...","summary":"..."}；\n- title：可选，若原文标题已足够清晰可直接复用或略微改写；\n- summary：中文摘要，建议 80~200 字，尽量覆盖关键信息、结论与可执行要点；\n\n帖子标题：\n{{title}}\n\n{{tagsLine}}\n\n帖子正文：\n{{content}}',
NULL, NULL, 0.3, 8000)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
system_prompt = VALUES(system_prompt),
prompt_template = VALUES(prompt_template),
model = VALUES(model),
provider_id = VALUES(provider_id),
temperature = VALUES(temperature),
max_content_chars = VALUES(max_content_chars);

-- 13) 初始化：帖子摘要生成配置（LLM）
-- 表：post_summary_gen_config
-- 说明：model/provider_id 默认 NULL -> 自动路由

INSERT INTO ai_gen_task_config (
  group_code, sub_type,
  enabled, system_prompt, prompt_template, model, provider_id, temperature, max_content_chars,
  history_enabled, history_keep_days, history_keep_rows
)
VALUES ('SEMANTIC_TRANSLATE', 'DEFAULT',
1,
'你是一个专业的翻译助手。\n要求：\n1. 把用户提供的标题与正文翻译成目标语言；\n2. 正文输出必须为 Markdown，尽量保留原文的结构（标题层级/列表/引用/代码块/表格等）；\n3. 不要添加与原文无关的内容，不要进行总结，不要输出额外解释；\n4. 输出严格为 JSON（不要包裹 ```），字段如下：\n   - title: 翻译后的标题（纯文本）\n   - markdown: 翻译后的正文 Markdown\n',
'目标语言：{{targetLang}}\n\n标题：\n{{title}}\n\n正文（Markdown）：\n{{content}}\n',
NULL, NULL, 0.2, 8000, 1, 30, 5000)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
system_prompt = VALUES(system_prompt),
prompt_template = VALUES(prompt_template),
model = VALUES(model),
provider_id = VALUES(provider_id),
temperature = VALUES(temperature),
max_content_chars = VALUES(max_content_chars),
history_enabled = VALUES(history_enabled),
history_keep_days = VALUES(history_keep_days),
history_keep_rows = VALUES(history_keep_rows);

-- 14) 初始化：语义翻译配置（LLM）
-- 表：semantic_translate_config
-- 说明：model/provider_id 默认 NULL -> 自动路由

INSERT INTO ai_gen_task_config (
  group_code, sub_type,
  enabled, system_prompt, prompt_template, model, provider_id, temperature, max_content_chars
)
VALUES ('POST_LANG_LABEL', 'DEFAULT',
1,
'你是一个语言识别助手。\n任务：根据输入的标题与正文，判断文本包含的自然语言。\n输出要求：\n1. 只输出 JSON（不要包裹 ```），格式：{"languages":["zh","en"]}\n2. languages 使用简短语言代码（优先 ISO 639-1：zh/en/ja/ko/fr/de/es/ru/it/pt/...）。中文统一用 zh。\n3. 如果文本明显由多种语言混合组成，请输出多个语言代码（最多 3 个）。\n4. 不要输出解释、不要输出多余字段。',
'标题：\n{{title}}\n\n正文：\n{{content}}',
NULL, NULL, 0.0, 8000)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
system_prompt = VALUES(system_prompt),
prompt_template = VALUES(prompt_template),
model = VALUES(model),
provider_id = VALUES(provider_id),
temperature = VALUES(temperature),
max_content_chars = VALUES(max_content_chars);

-- 15) 初始化：帖子语言识别配置（LLM）
-- 表：post_lang_label_gen_config
-- 说明：model/provider_id 默认 NULL -> 自动路由

INSERT INTO ai_gen_task_config (
  group_code, sub_type,
  enabled, system_prompt, prompt_template, model, provider_id, temperature, max_count, max_content_chars
)
VALUES ('POST_RISK_TAG', 'DEFAULT',
1,
'你是一个社区内容风险识别助手。\n任务：根据输入的标题与正文，生成该帖子可能涉及的风险标签。\n输出要求：\n1. 只输出 JSON（不要包裹 ```），格式：{"riskTags":["诈骗","隐私泄露","仇恨言论"]}。\n2. riskTags 必须使用中文短语（不要英文/拼音），每个标签不超过 8 个汉字。\n3. 标签应尽量稳定、可复用、能概括风险类型；最多输出 {{maxCount}} 个。\n4. 如果内容看起来风险很低，可以输出空数组。\n5. 不要输出解释、不要输出多余字段。',
'标题：\n{{title}}\n\n正文：\n{{content}}',
NULL, NULL, 0.2, 10, 8000)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled),
system_prompt = VALUES(system_prompt),
prompt_template = VALUES(prompt_template),
model = VALUES(model),
provider_id = VALUES(provider_id),
temperature = VALUES(temperature),
max_count = VALUES(max_count),
max_content_chars = VALUES(max_content_chars);

-- 16) 初始化：帖子风险标签生成配置（LLM）
-- 表：post_risk_tag_gen_config
-- 说明：model/provider_id 默认 NULL -> 自动路由

-- 17) 清理：删除已废弃的 EMBEDDING 任务类型（由 POST_EMBEDDING / SIMILARITY_EMBEDDING 替代）

DELETE FROM llm_routing_policies WHERE task_type = 'EMBEDDING';
DELETE FROM llm_models WHERE purpose = 'EMBEDDING';
DELETE FROM llm_routing_scenarios WHERE task_type = 'EMBEDDING';
