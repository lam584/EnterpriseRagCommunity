-- V4：LLM 价格配置（币种：CNY）
-- 说明：
-- - 本表同时保留 input_cost_per_1k / output_cost_per_1k（按每 1K token 计价的旧字段），但当前以 metadata.pricing 为主。
-- - pricing.unit = PER_1M：单价按“每 100 万 token”。
-- - pricing.strategy：
--   - FLAT：统一单价。使用 defaultInputCostPerUnit / defaultOutputCostPerUnit；部分模型还区分 thinking / nonThinking。
--   - TIERED：分段阶梯。tiers 以 upToTokens（本次请求总 token 上限）匹配，对应 inputCostPerUnit / outputCostPerUnit。
-- - 迁移脚本使用 ON DUPLICATE KEY UPDATE，确保重复执行时幂等。

-- Qwen3 Max：分段阶梯计价（常规版本）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-max',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 2.5, 'outputCostPerUnit', 10),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 4, 'outputCostPerUnit', 16),
    JSON_OBJECT('upToTokens', 252000, 'inputCostPerUnit', 7, 'outputCostPerUnit', 28)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Max：分段阶梯计价（2028-01-23 定价版本）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-max-2028-01-23',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 2.5, 'outputCostPerUnit', 10),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 4, 'outputCostPerUnit', 16),
    JSON_OBJECT('upToTokens', 252000, 'inputCostPerUnit', 7, 'outputCostPerUnit', 28)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Max：分段阶梯计价（2025-09-23 定价版本）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-max-2025-09-23',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 6, 'outputCostPerUnit', 24),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 10, 'outputCostPerUnit', 40),
    JSON_OBJECT('upToTokens', 252000, 'inputCostPerUnit', 15, 'outputCostPerUnit', 60)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Max：分段阶梯计价（预览版）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-max-preview',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 6, 'outputCostPerUnit', 24),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 10, 'outputCostPerUnit', 40),
    JSON_OBJECT('upToTokens', 252000, 'inputCostPerUnit', 15, 'outputCostPerUnit', 60)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Next 80B（A3B）：思考版（thinking）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-next-80b-a3b-thinking',
  'CNY',
  0.001,
  0.01,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 1,
    'defaultOutputCostPerUnit', 10,
    'thinkingInputCostPerUnit', 1,
    'thinkingOutputCostPerUnit', 10
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Next 80B（A3B）：指令版（instruct，非思考 nonThinking）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-next-80b-a3b-instruct',
  'CNY',
  0.001,
  0.004,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 1,
    'defaultOutputCostPerUnit', 4,
    'nonThinkingInputCostPerUnit', 1,
    'nonThinkingOutputCostPerUnit', 4
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 235B（A22B）：思考版（thinking，2507 版本）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-235b-a22b-thinking-2507',
  'CNY',
  0.002,
  0.02,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 2,
    'defaultOutputCostPerUnit', 20,
    'thinkingInputCostPerUnit', 2,
    'thinkingOutputCostPerUnit', 20
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 235B（A22B）：指令版（instruct，2507 版本）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-235b-a22b-instruct-2507',
  'CNY',
  0.002,
  0.008,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 2,
    'defaultOutputCostPerUnit', 8,
    'nonThinkingInputCostPerUnit', 2,
    'nonThinkingOutputCostPerUnit', 8
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 30B（A3B）：思考版（thinking，2507 版本）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-30b-a3b-thinking-2507',
  'CNY',
  0.00075,
  0.0075,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 0.75,
    'defaultOutputCostPerUnit', 7.5,
    'thinkingInputCostPerUnit', 0.75,
    'thinkingOutputCostPerUnit', 7.5
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 30B（A3B）：指令版（instruct，2507 版本）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-30b-a3b-instruct-2507',
  'CNY',
  0.00075,
  0.003,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 0.75,
    'defaultOutputCostPerUnit', 3,
    'nonThinkingInputCostPerUnit', 0.75,
    'nonThinkingOutputCostPerUnit', 3
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 235B（A22B）：通用版（不区分思考/非思考）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-235b-a22b',
  'CNY',
  0.002,
  0.02,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 2, 'defaultOutputCostPerUnit', 20))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 32B：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-32b',
  'CNY',
  0.002,
  0.02,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 2, 'defaultOutputCostPerUnit', 20))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 30B（A3B）：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-30b-a3b',
  'CNY',
  0.00075,
  0.01,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.75, 'defaultOutputCostPerUnit', 10))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 14B：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-14b',
  'CNY',
  0.001,
  0.01,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 1, 'defaultOutputCostPerUnit', 10))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 8B：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-8b',
  'CNY',
  0.0005,
  0.005,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.5, 'defaultOutputCostPerUnit', 5))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 4B：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-4b',
  'CNY',
  0.0003,
  0.003,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.3, 'defaultOutputCostPerUnit', 3))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 1.7B：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-1.7b',
  'CNY',
  0.0003,
  0.003,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.3, 'defaultOutputCostPerUnit', 3))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 0.6B：通用版 /默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-0.6b',
  'CNY',
  0.0003,
  0.003,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.3, 'defaultOutputCostPerUnit', 3))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 235B（A22B）：视觉多模态思考版（thinking）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-235b-a22b-thinking',
  'CNY',
  0.002,
  0.02,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 2,
    'defaultOutputCostPerUnit', 20,
    'thinkingInputCostPerUnit', 2,
    'thinkingOutputCostPerUnit', 20
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 235B（A22B）：视觉多模态指令版（instruct）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-235b-a22b-instruct',
  'CNY',
  0.002,
  0.008,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 2,
    'defaultOutputCostPerUnit', 8,
    'nonThinkingInputCostPerUnit', 2,
    'nonThinkingOutputCostPerUnit', 8
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 32B：视觉多模态思考版（thinking）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-32b-thinking',
  'CNY',
  0.002,
  0.02,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 2,
    'defaultOutputCostPerUnit', 20,
    'thinkingInputCostPerUnit', 2,
    'thinkingOutputCostPerUnit', 20
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 32B：视觉多模态指令版（instruct）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-32b-instruct',
  'CNY',
  0.002,
  0.008,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 2,
    'defaultOutputCostPerUnit', 8,
    'nonThinkingInputCostPerUnit', 2,
    'nonThinkingOutputCostPerUnit', 8
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 30B（A3B）：视觉多模态思考版（thinking）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-30b-a3b-thinking',
  'CNY',
  0.00075,
  0.0075,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 0.75,
    'defaultOutputCostPerUnit', 7.5,
    'thinkingInputCostPerUnit', 0.75,
    'thinkingOutputCostPerUnit', 7.5
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 30B（A3B）：视觉多模态指令版（instruct）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-30b-a3b-instruct',
  'CNY',
  0.00075,
  0.003,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 0.75,
    'defaultOutputCostPerUnit', 3,
    'nonThinkingInputCostPerUnit', 0.75,
    'nonThinkingOutputCostPerUnit', 3
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 8B：视觉多模态思考版（thinking）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-8b-thinking',
  'CNY',
  0.0005,
  0.005,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 0.5,
    'defaultOutputCostPerUnit', 5,
    'thinkingInputCostPerUnit', 0.5,
    'thinkingOutputCostPerUnit', 5
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 VL 8B：视觉多模态指令版（instruct）/默认单价（每 100 万 token）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-vl-8b-instruct',
  'CNY',
  0.0005,
  0.002,
  JSON_OBJECT('pricing', JSON_OBJECT(
    'strategy', 'FLAT',
    'unit', 'PER_1M',
    'defaultInputCostPerUnit', 0.5,
    'defaultOutputCostPerUnit', 2,
    'nonThinkingInputCostPerUnit', 0.5,
    'nonThinkingOutputCostPerUnit', 2
  ))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder 480B（A35B）：代码模型（指令）/分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-480b-a35b-instruct',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 6, 'outputCostPerUnit', 24),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 9, 'outputCostPerUnit', 36),
    JSON_OBJECT('upToTokens', 200000, 'inputCostPerUnit', 15, 'outputCostPerUnit', 60)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder 30B（A3B）：代码模型（指令）/分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-30b-a3b-instruct',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 1.5, 'outputCostPerUnit', 6),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 2.25, 'outputCostPerUnit', 9),
    JSON_OBJECT('upToTokens', 200000, 'inputCostPerUnit', 3.75, 'outputCostPerUnit', 15)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- 向量化（Embedding）：文本向量模型 v4 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'text-embedding-v4',
  'CNY',
  0.0005,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.5, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 向量化（Embedding）：文本向量模型 v3 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'text-embedding-v3',
  'CNY',
  0.0005,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.5, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 向量化（Embedding）：文本向量模型 v2 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'text-embedding-v2',
  'CNY',
  0.0007,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.7, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 向量化（Embedding）：文本向量模型 v1 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'text-embedding-v1',
  'CNY',
  0.0007,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.7, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 向量化（Embedding）：异步文本向量模型 v2 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'text-embedding-async-v2',
  'CNY',
  0.0007,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.7, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 向量化（Embedding）：异步文本向量模型 v1 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'text-embedding-async-v1',
  'CNY',
  0.0007,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.7, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 重排（Rerank）：Qwen3 重排模型 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-rerank',
  'CNY',
  0.0005,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.5, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- 重排（Rerank）：GTE 重排模型 v2 /默认单价（每 100 万 token，输出成本为 0）
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'gte-rerank-v2',
  'CNY',
  0.0008,
  0,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'FLAT', 'unit', 'PER_1M', 'defaultInputCostPerUnit', 0.8, 'defaultOutputCostPerUnit', 0))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder Plus：代码模型增强版 /分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-plus',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 4, 'outputCostPerUnit', 16),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 6, 'outputCostPerUnit', 24),
    JSON_OBJECT('upToTokens', 256000, 'inputCostPerUnit', 10, 'outputCostPerUnit', 40),
    JSON_OBJECT('upToTokens', 1000000, 'inputCostPerUnit', 20, 'outputCostPerUnit', 200)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder Plus：代码模型增强版（2025-09-23 定价版本）/分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-plus-2025-09-23',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 4, 'outputCostPerUnit', 16),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 6, 'outputCostPerUnit', 24),
    JSON_OBJECT('upToTokens', 256000, 'inputCostPerUnit', 10, 'outputCostPerUnit', 40),
    JSON_OBJECT('upToTokens', 1000000, 'inputCostPerUnit', 20, 'outputCostPerUnit', 200)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder Plus：代码模型增强版（2025-07-22 定价版本）/分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-plus-2025-07-22',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 4, 'outputCostPerUnit', 16),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 6, 'outputCostPerUnit', 24),
    JSON_OBJECT('upToTokens', 256000, 'inputCostPerUnit', 10, 'outputCostPerUnit', 40),
    JSON_OBJECT('upToTokens', 1000000, 'inputCostPerUnit', 20, 'outputCostPerUnit', 200)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder Flash：代码模型轻量版 /分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-flash',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 1, 'outputCostPerUnit', 4),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 1.5, 'outputCostPerUnit', 6),
    JSON_OBJECT('upToTokens', 256000, 'inputCostPerUnit', 2.5, 'outputCostPerUnit', 10),
    JSON_OBJECT('upToTokens', 1000000, 'inputCostPerUnit', 5, 'outputCostPerUnit', 25)
  )))
) ON DUPLICATE KEY UPDATE name = name;

-- Qwen3 Coder Flash：代码模型轻量版（2025-07-28 定价版本）/分段阶梯计价
INSERT INTO llm_price_configs (name, currency, input_cost_per_1k, output_cost_per_1k, metadata)
VALUES (
  'qwen3-coder-flash-2025-07-28',
  'CNY',
  NULL,
  NULL,
  JSON_OBJECT('pricing', JSON_OBJECT('strategy', 'TIERED', 'unit', 'PER_1M', 'tiers', JSON_ARRAY(
    JSON_OBJECT('upToTokens', 32000, 'inputCostPerUnit', 1, 'outputCostPerUnit', 4),
    JSON_OBJECT('upToTokens', 128000, 'inputCostPerUnit', 1.5, 'outputCostPerUnit', 6),
    JSON_OBJECT('upToTokens', 256000, 'inputCostPerUnit', 2.5, 'outputCostPerUnit', 10),
    JSON_OBJECT('upToTokens', 1000000, 'inputCostPerUnit', 5, 'outputCostPerUnit', 25)
  )))
) ON DUPLICATE KEY UPDATE name = name;
