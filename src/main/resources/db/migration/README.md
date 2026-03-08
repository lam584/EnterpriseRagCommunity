# 数据库迁移脚本分类

当前目录包含 5 个 Flyway 迁移脚本，按版本顺序执行。推荐按"职责"理解它们：

1. `V1__table_design.sql`：表结构/索引/约束/审核策略等"表设计类"定义（含 moderation_policy_config 初始数据）。
2. `V2__llm_model_default_configs.sql`：LLM 默认配置信息（prompts/provider/models/路由策略/生成任务配置/审核默认配置等）。
3. `V3__system_default_configs.sql`：系统默认配置信息（RBAC 权限/默认板块/系统开关/支持语言/检索默认配置等）。
4. `V4__llm_price_configs.sql`：LLM 模型价格预置（用于成本估算/展示）。
5. `V5__context_window_log_fields_and_context_defaults.sql`：扩展 context_windows 日志字段，并升级动态上下文裁剪的 app_settings 默认配置。

注意：
- V1-V4 已合并了历史演进迁移（原 V5-V8），适用于全新部署。
- 后续新增变更请继续新增新的 `V6__*.sql` 及以后的迁移文件，保持版本递增且脚本幂等。
