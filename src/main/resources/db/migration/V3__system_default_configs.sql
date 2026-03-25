/*
  V3：系统默认配置（权限/RBAC、默认版块、语言、RAG 配置等）

  目标：
  - 初始化 RBAC 权限点（permissions）与默认 USER 角色权限（role_permissions）
  - 初始化系统默认版块、默认注册角色、RAG 相关默认配置、支持语言列表等

  幂等性说明：
  - 大部分写入使用 INSERT IGNORE（依赖唯一键/主键）或 WHERE NOT EXISTS
*/

-- 1) 管理员后台：访问入口 + 左侧菜单分区
-- 表：permissions
-- 字段说明：
-- - resource：资源标识（通常是页面/模块/接口的逻辑分组）
-- - action：动作（access/read/write/update/delete/action/view 等）
-- - description：中文说明（用于管理端展示）
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('admin_ui','access','进入管理员后台 (/admin) 的总开关'),
  ('admin_content','access','访问后台-内容管理模块'),
  ('admin_review','access','访问后台-审核中心模块'),
  ('admin_semantic','access','访问后台-语义增强模块'),
  ('admin_retrieval','access','访问后台-检索与RAG模块'),
  ('admin_metrics','access','访问后台-评估与监控模块'),
  ('admin_users','access','访问后台-用户与权限模块');

-- 1.1) 管理员后台：子页面/表单（更细粒度的 UI 控制，可选）
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('admin_content_board','access','后台-内容管理-版块管理'),
  ('admin_content_post','access','后台-内容管理-帖子管理'),
  ('admin_content_comment','access','后台-内容管理-评论管理'),
  ('admin_content_tags','access','后台-内容管理-标签体系管理'),
  ('admin_review_queue','access','后台-审核中心-审核队列面板'),
  ('admin_review_rules','access','后台-审核中心-规则过滤层'),
  ('admin_review_embed','access','后台-审核中心-嵌入相似检测'),
  ('admin_review_llm','access','后台-审核中心-LLM 审核层'),
  ('admin_review_fallback','access','后台-审核中心-置信回退机制'),
  ('admin_review_logs','access','后台-审核中心-审核日志与追溯'),
  ('admin_review_risk_tags','access','后台-审核中心-风险标签生成'),
  ('admin_semantic_title_gen','access','后台-语义增强-标题生成'),
  ('admin_semantic_multi_label','access','后台-语义增强-多任务标签生成'),
  ('admin_semantic_summary','access','后台-语义增强-帖子摘要'),
  ('admin_semantic_translate','access','后台-语义增强-翻译'),
  ('admin_retrieval_index','access','后台-检索与RAG-向量索引构建'),
  ('admin_retrieval_hybrid','access','后台-检索与RAG-Hybrid 检索配置'),
  ('admin_retrieval_context','access','后台-检索与RAG-动态上下文裁剪'),
  ('admin_retrieval_citation','access','后台-检索与RAG-引用与来源展示配置'),
  ('admin_metrics_metrics','access','后台-评估与监控-指标采集层'),
  ('admin_metrics_abtest','access','后台-评估与监控-实验对比脚本'),
  ('admin_metrics_token','access','后台-评估与监控-Token 成本统计'),
  ('admin_metrics_label_quality','access','后台-评估与监控-标签质量评估工具'),
  ('admin_metrics_cost','access','后台-评估与监控-审核成本分析'),
  ('admin_users_user_role','access','后台-用户与权限-用户管理'),
  ('admin_users_roles','access','后台-用户与权限-角色管理'),
  ('admin_users_matrix','access','后台-用户与权限-权限管理'),
  ('admin_users_2fa','access','后台-用户与权限-高权限操作 2FA 策略');

-- 2) 管理员后台：当前页面会调用的接口权限（API 侧）
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('admin_posts','read','后台帖子查询'),
  ('admin_posts','update','后台帖子更新/状态调整'),
  ('admin_posts','delete','后台帖子删除/软删'),

  ('admin_comments','read','后台评论查询'),
  ('admin_comments','update','后台评论状态/删除标记更新'),
  ('admin_comments','delete','后台评论删除/软删'),

  ('admin_boards','read','后台板块查询'),
  ('admin_boards','write','后台板块创建/更新/删除'),

  ('admin_tags','read','后台标签查询'),
  ('admin_tags','write','后台标签创建/更新/删除'),

  ('admin_moderation_queue','read','审核队列查询'),
  ('admin_moderation_queue','action','审核队列处理(approve/reject/backfill)'),

  ('admin_moderation_rules','read','审核规则查询'),
  ('admin_moderation_rules','write','审核规则创建/更新/删除'),

  ('admin_moderation_embed','read','嵌入相似检测配置/查询'),
  ('admin_moderation_embed','write','嵌入相似检测配置更新'),

  ('admin_moderation_llm','read','LLM 审核层配置/查询'),
  ('admin_moderation_llm','write','LLM 审核层配置更新'),

  ('admin_moderation_logs','read','审核日志查询'),

  ('admin_risk_tags','read','风险标签查询'),
  ('admin_risk_tags','write','风险标签生成/配置更新'),

  ('admin_semantic_title_gen','action','标题生成任务(触发/预览/保存)'),
  ('admin_semantic_multi_label','action','多任务标签生成任务(触发/预览/保存)'),
  ('admin_semantic_summary','action','帖子摘要生成任务(触发/预览/保存)'),
  ('admin_semantic_translate','action','翻译任务(触发/预览/保存)'),

  ('admin_retrieval_index','action','向量索引构建/重建'),
  ('admin_retrieval_hybrid','write','Hybrid 检索配置更新'),
  ('admin_retrieval_context','write','动态上下文裁剪配置更新'),
  ('admin_retrieval_citation','write','引用与来源展示配置更新'),

  ('admin_metrics_metrics','read','指标采集层读取'),
  ('admin_metrics_abtest','action','实验对比脚本执行'),
  ('admin_metrics_token','read','Token 成本统计读取'),
  ('admin_metrics_label_quality','read','标签质量评估读取'),
  ('admin_metrics_cost','read','审核成本分析读取'),

  ('admin_users','read','后台用户/权限数据读取(通用)'),
  ('admin_users','write','后台用户/权限数据写入(通用)'),
  ('admin_permissions','read','权限列表查询'),
  ('admin_permissions','write','权限创建/更新/删除'),
  ('admin_role_permissions','read','角色-权限矩阵读取'),
  ('admin_role_permissions','write','角色-权限矩阵写入/更新/删除'),
  ('admin_user_roles','read','用户-角色关联读取'),
  ('admin_user_roles','write','用户-角色关联写入/更新/清空'),
  ('admin_content_formats','access','后台-内容管理-格式管理'),
  ('admin_content_formats','write','后台-内容管理-格式管理配置更新'),
  ('admin_content_posts_compose','access','后台-内容管理-发帖表单配置'),
  ('admin_content_posts_compose','write','后台-内容管理-发帖表单配置更新'),
  ('admin_moderation_chunk_review','access','后台-审核-分片审核配置与进度查看'),
  ('admin_moderation_chunk_review','write','后台-审核-分片审核配置更新'),
  ('admin_ai_chat_context','access','后台-LLM-对话上下文治理'),
  ('admin_ai_chat_context','write','后台-LLM-对话上下文治理配置更新'),
  ('admin_metrics_llm_queue','access','后台-评估与监控-LLM 调用队列页面'),
  ('admin_metrics_llm_queue','read','后台-LLM 调用队列读取'),
  ('admin_metrics_llm_queue','write','后台-LLM 调用队列配置写入'),
  ('admin_metrics_circuit_breaker','access','后台-评估与监控-内容安全熔断'),
  ('admin_safety_circuit_breaker','read','内容安全熔断读取'),
  ('admin_safety_circuit_breaker','write','内容安全熔断配置更新/一键启停');

-- 2.1) 运维/后台工具权限
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('admin_hot_scores','action','热度分重算/运维动作');

-- 3) 前台门户：页面级权限（VIEW）
-- 说明：
-- - 建议只对“需要登录”的页面加权限校验，对公开页面保持 permitAll（否则访客无法浏览门户）
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('portal_discover','view','前台-发现页(整体)'),
  ('portal_discover_home','view','前台-发现-推荐/首页'),
  ('portal_discover_boards','view','前台-发现-版块'),
  ('portal_discover_tags','view','前台-发现-标签'),
  ('portal_discover_hot','view','前台-发现-热榜'),

  ('portal_search','view','前台-搜索(整体)'),
  ('portal_search_posts','view','前台-搜索-帖子'),

  ('portal_posts','view','前台-帖子模块(整体)'),
  ('portal_posts_detail','view','前台-帖子详情页'),
  ('portal_posts_mine','view','前台-我的帖子'),
  ('portal_posts_drafts','view','前台-草稿箱'),
  ('portal_posts_bookmarks','view','前台-收藏列表'),

  ('portal_interact','view','前台-互动中心(整体)'),
  ('portal_interact_replies','view','前台-互动-回复'),
  ('portal_interact_likes','view','前台-互动-点赞'),
  ('portal_interact_mentions','view','前台-互动-提及'),
  ('portal_interact_reports','view','前台-互动-举报'),

  ('portal_assistant','view','前台-助手(整体)'),
  ('portal_assistant_chat','view','前台-助手-聊天'),
  ('portal_assistant_history','view','前台-助手-历史'),
  ('portal_assistant_collections','view','前台-助手-收藏/知识库'),
  ('portal_assistant_settings','view','前台-助手-设置'),

  ('portal_account','view','前台-账号中心(整体)'),
  ('portal_account_profile','view','前台-账号-资料'),
  ('portal_account_security','view','前台-账号-安全'),
  ('portal_account_preferences','view','前台-账号-偏好'),
  ('portal_account_connections','view','前台-账号-绑定与连接');

-- 4) 前台门户：功能级权限（WRITE/ACTION）
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('portal_posts','create','前台发帖/保存草稿/编辑帖子'),
  ('portal_posts','update','前台编辑帖子'),
  ('portal_posts','delete','前台删除帖子(若支持)'),

  ('portal_comments','create','前台发表评论/回复'),
  ('portal_comments','delete','前台删除自己的评论(若支持)'),

  ('portal_reactions','like','前台点赞'),
  ('portal_reactions','unlike','前台取消点赞'),

  ('portal_favorites','bookmark','前台收藏'),
  ('portal_favorites','unbookmark','前台取消收藏'),

  ('portal_reports','create','前台举报内容');

-- 5) 初始化：默认版块（便于测试发帖）
-- 幂等：同租户/同父级/同名称的默认版块已存在时不重复插入

INSERT INTO boards(
  tenant_id,
  parent_id,
  name,
  description,
  visible,
  sort_order,
  created_at,
  updated_at
)
SELECT
  NULL,
  NULL,
  '默认版块',
  '系统初始化默认版块，用于测试发帖功能',
  1,
  0,
  NOW(3),
  NOW(3)
WHERE NOT EXISTS (
  SELECT 1
  FROM boards b
  WHERE b.tenant_id IS NULL
  AND b.parent_id IS NULL
  AND b.name = '默认版块'
);

INSERT IGNORE INTO roles(role_id, role_name, builtin, immutable)
VALUES (1, 'USER', 1, 1);

INSERT IGNORE INTO app_settings(k, v)
SELECT
  'default_register_role_id',
  CAST(r.role_id AS CHAR)
FROM roles r
WHERE r.role_name = 'USER';

-- 6) 初始化：基线 USER 角色并授予所有 portal_* 权限
-- 说明：
-- - 本项目通过 role_permissions 推断角色（无单独 roles 表）
-- - 前台路由由 RBAC 权限控制
-- 幂等：INSERT IGNORE 依赖主键 (role_id, permission_id)

INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  r.role_id,
  r.role_name,
  p.id,
  1
FROM roles r
JOIN permissions p
  ON p.resource LIKE 'portal\_%'
WHERE r.role_name = 'USER';

-- 9) 默认配置：moderation_samples 增量同步（MySQL -> ES）
-- 说明：默认关闭，避免后台自动向量化带来不可预期成本

INSERT IGNORE INTO app_settings(k, v) VALUES ('moderation.samples.autoSync.enabled', 'false');
INSERT IGNORE INTO app_settings(k, v) VALUES ('moderation.samples.autoSync.intervalSeconds', '60');

-- 10) 默认配置：相似检测样本库 ES 索引 + 相似检测运行时配置
-- 幂等：表为空时才插入

INSERT INTO moderation_samples_index_config (
  index_name,
  ik_enabled,
  embedding_model,
  embedding_dims,
  updated_at
)
SELECT
  'ad_violation_samples_v1',
  1,
  NULL,
  0,
  NOW(3)
WHERE NOT EXISTS (
  SELECT 1 FROM moderation_samples_index_config
);

INSERT INTO moderation_similarity_config (
  enabled,
  embedding_model,
  embedding_dims,
  max_input_chars,
  default_top_k,
  default_num_candidates,
  updated_at
)
SELECT
  1,
  NULL,
  0,
  0,
  5,
  0,
  NOW(3)
WHERE NOT EXISTS (
  SELECT 1 FROM moderation_similarity_config
);

INSERT IGNORE INTO app_settings (k, v, updated_at)
VALUES
('retrieval_rag_config', '{
  "enabled": true,
  "provider": "MILVUS",
  "embeddingModel": "",
  "embeddingDim": 1024,
  "collectionName": "rag_chunks",
  "topK": 100,
  "scoreThreshold": 0.0
}', NOW()),
('retrieval_hybrid_config', '{
  "enabled": true,
  "bm25K": 50,
  "bm25TitleBoost": 2,
  "bm25ContentBoost": 1,
  "vecK": 50,
  "fileVecEnabled": true,
  "fileVecK": 30,
  "hybridK": 30,
  "fusionMode": "RRF",
  "bm25Weight": 1,
  "vecWeight": 1,
  "fileVecWeight": 1,
  "rrfK": 60,
  "rerankEnabled": true,
  "rerankModel": "",
  "rerankTemperature": 0,
  "rerankK": 30,
  "maxDocs": 500,
  "perDocMaxTokens": 4000,
  "maxInputTokens": 30000
}', NOW());

INSERT INTO vector_indices (provider, collection_name, metric, dim, status, metadata)
SELECT
  'OTHER',
  'rag_post_chunks_v1',
  'cosine',
  1024,
  'READY',
  JSON_OBJECT(
    'sourceType', 'POST',
    'embeddingModel', '',
    'defaultChunkMaxChars', 800,
    'defaultChunkOverlapChars', 80
  )
WHERE NOT EXISTS (
  SELECT 1 FROM vector_indices
  WHERE provider = 'OTHER' AND collection_name = 'rag_post_chunks_v1'
);

INSERT INTO vector_indices (provider, collection_name, metric, dim, status, metadata)
SELECT
  'OTHER',
  'rag_post_chunks_v1_comments',
  'cosine',
  1024,
  'READY',
  JSON_OBJECT(
    'sourceType', 'COMMENT',
    'embeddingModel', '',
    'defaultChunkMaxChars', 800,
    'defaultChunkOverlapChars', 80
  )
WHERE NOT EXISTS (
  SELECT 1 FROM vector_indices
  WHERE provider = 'OTHER' AND collection_name = 'rag_post_chunks_v1_comments'
);

INSERT INTO vector_indices (provider, collection_name, metric, dim, status, metadata)
SELECT
  'OTHER',
  'rag_file_assets_v1',
  'cosine',
  1024,
  'READY',
  JSON_OBJECT(
    'sourceType', 'FILE_ASSET',
    'embeddingModel', '',
    'defaultChunkMaxChars', 1200,
    'defaultChunkOverlapChars', 120
  )
WHERE NOT EXISTS (
  SELECT 1 FROM vector_indices
  WHERE provider = 'OTHER' AND collection_name = 'rag_file_assets_v1'
);

-- 11) 默认配置：RAG 与 Hybrid 检索配置（JSON 存入 app_settings）
-- 说明：
-- - retrieval_rag_config：向量检索基础配置
-- - retrieval_hybrid_config：BM25 + 向量融合 + rerank 等混合检索配置

INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  rp.role_id,
  rp.role_name,
  p_new.id,
  1
FROM role_permissions rp
JOIN permissions p_old
  ON p_old.id = rp.permission_id
  AND p_old.resource = 'admin_metrics'
  AND p_old.action = 'access'
  AND rp.allow = 1
JOIN permissions p_new
  ON p_new.resource = 'admin_metrics_circuit_breaker'
  AND p_new.action = 'access';
INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  rp.role_id,
  rp.role_name,
  p_new.id,
  1
FROM role_permissions rp
JOIN permissions p_old
  ON p_old.id = rp.permission_id
  AND p_old.resource = 'admin_metrics'
  AND p_old.action = 'access'
  AND rp.allow = 1
JOIN permissions p_new
  ON p_new.resource = 'admin_safety_circuit_breaker'
  AND p_new.action = 'read';
INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  rp.role_id,
  rp.role_name,
  p_write.id,
  1
FROM role_permissions rp
JOIN permissions p_read
  ON p_read.id = rp.permission_id
  AND p_read.resource = 'admin_safety_circuit_breaker'
  AND p_read.action = 'read'
  AND rp.allow = 1
JOIN permissions p_write
  ON p_write.resource = 'admin_safety_circuit_breaker'
  AND p_write.action = 'write';

-- Insert risk tags forcefully (no IGNORE) to ensure they exist
INSERT INTO tags (tenant_id, type, name, slug, description, is_system, is_active, threshold, created_at)
SELECT
  v.tenant_id,
  v.type,
  v.name,
  v.slug,
  v.description,
  v.is_system,
  v.is_active,
  v.threshold,
  v.created_at
FROM (
  SELECT NULL AS tenant_id, 'RISK' AS type, '政治敏感' AS name, 'political' AS slug, '涉及政治敏感内容' AS description, 1 AS is_system, 1 AS is_active, 0.5 AS threshold, NOW() AS created_at
  UNION ALL SELECT NULL, 'RISK', '暴力血腥', 'violence', '涉及暴力血腥内容', 1, 1, 0.5, NOW()
  UNION ALL SELECT NULL, 'RISK', '诈骗引流', 'scam', '涉及诈骗、引流、杀猪盘等', 1, 1, 0.85, NOW()
  UNION ALL SELECT NULL, 'RISK', '色情低俗', 'pornography', '涉及色情低俗内容', 1, 1, 0.5, NOW()
  UNION ALL SELECT NULL, 'RISK', '辱骂攻击', 'abuse', '涉及辱骂、人身攻击', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '违禁品', 'contraband', '涉及违禁品（毒品、枪支等）', 1, 1, 0.7, NOW()
  UNION ALL SELECT NULL, 'RISK', '未成年人', 'minor', '涉及未成年人不良内容', 1, 1, 0.1, NOW()
  UNION ALL SELECT NULL, 'RISK', '垃圾广告', 'advertisement', '垃圾广告', 1, 1, 0.8, NOW()
  UNION ALL SELECT NULL, 'RISK', '无意义灌水', 'meaningless', '无意义灌水', 1, 1, 0.9, NOW()
  UNION ALL SELECT NULL, 'RISK', '负面情绪', 'negative', '负面情绪、丧文化', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '宗教敏感', 'religion', '涉及宗教敏感内容', 1, 1, 0.5, NOW()
  UNION ALL SELECT NULL, 'RISK', '恐怖主义', 'terrorism', '涉及恐怖主义', 1, 1, 0.1, NOW()
  UNION ALL SELECT NULL, 'RISK', '赌博博彩', 'gambling', '涉及赌博、博彩内容', 1, 1, 0.8, NOW()
  UNION ALL SELECT NULL, 'RISK', '仇恨言论', 'hate-speech', '涉及种族、性别、地域等仇恨言论', 1, 1, 0.4, NOW()
  UNION ALL SELECT NULL, 'RISK', '自残自杀', 'self-harm', '涉及自残、自杀、厌世内容', 1, 1, 0.3, NOW()
  UNION ALL SELECT NULL, 'RISK', '隐私泄露', 'privacy', '涉及他人隐私泄露', 1, 1, 0.7, NOW()
  UNION ALL SELECT NULL, 'RISK', '谣言', 'rumor', '传播谣言、不实信息', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '歧视', 'discrimination', '涉及性别、职业、身体等歧视', 1, 1, 0.5, NOW()
  UNION ALL SELECT NULL, 'RISK', '诱导点击', 'clickbait', '夸大其词、诱导点击', 1, 1, 0.8, NOW()
  UNION ALL SELECT NULL, 'RISK', '低质内容', 'low-quality', '内容空洞、排版混乱', 1, 1, 0.9, NOW()
  UNION ALL SELECT NULL, 'RISK', '竞品拉踩', 'competitor', '竞品宣传、恶意拉踩', 1, 1, 0.9, NOW()
  UNION ALL SELECT NULL, 'RISK', '虐待动物', 'animal-abuse', '虐待、残害动物', 1, 1, 0.4, NOW()
  UNION ALL SELECT NULL, 'RISK', '非法服务', 'illegal-service', '办证、洗钱、代孕等非法服务', 1, 1, 0.7, NOW()
  UNION ALL SELECT NULL, 'RISK', '二维码引流', 'qrcode', '包含不明二维码，疑似引流', 1, 1, 0.85, NOW()
  UNION ALL SELECT NULL, 'RISK', '邪教', 'cult', '涉及邪教组织、教义', 1, 1, 0.1, NOW()
  UNION ALL SELECT NULL, 'RISK', '传销', 'pyramid-scheme', '涉及传销、拉人头', 1, 1, 0.2, NOW()
  UNION ALL SELECT NULL, 'RISK', '非法集资', 'illegal-fundraising', '涉及非法集资、庞氏骗局', 1, 1, 0.2, NOW()
  UNION ALL SELECT NULL, 'RISK', '贩卖人口', 'human-trafficking', '涉及贩卖人口、器官', 1, 1, 0.1, NOW()
  UNION ALL SELECT NULL, 'RISK', '网络暴力', 'cyberbullying', '网络暴力、群体攻击', 1, 1, 0.4, NOW()
  UNION ALL SELECT NULL, 'RISK', '人身攻击', 'personal-attack', '针对个人的恶意攻击、谩骂', 1, 1, 0.5, NOW()
  UNION ALL SELECT NULL, 'RISK', '血腥恐怖', 'gore', '极其血腥、恐怖、令人不适的画面', 1, 1, 0.3, NOW()
  UNION ALL SELECT NULL, 'RISK', '软色情', 'overexposure', '衣着暴露、软色情、擦边球', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '引战对立', 'provocation', '故意引战、制造对立', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '地域歧视', 'regional-discrimination', '地域歧视、地图炮', 1, 1, 0.5, NOW()
  UNION ALL SELECT NULL, 'RISK', '虚假宣传', 'false-advertising', '虚假宣传、夸大功效', 1, 1, 0.7, NOW()
  UNION ALL SELECT NULL, 'RISK', '侵犯版权', 'copyright-infringement', '侵犯版权、盗版资源', 1, 1, 0.8, NOW()
  UNION ALL SELECT NULL, 'RISK', '抄袭洗稿', 'plagiarism', '抄袭、洗稿', 1, 1, 0.8, NOW()
  UNION ALL SELECT NULL, 'RISK', '刷屏', 'spamming', '恶意刷屏、重复发布', 1, 1, 0.9, NOW()
  UNION ALL SELECT NULL, 'RISK', '烟酒推广', 'tobacco-alcohol', '涉及烟草、酒精推广', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '野生动物交易', 'wildlife-trade', '涉及非法野生动物交易', 1, 1, 0.3, NOW()
  UNION ALL SELECT NULL, 'RISK', '非法医疗', 'illegal-medical', '非法行医、违规药品', 1, 1, 0.4, NOW()
  UNION ALL SELECT NULL, 'RISK', '黑客攻击', 'hacking', '黑客攻击、木马病毒、网络入侵', 1, 1, 0.3, NOW()
  UNION ALL SELECT NULL, 'RISK', '恶意骚扰', 'harassment', '恶意骚扰、跟踪、威胁', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '封建迷信', 'superstition', '封建迷信、伪科学', 1, 1, 0.6, NOW()
  UNION ALL SELECT NULL, 'RISK', '其他', 'other', '其他未分类违规内容', 1, 1, 0.5, NOW()
) v
LEFT JOIN tags t
  ON t.tenant_id <=> v.tenant_id
  AND t.type = v.type
  AND t.slug = v.slug
WHERE t.id IS NULL;

INSERT INTO system_configurations (config_key, config_value, is_encrypted, description)
VALUES ('image.storage.mode', 'DASHSCOPE_TEMP', FALSE, '图片存储模式: LOCAL / DASHSCOPE_TEMP / ALIYUN_OSS')
ON DUPLICATE KEY UPDATE
    config_value = CASE
        WHEN config_value IS NULL OR TRIM(config_value) = '' THEN VALUES(config_value)
        ELSE config_value
    END,
    is_encrypted = VALUES(is_encrypted),
    description = VALUES(description);

INSERT IGNORE INTO system_configurations (config_key, config_value, is_encrypted, description)
VALUES
    ('image.storage.local.base_url', '', FALSE, 'LOCAL 模式下的公网域名 (如 https://example.com)'),
    ('image.storage.dashscope.model', 'qwen-vl-plus', FALSE, '百炼临时存储绑定的模型名称'),
    ('image.storage.oss.endpoint', '', TRUE, '阿里云 OSS Endpoint (如 oss-cn-hangzhou.aliyuncs.com)'),
    ('image.storage.oss.bucket', '', FALSE, '阿里云 OSS Bucket 名称'),
    ('image.storage.oss.access_key_id', '', TRUE, '阿里云 OSS AccessKey ID'),
    ('image.storage.oss.access_key_secret', '', TRUE, '阿里云 OSS AccessKey Secret'),
    ('image.storage.oss.region', '', FALSE, '阿里云 OSS Region (如 cn-hangzhou)'),
    ('image.compression.enabled', 'true', FALSE, '是否在上传前压缩图片'),
    ('image.compression.max_width', '1920', FALSE, '压缩最大宽度 (px)'),
    ('image.compression.max_height', '1920', FALSE, '压缩最大高度 (px)'),
    ('image.compression.quality', '0.85', FALSE, 'JPEG 压缩质量 (0.0-1.0)'),
    ('image.compression.max_bytes', '500000', FALSE, '压缩后最大字节数');

INSERT IGNORE INTO permissions(resource, action, description) VALUES
    ('admin_ai_image_storage', 'access', '后台-LLM-图片存储管理查看'),
    ('admin_ai_image_storage', 'write', '后台-LLM-图片存储管理配置更新');

INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  rp.role_id,
  rp.role_name,
  p_new.id,
  1
FROM role_permissions rp
JOIN permissions p_old
  ON p_old.id = rp.permission_id
  AND p_old.resource = 'admin_review'
  AND p_old.action = 'access'
  AND rp.allow = 1
JOIN permissions p_new
  ON p_new.resource = 'admin_ai_image_storage'
  AND p_new.action = 'access';

INSERT IGNORE INTO role_permissions(role_id, role_name, permission_id, allow)
SELECT
  rp.role_id,
  rp.role_name,
  p_new.id,
  1
FROM role_permissions rp
JOIN permissions p_old
  ON p_old.id = rp.permission_id
  AND p_old.resource = 'admin_moderation_llm'
  AND p_old.action = 'write'
  AND rp.allow = 1
JOIN permissions p_new
  ON p_new.resource = 'admin_ai_image_storage'
  AND p_new.action = 'write';



INSERT INTO supported_languages (language_code, display_name, native_name, is_active, sort_order) VALUES
-- 表：supported_languages
-- 字段说明：
-- - language_code：语言代码（优先 BCP-47/ISO）
-- - display_name/native_name：显示名称/本地名称
-- - is_active：是否启用
-- - sort_order：排序顺序
('en', '英语（English）', 'English', 1, 1),
('zh-CN', '简体中文（Simplified Chinese）', '简体中文', 1, 2),
('zh-TW', '繁体中文（Traditional Chinese）', '繁體中文', 1, 3),
('fr', '法语（French）', 'Français', 1, 4),
('es', '西班牙语（Spanish）', 'Español', 1, 5),
('ar', '阿拉伯语（Arabic）', 'العربية', 1, 6),
('ru', '俄语（Russian）', 'Русский', 1, 7),
('pt', '葡萄牙语（Portuguese）', 'Português', 1, 8),
('de', '德语（German）', 'Deutsch', 1, 9),
('it', '意大利语（Italian）', 'Italiano', 1, 10),
('nl', '荷兰语（Dutch）', 'Nederlands', 1, 11),
('da', '丹麦语（Danish）', 'Dansk', 1, 12),
('ga', '爱尔兰语（Irish）', 'Gaeilge', 1, 13),
('cy', '威尔士语（Welsh）', 'Cymraeg', 1, 14),
('fi', '芬兰语（Finnish）', 'Suomi', 1, 15),
('is', '冰岛语（Icelandic）', 'Íslenska', 1, 16),
('sv', '瑞典语（Swedish）', 'Svenska', 1, 17),
('nn', '新挪威语（Norwegian Nynorsk）', 'Norsk nynorsk', 1, 18),
('nb', '书面挪威语（Norwegian Bokmål）', 'Norsk bokmål', 1, 19),
('ja', '日语（Japanese）', '日本語', 1, 20),
('ko', '朝鲜语/韩语（Korean）', '한국어', 1, 21),
('vi', '越南语（Vietnamese）', 'Tiếng Việt', 1, 22),
('th', '泰语（Thai）', 'ไทย', 1, 23),
('id', '印度尼西亚语（Indonesian）', 'Bahasa Indonesia', 1, 24),
('ms', '马来语（Malay）', 'Bahasa Melayu', 1, 25),
('my', '缅甸语（Burmese）', 'မြန်မာဘာသာ', 1, 26),
('tl', '他加禄语（Tagalog）', 'Tagalog', 1, 27),
('km', '高棉语（Khmer）', 'ខ្មែរ', 1, 28),
('lo', '老挝语（Lao）', 'ລາວ', 1, 29),
('hi', '印地语（Hindi）', 'हिन्दी', 1, 30),
('bn', '孟加拉语（Bengali）', 'বাংলা', 1, 31),
('ur', '乌尔都语（Urdu）', 'اردو', 1, 32),
('ne', '尼泊尔语（Nepali）', 'नेपाली', 1, 33),
('he', '希伯来语（Hebrew）', 'עברית', 1, 34),
('tr', '土耳其语（Turkish）', 'Türkçe', 1, 35),
('fa', '波斯语（Persian）', 'فارسی', 1, 36),
('pl', '波兰语（Polish）', 'Polski', 1, 37),
('uk', '乌克兰语（Ukrainian）', 'Українська', 1, 38),
('cs', '捷克语（Czech）', 'Čeština', 1, 39),
('ro', '罗马尼亚语（Romanian）', 'Română', 1, 40),
('bg', '保加利亚语（Bulgarian）', 'Български', 1, 41),
('sk', '斯洛伐克语（Slovak）', 'Slovenčina', 1, 42),
('hu', '匈牙利语（Hungarian）', 'Magyar', 1, 43),
('sl', '斯洛文尼亚语（Slovenian）', 'Slovenščina', 1, 44),
('lv', '拉脱维亚语（Latvian）', 'Latviešu', 1, 45),
('et', '爱沙尼亚语（Estonian）', 'Eesti', 1, 46),
('lt', '立陶宛语（Lithuanian）', 'Lietuvių', 1, 47),
('be', '白俄罗斯语（Belarusian）', 'Беларуская', 1, 48),
('el', '希腊语（Greek）', 'Ελληνικά', 1, 49),
('hr', '克罗地亚语（Croatian）', 'Hrvatski', 1, 50),
('mk', '马其顿语（Macedonian）', 'Македонски', 1, 51),
('mt', '马耳他语（Maltese）', 'Malti', 1, 52),
('sr', '塞尔维亚语（Serbian）', 'Српски', 1, 53),
('bs', '波斯尼亚语（Bosnian）', 'Bosanski', 1, 54),
('ka', '格鲁吉亚语（Georgian）', 'ქართული', 1, 55),
('hy', '亚美尼亚语（Armenian）', 'Հայերեն', 1, 56),
('azj', '北阿塞拜疆语（North Azerbaijani）', 'Azərbaycanca', 1, 57),
('kk', '哈萨克语（Kazakh）', 'Қазақша', 1, 58),
('uzn', '北乌兹别克语（Northern Uzbek）', 'Oʻzbekcha', 1, 59),
('tg', '塔吉克语（Tajik）', 'Тоҷикӣ', 1, 60),
('sw', '斯瓦西里语（Swahili）', 'Kiswahili', 1, 61),
('af', '南非语（Afrikaans）', 'Afrikaans', 1, 62),
('yue', '粤语（Cantonese）', '粵語', 1, 63),
('lb', '卢森堡语（Luxembourgish）', 'Lëtzebuergesch', 1, 64),
('li', '林堡语（Limburgish）', 'Limburgs', 1, 65),
('ca', '加泰罗尼亚语（Catalan）', 'Català', 1, 66),
('gl', '加利西亚语（Galician）', 'Galego', 1, 67),
('ast', '阿斯图里亚斯语（Asturian）', 'Asturianu', 1, 68),
('eu', '巴斯克语（Basque）', 'Euskara', 1, 69),
('oc', '奥克语（Occitan）', 'Occitan', 1, 70),
('vec', '威尼斯语（Venetian）', 'Vèneto', 1, 71),
('sc', '撒丁语（Sardinian）', 'Sardu', 1, 72),
('scn', '西西里语（Sicilian）', 'Sicilianu', 1, 73),
('fur', '弗留利语（Friulian）', 'Furlan', 1, 74),
('lmo', '隆巴底语（Lombard）', 'Lombard', 1, 75),
('lij', '利古里亚语（Ligurian）', 'Ligure', 1, 76),
('fo', '法罗语（Faroese）', 'Føroyskt', 1, 77),
('als', '托斯克阿尔巴尼亚语（Tosk Albanian）', 'Shqip', 1, 78),
('szl', '西里西亚语（Silesian）', 'Ślōnskŏ gŏdka', 1, 79),
('ba', '巴什基尔语（Bashkir）', 'Башҡорт', 1, 80),
('tt', '鞑靼语（Tatar）', 'Татар', 1, 81),
('acm', '美索不达米亚阿拉伯语（Mesopotamian Arabic）', 'العربية', 1, 82),
('ars', '内志阿拉伯语（Najdi Arabic）', 'العربية', 1, 83),
('arz', '埃及阿拉伯语（Egyptian Arabic）', 'العربية', 1, 84),
('apc', '黎凡特阿拉伯语（Levantine Arabic）', 'العربية', 1, 85),
('acq', '闪米特阿拉伯语（Ta''izzi-Adeni Arabic）', 'العربية', 1, 86),
('prs', '达里语（Dari）', 'دری', 1, 87),
('aeb', '突尼斯阿拉伯语（Tunisian Arabic）', 'العربية', 1, 88),
('ary', '摩洛哥阿拉伯语（Moroccan Arabic）', 'العربية', 1, 89),
('kea', '克里奥尔语（Kabuverdianu）', 'Kabuverdianu', 1, 90),
('tpi', '托克皮辛语（Tok Pisin）', 'Tok Pisin', 1, 91),
('ydd', '意第绪（Eastern Yiddish）', 'ייִדיש', 1, 92),
('sd', '信德阿拉伯语（Sindhi）', 'سنڌي', 1, 93),
('si', '僧伽罗语（Sinhala）', 'සිංහල', 1, 94),
('te', '泰卢固语（Telugu）', 'తెలుగు', 1, 95),
('pa', '旁遮普语（Punjabi）', 'ਪੰਜਾਬੀ', 1, 96),
('ta', '泰米尔语（Tamil）', 'தமிழ்', 1, 97),
('gu', '古吉拉特语（Gujarati）', 'ગુજરાતી', 1, 98),
('ml', '马拉雅拉姆语（Malayalam）', 'മലയാളം', 1, 99),
('mr', '马拉地语（Marathi）', 'मराठी', 1, 100),
('kn', '卡纳达语（Kannada）', 'ಕನ್ನಡ', 1, 101),
('mag', '马加拉语（Magahi）', 'मगही', 1, 102),
('or', '奥里亚语（Oriya）', 'ଓଡ଼ିଆ', 1, 103),
('awa', '阿瓦德语（Awadhi）', 'अवधी', 1, 104),
('mai', '迈蒂利语（Maithili）', 'मैथिली', 1, 105),
('as', '阿萨姆语（Assamese）', 'অসমীয়া', 1, 106),
('hne', '切蒂斯格尔语（Chhattisgarhi）', 'छत्तीसगढ़ी', 1, 107),
('bho', '比哈尔语（Bhojpuri）', 'भोजपुरी', 1, 108),
('min', '米南加保语（Minangkabau）', 'Minangkabau', 1, 109),
('ban', '巴厘语（Balinese）', 'Basa Bali', 1, 110),
('jv', '爪哇语（Javanese）', 'Basa Jawa', 1, 111),
('bjn', '班章语（Banjar）', 'Banjar', 1, 112),
('su', '巽他语（Sundanese）', 'Basa Sunda', 1, 113),
('ceb', '宿务语（Cebuano）', 'Cebuano', 1, 114),
('pag', '邦阿西楠语（Pangasinan）', 'Pangasinan', 1, 115),
('ilo', '伊洛卡诺语（Iloko）', 'Ilokano', 1, 116),
('war', '瓦莱语（Waray (Philippines)）', 'Winaray', 1, 117),
('ht', '海地语（Haitian）', 'Kreyòl ayisyen', 1, 118),
('pap', '帕皮阿门托语（Papiamento）', 'Papiamentu', 1, 119);
