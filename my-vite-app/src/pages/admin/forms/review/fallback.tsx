// fallback.tsx

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import type { ModerationConfidenceFallbackConfig, FallbackAction } from '../../../../services/moderationFallbackService';
import { getFallbackConfig, updateFallbackConfig } from '../../../../services/moderationFallbackService';

function clamp(n: number, min: number, max: number) {
  if (Number.isNaN(n)) return min;
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

        reportHumanThreshold: clamp(cfg.reportHumanThreshold, 1, 1000000),
      };

      if (payload.llmHumanThreshold! > payload.llmRejectThreshold!) {
        payload.llmHumanThreshold = payload.llmRejectThreshold;
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
            统一控制 RULE / VEC / LLM 三层在达到阈值或命中条件后：直接拒绝 / 进入 LLM / 转人工。
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
        title="规则过滤层 (RULE)"
        desc="当规则命中时，根据命中规则的严重级别选择动作。"
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
          </div>
          <div>
            <Label>中风险命中动作</Label>
            <Select
              value={cfg.ruleMediumAction}
              disabled={!editing}
              onChange={(v) => setCfg((p) => (p ? { ...p, ruleMediumAction: v } : p))}
            />
          </div>
          <div>
            <Label>低风险命中动作</Label>
            <Select
              value={cfg.ruleLowAction}
              disabled={!editing}
              onChange={(v) => setCfg((p) => (p ? { ...p, ruleLowAction: v } : p))}
            />
          </div>
        </div>
      </Section>

      <Section
        title="嵌入相似检测 (VEC)"
        desc="距离越小越相似。距离 ≤ 阈值视为命中。"
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
            <div className="text-xs text-gray-500 mt-1">建议范围 0.05 ~ 0.4（根据向量模型与样本密度调整）</div>
          </div>
          <div className="grid grid-cols-1 gap-3">
            <div>
              <Label>命中动作</Label>
              <Select
                value={cfg.vecHitAction}
                disabled={!editing}
                onChange={(v) => setCfg((p) => (p ? { ...p, vecHitAction: v } : p))}
              />
            </div>
            <div>
              <Label>未命中动作</Label>
              <Select
                value={cfg.vecMissAction}
                disabled={!editing}
                onChange={(v) => setCfg((p) => (p ? { ...p, vecMissAction: v } : p))}
              />
            </div>
          </div>
        </div>
      </Section>

      <Section
        title="LLM 审核层"
        desc="LLM 输出风险分(0~1)。风险分 ≥ 拒绝阈值 -> 直接拒绝；介于转人工阈值与拒绝阈值 -> 转人工；低于转人工阈值 -> 通过。"
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
            <div className="text-xs text-gray-500 mt-1">会自动保证转人工阈值 ≤ 拒绝阈值</div>
          </div>
        </div>
      </Section>

      <Section
        title="举报转人工"
        desc="当同一目标累计举报次数达到阈值后，直接进入人工审核（不再进入自动审核队列）。"
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
            <div className="text-xs text-gray-500 mt-1">默认 5，可根据社区规模与滥用情况调整</div>
          </div>
        </div>
      </Section>
    </div>
  );
};

export default FallbackForm;
