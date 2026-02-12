# 数据库迁移脚本分类

当前目录包含若干 Flyway 迁移脚本，按版本顺序执行。推荐按“职责”理解它们：

1. `V1__table_design.sql`：表结构/索引/约束等“表设计类”变更。
2. `V2__llm_model_default_configs.sql`：LLM 默认配置信息（provider/models/路由策略/生成任务配置/审核默认配置等）。
3. `V3__system_default_configs.sql`：系统默认配置信息（RBAC 权限/默认板块/系统开关/支持语言/检索默认配置等）。
4. `V4__llm_price_configs.sql`：LLM 模型价格预置（用于成本估算/展示）。

注意：
- 若你希望“表设计在 V1、LLM 默认配置在 V2、系统默认配置在 V3、LLM 模型价格预置在 V4”，可以把 V5 视为后续演进迁移。
- 后续新增变更请继续新增新的 `V{N}__*.sql` 迁移文件，保持版本递增且脚本幂等。
