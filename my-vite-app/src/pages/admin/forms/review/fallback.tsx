// fallback.tsx

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import type { ModerationConfidenceFallbackConfig, FallbackAction } from '../../../../services/moderationFallbackService';
import { getFallbackConfig, updateFallbackConfig } from '../../../../services/moderationFallbackService';

function clamp(n: number, min: number, max: number) {
  if (!Number.isFinite(n)) return min;
  return Math.min(Math.max(n, min), max);
}

const actionOptions: Array<{ value: FallbackAction; label: string }> = [
  { value: 'REJECT', label: '直接拒绝' },
  { value: 'LLM', label: '进入 LLM' },
  { value: 'HUMAN', label: '转人工' },
];

const Section: React.FC<React.PropsWithChildren<{ title: string; desc?: string }>> = ({ title, desc, children }) => (
  <div className="bg-white rounded-lg shadow p-4 space-y-3">
    <div>
      <div className="text-lg font-semibold text-gray-900">{title}</div>
      {desc ? <div className="text-sm text-gray-600 mt-1">{desc}</div> : null}
    </div>
    {children}
  </div>
);

const Label: React.FC<React.PropsWithChildren> = ({ children }) => (
  <div className="text-sm text-gray-700 font-medium">{children}</div>
);

const Select: React.FC<{ value: FallbackAction; onChange: (v: FallbackAction) => void; disabled?: boolean }> = ({
  value,
  onChange,
  disabled,
}) => (
  <select
    className="rounded border px-3 py-2 w-full"
    value={value}
    disabled={disabled}
    onChange={(e) => onChange(e.target.value as FallbackAction)}
  >
    {actionOptions.map((o) => (
      <option key={o.value} value={o.value}>
        {o.label}
      </option>
    ))}
  </select>
);

const Switch: React.FC<{ checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }> = ({
  checked,
  onChange,
  label,
  disabled,
}) => (
  <label className="inline-flex items-center gap-2 text-sm text-gray-700">
    <input
      type="checkbox"
      className="h-4 w-4"
      checked={checked}
      disabled={disabled}
      onChange={(e) => onChange(e.target.checked)}
    />
    <span>{label}</span>
  </label>
);

const FallbackForm: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  const [cfg, setCfg] = useState<ModerationConfidenceFallbackConfig | null>(null);
  const [committedCfg, setCommittedCfg] = useState<ModerationConfidenceFallbackConfig | null>(null);
  const [editing, setEditing] = useState(false);
  const hasUnsavedChanges = useMemo(() => JSON.stringify(cfg) !== JSON.stringify(committedCfg), [cfg, committedCfg]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setOk(null);
    try {
      const data = await getFallbackConfig();
      setCfg(data);
      setCommittedCfg(data);
      setEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const canSave = useMemo(() => !!cfg && !saving && editing && hasUnsavedChanges, [cfg, editing, hasUnsavedChanges, saving]);

  const save = useCallback(async () => {
    if (!cfg) return;
    if (!editing) return;

    setSaving(true);
    setError(null);
    setOk(null);

    try {
      const payload: Partial<ModerationConfidenceFallbackConfig> = {
        ruleEnabled: cfg.ruleEnabled,
        ruleHighAction: cfg.ruleHighAction,
        ruleMediumAction: cfg.ruleMediumAction,
        ruleLowAction: cfg.ruleLowAction,

        vecEnabled: cfg.vecEnabled,
        vecThreshold: clamp(cfg.vecThreshold, 0, 2),
        vecHitAction: cfg.vecHitAction,
        vecMissAction: cfg.vecMissAction,

        llmEnabled: cfg.llmEnabled,
        llmRejectThreshold: clamp(cfg.llmRejectThreshold, 0, 1),
        llmHumanThreshold: clamp(cfg.llmHumanThreshold, 0, 1),
        llmTextRiskThreshold: clamp(cfg.llmTextRiskThreshold, 0, 1),
        llmImageRiskThreshold: clamp(cfg.llmImageRiskThreshold, 0, 1),
        llmStrongRejectThreshold: clamp(cfg.llmStrongRejectThreshold, 0, 1),
        llmStrongPassThreshold: clamp(cfg.llmStrongPassThreshold, 0, 1),
        llmCrossModalThreshold: clamp(cfg.llmCrossModalThreshold, 0, 1),

        reportHumanThreshold: clamp(cfg.reportHumanThreshold, 1, 1000000),
      };

      if (payload.llmHumanThreshold! > payload.llmRejectThreshold!) {
        payload.llmHumanThreshold = payload.llmRejectThreshold;
      }
      if (payload.llmStrongPassThreshold! > payload.llmStrongRejectThreshold!) {
        payload.llmStrongPassThreshold = payload.llmStrongRejectThreshold;
      }

      const saved = await updateFallbackConfig(payload);
      setCfg(saved);
      setCommittedCfg(saved);
      setEditing(false);
      setOk('已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }, [cfg, editing]);

  if (loading || !cfg) {
    return (
      <div className="bg-white rounded-lg shadow p-4 text-sm text-gray-600">
        {loading ? '正在加载…' : '无数据'}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 flex items-center justify-between gap-3">
        <div>
          <div className="text-lg font-semibold">置信回退机制</div>
          <div className="text-sm text-gray-600 mt-1">
            用来配置“系统不确定时怎么处理”。审核通常按顺序经过：规则过滤（RULE）→ 向量相似检测（VEC）→ LLM 复核（LLM）→ 人工。
            当某一层命中条件/达到阈值时，就按这里设定的动作执行：直接拒绝 / 继续进入 LLM / 转人工。
          </div>
          <div className="text-xs text-gray-500 mt-2">
            更新时间：{cfg.updatedAt ?? '—'}{cfg.updatedBy ? ` · 更新人：${cfg.updatedBy}` : ''}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => void load()}
            className="rounded border px-3 py-2 text-sm"
            disabled={loading || saving}
          >
            刷新
          </button>
          {!editing ? (
            <button
              type="button"
              onClick={() => {
                setEditing(true);
                setError(null);
                setOk(null);
              }}
              className="rounded border px-3 py-2 text-sm disabled:opacity-60"
              disabled={loading || saving}
            >
              编辑
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  setCfg(committedCfg);
                  setEditing(false);
                  setError(null);
                  setOk(null);
                }}
                className="rounded border px-3 py-2 text-sm disabled:opacity-60"
                disabled={loading || saving}
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void save()}
                className="rounded bg-blue-600 text-white px-4 py-2 text-sm disabled:opacity-60"
                disabled={!canSave}
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </>
          )}
        </div>
      </div>

      {error ? <div className="bg-red-50 border border-red-200 text-red-700 rounded p-3 text-sm">{error}</div> : null}
      {ok ? <div className="bg-green-50 border border-green-200 text-green-700 rounded p-3 text-sm">{ok}</div> : null}

      <Section
        title="规则过滤层 (RULE)配置"
        desc="规则过滤=明确的“黑名单/敏感词/URL/广告模式”等规则。只要命中规则，就会立刻触发；并按该规则的严重级别（高/中/低）决定下一步怎么走。"
      >
        <div className="flex items-center justify-between">
          <Switch
            checked={cfg.ruleEnabled}
            onChange={(v) => setCfg((prev) => (prev ? { ...prev, ruleEnabled: v } : prev))}
            label="启用规则过滤层"
            disabled={!editing}
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div>
            <Label>高风险命中动作</Label>
            <Select
              value={cfg.ruleHighAction}
              disabled={!editing}
              onChange={(v) => setCfg((p) => (p ? { ...p, ruleHighAction: v } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">当命中 HIGH 级规则时执行的处理方式（通常用于明确违规：如黑名单命中）。</div>
          </div>
          <div>
            <Label>中风险命中动作</Label>
            <Select
              value={cfg.ruleMediumAction}
              disabled={!editing}
              onChange={(v) => setCfg((p) => (p ? { ...p, ruleMediumAction: v } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">当命中 MEDIUM 级规则时执行的处理方式（可用来“先交给 LLM 再判断”）。</div>
          </div>
          <div>
            <Label>低风险命中动作</Label>
            <Select
              value={cfg.ruleLowAction}
              disabled={!editing}
              onChange={(v) => setCfg((p) => (p ? { ...p, ruleLowAction: v } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">当命中 LOW 级规则时执行的处理方式（常用于疑似/弱信号命中）。</div>
          </div>
        </div>
      </Section>

      <Section
        title="嵌入相似检测 (VEC)配置"
        desc="向量相似检测=把内容“转换成向量”，与已知样本做相似度比较。距离越小越像；当 距离 ≤ 阈值 时视为命中。阈值越大越容易命中（更严格），阈值越小越不容易命中（更宽松）。"
      >
        <div className="flex items-center justify-between">
          <Switch
            checked={cfg.vecEnabled}
            onChange={(v) => setCfg((prev) => (prev ? { ...prev, vecEnabled: v } : prev))}
            label="启用嵌入相似检测"
            disabled={!editing}
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <Label>命中阈值 (vecThreshold)</Label>
            <input
              type="number"
              step="0.0001"
              className="rounded border px-3 py-2 w-full"
              value={cfg.vecThreshold}
              disabled={!editing}
              onChange={(e) => setCfg((p) => (p ? { ...p, vecThreshold: Number(e.target.value) } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">
              只要距离 ≤ 该值就算“相似命中”。建议范围 0.05 ~ 0.4（根据向量模型与样本密度调整）。
            </div>
          </div>
          <div className="grid grid-cols-1 gap-3">
            <div>
              <Label>命中动作</Label>
              <Select
                value={cfg.vecHitAction}
                disabled={!editing}
                onChange={(v) => setCfg((p) => (p ? { ...p, vecHitAction: v } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">当内容与样本“足够相似”时的处理方式。</div>
            </div>
            <div>
              <Label>未命中动作</Label>
              <Select
                value={cfg.vecMissAction}
                disabled={!editing}
                onChange={(v) => setCfg((p) => (p ? { ...p, vecMissAction: v } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">当内容与样本“不够相似”时的处理方式（通常是继续进入 LLM）。</div>
            </div>
          </div>
        </div>
      </Section>

      <Section
        title="LLM 审核层配置"
        desc="LLM 会给出一个 0~1 的风险分：越接近 1 越像违规。系统根据阈值把结果分成三段：≥ 拒绝阈值 直接拒绝；介于转人工阈值与拒绝阈值 进入人工；低于转人工阈值 通过。阈值越低越严格（更容易拒绝/转人工）。"
      >
        <div className="flex items-center justify-between">
          <Switch
            checked={cfg.llmEnabled}
            onChange={(v) => setCfg((prev) => (prev ? { ...prev, llmEnabled: v } : prev))}
            label="启用 LLM 审核"
            disabled={!editing}
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <Label>拒绝阈值 (llmRejectThreshold)</Label>
            <input
              type="number"
              step="0.01"
              min={0}
              max={1}
              className="rounded border px-3 py-2 w-full"
              value={cfg.llmRejectThreshold}
              disabled={!editing}
              onChange={(e) => setCfg((p) => (p ? { ...p, llmRejectThreshold: Number(e.target.value) } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">风险分 ≥ 该值：直接拒绝。值越低，拒绝越多；值越高，拒绝越少。</div>
          </div>
          <div>
            <Label>转人工阈值 (llmHumanThreshold)</Label>
            <input
              type="number"
              step="0.01"
              min={0}
              max={1}
              className="rounded border px-3 py-2 w-full"
              value={cfg.llmHumanThreshold}
              disabled={!editing}
              onChange={(e) => setCfg((p) => (p ? { ...p, llmHumanThreshold: Number(e.target.value) } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">
              风险分 &lt; 该值：直接通过；介于该值与拒绝阈值之间：转人工。会自动保证转人工阈值 ≤ 拒绝阈值。
            </div>
          </div>
        </div>

        <div className="mt-3 border-t pt-3">
          <div className="text-sm font-semibold text-gray-900">图文审核阈值（文本/图片拆分 + 跨模态复核）</div>
          <div className="text-xs text-gray-600 mt-1">
            当待审内容包含图片时：先分别进行文本审核与图片审核（图片会生成描述），再在必要时进行跨模态复核。
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
            <div>
              <Label>文本风险阈值 (llmTextRiskThreshold)</Label>
              <input
                type="number"
                step="0.01"
                min={0}
                max={1}
                className="rounded border px-3 py-2 w-full"
                value={cfg.llmTextRiskThreshold}
                disabled={!editing}
                onChange={(e) => setCfg((p) => (p ? { ...p, llmTextRiskThreshold: Number(e.target.value) } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">
                阈值1：文本初审风险 ≥ 该值视为“可疑/偏高风险”，会进入后续的短路判断/跨模态复核逻辑。
              </div>
            </div>
            <div>
              <Label>图片风险阈值 (llmImageRiskThreshold)</Label>
              <input
                type="number"
                step="0.01"
                min={0}
                max={1}
                className="rounded border px-3 py-2 w-full"
                value={cfg.llmImageRiskThreshold}
                disabled={!editing}
                onChange={(e) => setCfg((p) => (p ? { ...p, llmImageRiskThreshold: Number(e.target.value) } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">阈值2：图片初审风险 ≥ 该值视为“可疑/偏高风险”。</div>
            </div>
            <div>
              <Label>强拒绝阈值 (llmStrongRejectThreshold)</Label>
              <input
                type="number"
                step="0.01"
                min={0}
                max={1}
                className="rounded border px-3 py-2 w-full"
                value={cfg.llmStrongRejectThreshold}
                disabled={!editing}
                onChange={(e) => setCfg((p) => (p ? { ...p, llmStrongRejectThreshold: Number(e.target.value) } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">阈值3：只要文本或图片任一风险 ≥ 该值，直接拒绝（无需再复核）。</div>
            </div>
            <div>
              <Label>强通过阈值 (llmStrongPassThreshold)</Label>
              <input
                type="number"
                step="0.01"
                min={0}
                max={1}
                className="rounded border px-3 py-2 w-full"
                value={cfg.llmStrongPassThreshold}
                disabled={!editing}
                onChange={(e) => setCfg((p) => (p ? { ...p, llmStrongPassThreshold: Number(e.target.value) } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">阈值4：当文本与图片风险都 &lt; 该值时，直接通过（无需再复核）。</div>
            </div>
            <div className="md:col-span-2">
              <Label>跨模态综合阈值 (llmCrossModalThreshold)</Label>
              <input
                type="number"
                step="0.01"
                min={0}
                max={1}
                className="rounded border px-3 py-2 w-full"
                value={cfg.llmCrossModalThreshold}
                disabled={!editing}
                onChange={(e) => setCfg((p) => (p ? { ...p, llmCrossModalThreshold: Number(e.target.value) } : p))}
              />
              <div className="text-xs text-gray-500 mt-1">
                阈值5：跨模态复核的综合风险 ≥ 该值则拒绝；否则通过（用于“文本与图片组合起来才有问题”的场景）。
              </div>
            </div>
          </div>
          <div className="text-xs text-gray-500 mt-2">会自动保证强通过阈值 ≤ 强拒绝阈值</div>
        </div>
      </Section>

      <Section
        title="举报转人工"
        desc="当同一目标（同一帖子/评论/用户等）累计被举报次数达到阈值后，直接进入人工审核；避免被反复刷进自动队列。"
      >
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <Label>转人工阈值 (reportHumanThreshold)</Label>
            <input
              type="number"
              step="1"
              min={1}
              className="rounded border px-3 py-2 w-full"
              value={cfg.reportHumanThreshold}
              disabled={!editing}
              onChange={(e) => setCfg((p) => (p ? { ...p, reportHumanThreshold: Number(e.target.value) } : p))}
            />
            <div className="text-xs text-gray-500 mt-1">例如填 5 表示：同一目标累计 5 次举报就直接转人工。默认 5，可按社区规模调整。</div>
          </div>
        </div>
      </Section>
    </div>
  );
};

export default FallbackForm;
