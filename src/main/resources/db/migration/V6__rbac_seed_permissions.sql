-- Seed core RBAC permissions for whitelist enforcement.
-- Idempotent: uses INSERT IGNORE based on UNIQUE(resource, action)

-- 1) Admin UI access + sidebar sections
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('admin_ui','access','进入管理员后台 (/admin) 的总开关'),
  ('admin_content','access','访问后台-内容管理模块'),
  ('admin_review','access','访问后台-审核中心模块'),
  ('admin_semantic','access','访问后台-语义增强模块'),
  ('admin_retrieval','access','访问后台-检索与RAG模块'),
  ('admin_metrics','access','访问后台-评估与监控模块'),
  ('admin_users','access','访问后台-用户与权限模块');

-- 1.1) Admin sub-pages/forms (fine-grained UI gating, optional)
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

-- 2) Admin APIs used by current pages
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
  ('admin_user_roles','write','用户-角色关联写入/更新/清空');

-- 2.1) Ops/admin tools
INSERT IGNORE INTO permissions(resource, action, description) VALUES
  ('admin_hot_scores','action','热度分重算/运维动作');

-- 3) Portal (frontend) page-level permissions (VIEW)
-- If you want "每个页面都需要权限"，建议只对需要登录的页面加 RequirePermission，
-- 对公开浏览页面保持 permitAll（否则访客无法浏览门户）。
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

-- 4) Portal feature permissions (WRITE/ACTION)
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
