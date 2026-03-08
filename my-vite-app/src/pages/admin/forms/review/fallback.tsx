import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CircleHelp } from 'lucide-react';
import type { ModerationPolicyContentType } from '../../../../services/moderationPolicyService';
import { adminGetModerationPolicyConfig, adminUpsertModerationPolicyConfig } from '../../../../services/moderationPolicyService';

type JsonObject = Record<string, unknown>;

const Section: React.FC<React.PropsWithChildren<{ title: string; desc?: string }>> = ({ title, desc, children }) => (
  <div className="bg-white rounded-lg shadow p-4 space-y-4 ring-1 ring-gray-200/60">
    <div>
      <div className="text-lg font-semibold text-gray-900">{title}</div>
      {desc ? <div className="text-sm text-gray-600 mt-1">{desc}</div> : null}
    </div>
    {children}
  </div>
);

const HintIconButton: React.FC<{ hint: string; ariaLabel: string }> = ({ hint, ariaLabel }) => {
  const [open, setOpen] = useState(false);
  const hoverTimerRef = useRef<number | null>(null);
  const rootRef = useRef<HTMLSpanElement | null>(null);
  const tooltipId = React.useId();

  const clearHoverTimer = useCallback(() => {
    if (hoverTimerRef.current != null) {
      window.clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = null;
    }
  }, []);

  const openWithDelay = useCallback(() => {
    clearHoverTimer();
    hoverTimerRef.current = window.setTimeout(() => setOpen(true), 150);
  }, [clearHoverTimer]);

  const closeNow = useCallback(() => {
    clearHoverTimer();
    setOpen(false);
  }, [clearHoverTimer]);

  useEffect(() => {
    if (!open) return;

    const onPointerDown = (e: PointerEvent) => {
      const root = rootRef.current;
      const target = e.target as Node | null;
      if (root && target && root.contains(target)) return;
      setOpen(false);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };

    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('scroll', closeNow, true);
    return () => {
      window.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('scroll', closeNow, true);
    };
  }, [closeNow, open]);

  useEffect(() => () => clearHoverTimer(), [clearHoverTimer]);

  return (
    <span ref={rootRef} className="relative inline-flex" onMouseEnter={openWithDelay} onMouseLeave={closeNow}>
      <button
        type="button"
        className="p-0.5 rounded text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
        aria-label={ariaLabel}
        aria-describedby={open ? tooltipId : undefined}
        onFocus={() => setOpen(true)}
        onBlur={closeNow}
        onClick={() => setOpen((v) => !v)}
      >
        <CircleHelp size={14} />
      </button>
      {open ? (
        <div
          id={tooltipId}
          role="tooltip"
          className="absolute z-50 bottom-full left-1/2 -translate-x-1/2 mb-2 rounded-md bg-gray-900 text-white px-2 py-1.5 text-xs leading-relaxed shadow-lg ring-1 ring-black/10 max-w-sm whitespace-pre-line"
        >
          {hint}
        </div>
      ) : null}
    </span>
  );
};

const FieldLabel: React.FC<{ id?: string; label: string; hint?: string }> = ({ id, label, hint }) => (
  <div className="flex items-center gap-1 mb-1">
    {id ? (
      <label htmlFor={id} className="text-sm font-medium text-gray-700">
        {label}
      </label>
    ) : (
      <span className="text-sm font-medium text-gray-700">{label}</span>
    )}
    {hint ? <HintIconButton hint={hint} ariaLabel={`${label}说明`} /> : null}
  </div>
);

function isObject(v: unknown): v is JsonObject {
  return !!v && typeof v === 'object' && !Array.isArray(v);
}

function deepGet(obj: JsonObject, path: string): unknown {
  const segs = path.split('.').filter(Boolean);
  let cur: unknown = obj;
  for (const s of segs) {
    if (!isObject(cur)) return undefined;
    cur = cur[s];
  }
  return cur;
}

function deepSet(obj: JsonObject, path: string, value: unknown): JsonObject {
  const segs = path.split('.').filter(Boolean);
  if (segs.length === 0) return obj;
  const out: JsonObject = { ...obj };
  let cur: JsonObject = out;
  for (let i = 0; i < segs.length - 1; i++) {
    const k = segs[i]!;
    const next = cur[k];
    cur[k] = isObject(next) ? { ...next } : {};
    cur = cur[k] as JsonObject;
  }
  cur[segs[segs.length - 1]!] = value;
  return out;
}

function deepDelete(obj: JsonObject, path: string): JsonObject {
  const segs = path.split('.').filter(Boolean);
  if (segs.length === 0) return obj;
  const out: JsonObject = { ...obj };
  let cur: JsonObject = out;
  const stack: Array<{ parent: JsonObject; key: string }> = [];
  for (let i = 0; i < segs.length - 1; i++) {
    const k = segs[i]!;
    const next = cur[k];
    if (!isObject(next)) return out;
    const cloned = { ...next };
    cur[k] = cloned;
    stack.push({ parent: cur, key: k });
    cur = cloned;
  }
  delete cur[segs[segs.length - 1]!];
  for (let i = stack.length - 1; i >= 0; i--) {
    const { parent, key } = stack[i]!;
    const v = parent[key];
    if (isObject(v) && Object.keys(v).length === 0) delete parent[key];
  }
  return out;
}

function toNumberOrUndefined(raw: string): number | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  const n = Number(t);
  return Number.isFinite(n) ? n : undefined;
}

const OptionalNumberInput: React.FC<{
  label: string;
  hint?: string;
  value: unknown;
  onChange: (next: number | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
}> = ({ label, hint, value, onChange, placeholder, disabled }) => {
  const s = typeof value === 'number' && Number.isFinite(value) ? String(value) : '';
  const id = React.useId();
  return (
    <div>
      <FieldLabel id={id} label={label} hint={hint} />
      <input
        id={id}
        type="number"
        inputMode="decimal"
        className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
        value={s}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => onChange(toNumberOrUndefined(e.target.value))}
      />
    </div>
  );
};

const OptionalBoolSelect: React.FC<{
  label: string;
  hint?: string;
  value: unknown;
  onChange: (next: boolean | undefined) => void;
  disabled?: boolean;
}> = ({ label, hint, value, onChange, disabled }) => {
  const v = typeof value === 'boolean' ? (value ? 'true' : 'false') : '';
  const id = React.useId();
  return (
    <div>
      <FieldLabel id={id} label={label} hint={hint} />
      <select
        id={id}
        className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
        value={v}
        disabled={disabled}
        onChange={(e) => {
          const t = e.target.value;
          if (!t) {
            onChange(undefined);
            return;
          }
          onChange(t === 'true');
        }}
      >
        <option value="">（不设置）</option>
        <option value="true">是（true）</option>
        <option value="false">否（false）</option>
      </select>
    </div>
  );
};

const OptionalEnumSelect: React.FC<{
  label: string;
  hint?: string;
  value: unknown;
  onChange: (next: string | undefined) => void;
  options: Array<{ value: string; label: string }>;
  disabled?: boolean;
}> = ({ label, hint, value, onChange, options, disabled }) => {
  const v = typeof value === 'string' ? value : '';
  const id = React.useId();
  return (
    <div>
      <FieldLabel id={id} label={label} hint={hint} />
      <select
        id={id}
        className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
        value={v}
        disabled={disabled}
        onChange={(e) => {
          const t = e.target.value;
          onChange(t ? t : undefined);
        }}
      >
        <option value="">（不设置）</option>
        {options.map((op) => (
          <option key={op.value} value={op.value}>
            {op.label}
          </option>
        ))}
      </select>
    </div>
  );
};

const JsonMiniEditor: React.FC<{
  label: string;
  hint?: string;
  value: unknown;
  onChange: (next: JsonObject | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
}> = ({ label, hint, value, onChange, placeholder, disabled }) => {
  const [text, setText] = useState(() => (isObject(value) ? JSON.stringify(value, null, 2) : ''));
  const [err, setErr] = useState<string | null>(null);
  const id = React.useId();

  useEffect(() => {
    setText(isObject(value) ? JSON.stringify(value, null, 2) : '');
    setErr(null);
  }, [value]);

  return (
    <div>
      <FieldLabel id={id} label={label} hint={hint} />
      <textarea
        id={id}
        className="w-full rounded border border-gray-300 bg-white px-3 py-2 font-mono text-xs min-h-[140px] focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
        value={text}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => {
          const t = e.target.value;
          setText(t);
          if (!t.trim()) {
            setErr(null);
            onChange(undefined);
            return;
          }
          try {
            const v = JSON.parse(t) as unknown;
            if (!isObject(v)) throw new Error('必须是 JSON object');
            setErr(null);
            onChange(v);
          } catch (e2) {
            setErr(e2 instanceof Error ? e2.message : String(e2));
          }
        }}
        spellCheck={false}
      />
      {err ? <div className="text-xs text-red-600 mt-1">JSON 错误：{err}</div> : <div className="text-xs text-gray-500 mt-1">留空表示不设置。</div>}
    </div>
  );
};

export const OptionalJsonArrayInput: React.FC<{
  label: string;
  hint?: string;
  value: unknown;
  onChange: (next: string[] | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
}> = ({ label, hint, value, onChange, placeholder, disabled }) => {
  const [text, setText] = useState(() => (Array.isArray(value) ? JSON.stringify(value, null, 2) : ''));
  const [err, setErr] = useState<string | null>(null);
  const id = React.useId();

  useEffect(() => {
    setText(Array.isArray(value) ? JSON.stringify(value, null, 2) : '');
    setErr(null);
  }, [value]);

  return (
    <div>
      <FieldLabel id={id} label={label} hint={hint} />
      <textarea
        id={id}
        className="w-full rounded border border-gray-300 bg-white px-3 py-2 font-mono text-xs min-h-[120px] focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
        value={text}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => {
          const t = e.target.value;
          setText(t);
          if (!t.trim()) {
            setErr(null);
            onChange(undefined);
            return;
          }
          try {
            const v = JSON.parse(t) as unknown;
            if (!Array.isArray(v)) throw new Error('必须是 JSON array');
            const items: string[] = [];
            for (const x of v) {
              if (typeof x !== 'string') throw new Error('数组元素必须是 string');
              items.push(x);
            }
            setErr(null);
            onChange(items);
          } catch (e2) {
            setErr(e2 instanceof Error ? e2.message : String(e2));
          }
        }}
        spellCheck={false}
      />
      {err ? <div className="text-xs text-red-600 mt-1">JSON 错误：{err}</div> : <div className="text-xs text-gray-500 mt-1">留空表示不设置。</div>}
    </div>
  );
};

export default function ModerationFallbackForm() {
  const [contentType, setContentType] = useState<ModerationPolicyContentType>('POST');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const [policyVersion, setPolicyVersion] = useState<string>('');
  const [config, setConfig] = useState<JsonObject>({});
  const [committed, setCommitted] = useState<{ policyVersion: string; config: JsonObject } | null>(null);

  const hasDirty = useMemo(() => {
    if (!committed) return false;
    return policyVersion !== committed.policyVersion || JSON.stringify(config) !== JSON.stringify(committed.config);
  }, [committed, config, policyVersion]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSaved(null);
    try {
      const dto = await adminGetModerationPolicyConfig(contentType);
      const pv = dto.policyVersion || '';
      const cfg = isObject(dto.config) ? dto.config : {};
      setPolicyVersion(pv);
      setConfig(cfg);
      setCommitted({ policyVersion: pv, config: cfg });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [contentType]);

  useEffect(() => {
    void load();
  }, [load]);

  const save = useCallback(async () => {
    setSaving(true);
    setError(null);
    setSaved(null);
    try {
      const pv = policyVersion.trim();
      if (!pv) throw new Error('policyVersion 不能为空');
      const dto = await adminUpsertModerationPolicyConfig({ contentType, policyVersion: pv, config });
      const nextPv = dto.policyVersion || pv;
      const nextCfg = isObject(dto.config) ? dto.config : {};
      setPolicyVersion(nextPv);
      setConfig(nextCfg);
      setCommitted({ policyVersion: nextPv, config: nextCfg });
      setSaved('保存成功');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [config, contentType, policyVersion]);

  const setPath = useCallback((path: string, v: unknown) => {
    setConfig((prev) => {
      if (v === undefined) return deepDelete(prev, path);
      return deepSet(prev, path, v);
    });
    setSaved(null);
  }, []);

  return (
    <div className="space-y-4 max-w-6xl mx-auto">
      <div className="bg-white rounded-lg shadow p-4 ring-1 ring-gray-200/60">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <div className="text-lg font-semibold text-gray-900">审核策略配置（Policy）</div>
              {loading ? <span className="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700">加载中</span> : null}
              {!loading && hasDirty ? <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">未保存</span> : null}
              {!loading && !hasDirty ? <span className="inline-flex items-center rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">已同步</span> : null}
            </div>
            <div className="text-sm text-gray-600 mt-1">按内容类型编辑阈值/升级/路由/分片/举报复审触发/反垃圾等字段（对齐 docs）。</div>
          </div>
          <div className="flex gap-2">
            <button
              className="px-4 py-2 rounded-md border border-gray-300 bg-white text-sm text-gray-800 hover:bg-gray-50 disabled:opacity-60"
              disabled={loading || saving}
              onClick={() => void load()}
            >
              刷新
            </button>
            <button
              className="px-4 py-2 rounded-md bg-blue-600 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
              disabled={saving || loading || !hasDirty}
              onClick={() => void save()}
            >
              {saving ? '保存中…' : '保存'}
            </button>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <div>
            <FieldLabel
              label="内容类型"
              hint={'字段：contentType\n作用：分别维护不同内容载体的策略配置（帖子/评论/个人简介）。\n建议：改动前先切到对应类型确认当前生效配置。'}
            />
            <select
              className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
              value={contentType}
              onChange={(e) => setContentType(e.target.value as ModerationPolicyContentType)}
              disabled={loading || saving}
            >
              <option value="POST">帖子（POST）</option>
              <option value="COMMENT">评论（COMMENT）</option>
              <option value="PROFILE">个人简介（PROFILE）</option>
            </select>
          </div>
          <div>
            <FieldLabel
              label="策略版本号"
              hint={'字段：policyVersion\n作用：配置的版本标识，用于后端识别与追踪。\n建议：采用递增或语义化版本（例如 v1、v1.1）。'}
            />
            <input
              className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
              value={policyVersion}
              onChange={(e) => setPolicyVersion(e.target.value)}
              disabled={loading || saving}
              placeholder="例如：v1"
            />
          </div>
        </div>

        {error ? <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div> : null}
        {saved ? <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{saved}</div> : null}
        <div className="mt-4 text-xs text-gray-500">提示：留空表示不设置；将鼠标悬停在问号上可查看范围、英文字段名与建议值。</div>
      </div>

      <Section title="前置检测（RULE / VEC）" desc="进入 LLM 之前，先做规则命中与相似检测。">
        <div className="rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-3 gap-4">
          <OptionalBoolSelect
            label="启用规则检测"
            hint={'英文字段：precheck.rule.enabled\n类型：boolean\n作用：进入 LLM 前先做规则命中；命中后按动作处理。\n建议：一般开启。'}
            value={deepGet(config, 'precheck.rule.enabled')}
            onChange={(v) => setPath('precheck.rule.enabled', v)}
            disabled={loading || saving}
          />
          <OptionalEnumSelect
            label="规则命中：高风险动作"
            hint={'英文字段：precheck.rule.high_action\n类型：enum(HUMAN/LLM/REJECT)\n作用：当规则判定为高风险时采取的动作。\n建议：默认转人工或直接拒绝（按业务容忍度）。'}
            value={deepGet(config, 'precheck.rule.high_action')}
            onChange={(v) => setPath('precheck.rule.high_action', v)}
            disabled={loading || saving}
            options={[
              { value: 'HUMAN', label: '转人工（HUMAN）' },
              { value: 'LLM', label: '进入 LLM（LLM）' },
              { value: 'REJECT', label: '拒绝（REJECT）' },
            ]}
          />
          <OptionalEnumSelect
            label="规则命中：中风险动作"
            hint={'英文字段：precheck.rule.medium_action\n类型：enum(HUMAN/LLM/REJECT)\n作用：当规则判定为中风险时采取的动作。\n建议：常用进入 LLM 或转人工。'}
            value={deepGet(config, 'precheck.rule.medium_action')}
            onChange={(v) => setPath('precheck.rule.medium_action', v)}
            disabled={loading || saving}
            options={[
              { value: 'LLM', label: '进入 LLM（LLM）' },
              { value: 'HUMAN', label: '转人工（HUMAN）' },
              { value: 'REJECT', label: '拒绝（REJECT）' },
            ]}
          />
          <OptionalEnumSelect
            label="规则命中：低风险动作"
            hint={'英文字段：precheck.rule.low_action\n类型：enum(HUMAN/LLM/REJECT)\n作用：当规则判定为低风险时采取的动作。\n建议：通常进入 LLM（避免过度拦截）。'}
            value={deepGet(config, 'precheck.rule.low_action')}
            onChange={(v) => setPath('precheck.rule.low_action', v)}
            disabled={loading || saving}
            options={[
              { value: 'LLM', label: '进入 LLM（LLM）' },
              { value: 'HUMAN', label: '转人工（HUMAN）' },
              { value: 'REJECT', label: '拒绝（REJECT）' },
            ]}
          />
        </div>

        <div className="mt-4 rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-4 gap-4">
          <OptionalBoolSelect
            label="启用相似检测"
            hint={'英文字段：precheck.vec.enabled\n类型：boolean\n作用：进入 LLM 前做向量相似命中判断。\n建议：有历史违规样本库时开启。'}
            value={deepGet(config, 'precheck.vec.enabled')}
            onChange={(v) => setPath('precheck.vec.enabled', v)}
            disabled={loading || saving}
          />
          <OptionalNumberInput
            label="相似阈值"
            hint={'英文字段：precheck.vec.threshold\n类型：number\n含义：距离≤阈值视为命中（距离越小越相似）。\n建议：结合向量库与召回质量调整（常见 0.1~0.3）。'}
            value={deepGet(config, 'precheck.vec.threshold')}
            onChange={(v) => setPath('precheck.vec.threshold', v)}
            disabled={loading || saving}
            placeholder="例如：0.20（距离≤阈值视为命中）"
          />
          <OptionalEnumSelect
            label="相似命中动作"
            hint={'英文字段：precheck.vec.hit_action\n类型：enum(HUMAN/LLM/REJECT)\n作用：相似检测命中后采取的动作。\n建议：通常转人工或拒绝（取决于命中样本可靠性）。'}
            value={deepGet(config, 'precheck.vec.hit_action')}
            onChange={(v) => setPath('precheck.vec.hit_action', v)}
            disabled={loading || saving}
            options={[
              { value: 'HUMAN', label: '转人工（HUMAN）' },
              { value: 'LLM', label: '进入 LLM（LLM）' },
              { value: 'REJECT', label: '拒绝（REJECT）' },
            ]}
          />
          <OptionalEnumSelect
            label="相似未命中动作"
            hint={'英文字段：precheck.vec.miss_action\n类型：enum(HUMAN/LLM/REJECT)\n作用：相似检测未命中时的动作。\n建议：通常进入 LLM。'}
            value={deepGet(config, 'precheck.vec.miss_action')}
            onChange={(v) => setPath('precheck.vec.miss_action', v)}
            disabled={loading || saving}
            options={[
              { value: 'LLM', label: '进入 LLM（LLM）' },
              { value: 'HUMAN', label: '转人工（HUMAN）' },
              { value: 'REJECT', label: '拒绝（REJECT）' },
            ]}
          />
        </div>
      </Section>

      <Section title="决策阈值（thresholds）" desc="default + by_label + by_review_stage（reported/appeal 覆盖）。">
        <div className="rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-2 gap-4">
          <OptionalNumberInput
            label="放行阈值"
            hint={'英文字段：thresholds.default.T_allow\n类型：number\n范围：0~1\n作用：得分≤放行阈值时直接放行。\n建议：应小于拒绝阈值；可先用 0.2 附近起步。'}
            value={deepGet(config, 'thresholds.default.T_allow')}
            onChange={(v) => setPath('thresholds.default.T_allow', v)}
            disabled={loading || saving}
            placeholder="例如：0.20"
          />
          <OptionalNumberInput
            label="拒绝阈值"
            hint={'英文字段：thresholds.default.T_reject\n类型：number\n范围：0~1\n作用：得分≥拒绝阈值时直接拒绝。\n建议：应大于放行阈值；可先用 0.8 附近起步。'}
            value={deepGet(config, 'thresholds.default.T_reject')}
            onChange={(v) => setPath('thresholds.default.T_reject', v)}
            disabled={loading || saving}
            placeholder="例如：0.80"
          />
        </div>

        <div className="mt-4 rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-4 gap-4">
          <OptionalNumberInput
            label="被举报复审：放行阈值"
            hint={'英文字段：thresholds.by_review_stage.reported.T_allow\n类型：number\n范围：0~1\n作用：被举报复审阶段覆盖默认放行阈值。\n建议：通常更严格（更低）。'}
            value={deepGet(config, 'thresholds.by_review_stage.reported.T_allow')}
            onChange={(v) => setPath('thresholds.by_review_stage.reported.T_allow', v)}
            disabled={loading || saving}
            placeholder="例如：0.15"
          />
          <OptionalNumberInput
            label="被举报复审：拒绝阈值"
            hint={'英文字段：thresholds.by_review_stage.reported.T_reject\n类型：number\n范围：0~1\n作用：被举报复审阶段覆盖默认拒绝阈值。\n建议：通常更严格（更低或更高取决于得分定义）。'}
            value={deepGet(config, 'thresholds.by_review_stage.reported.T_reject')}
            onChange={(v) => setPath('thresholds.by_review_stage.reported.T_reject', v)}
            disabled={loading || saving}
            placeholder="例如：0.75"
          />
          <OptionalNumberInput
            label="申诉复审：放行阈值"
            hint={'英文字段：thresholds.by_review_stage.appeal.T_allow\n类型：number\n范围：0~1\n作用：申诉复审阶段覆盖默认放行阈值。\n建议：可略宽松（更高/更低取决于得分定义）。'}
            value={deepGet(config, 'thresholds.by_review_stage.appeal.T_allow')}
            onChange={(v) => setPath('thresholds.by_review_stage.appeal.T_allow', v)}
            disabled={loading || saving}
            placeholder="例如：0.25"
          />
          <OptionalNumberInput
            label="申诉复审：拒绝阈值"
            hint={'英文字段：thresholds.by_review_stage.appeal.T_reject\n类型：number\n范围：0~1\n作用：申诉复审阶段覆盖默认拒绝阈值。\n建议：结合申诉复核策略与人工容量调整。'}
            value={deepGet(config, 'thresholds.by_review_stage.appeal.T_reject')}
            onChange={(v) => setPath('thresholds.by_review_stage.appeal.T_reject', v)}
            disabled={loading || saving}
            placeholder="例如：0.85"
          />
        </div>

        <JsonMiniEditor
          label="按类目覆盖阈值"
          hint={'英文字段：thresholds.by_label\n类型：JSON object\n作用：按类目覆盖阈值（优先级高于 default）。\n格式示例：{"涉政":{"T_reject":0.5}}\n留空：不设置。'}
          value={deepGet(config, 'thresholds.by_label')}
          onChange={(v) => setPath('thresholds.by_label', v)}
          disabled={loading || saving}
          placeholder='例如: {"涉政":{"T_reject":0.5}}'
        />
      </Section>

      <Section title="升级规则（escalate_rules）" desc="证据硬约束触发升级。">
        <div className="rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-1 gap-4">
          <OptionalBoolSelect
            label="拒绝必须可验证证据"
            hint={'英文字段：escalate_rules.require_evidence\n类型：boolean\n作用：当需要拒绝时，若证据不足则升级处理。\n建议：开启可减少“无证据拒绝”。'}
            value={deepGet(config, 'escalate_rules.require_evidence')}
            onChange={(v) => setPath('escalate_rules.require_evidence', v)}
            disabled={loading || saving}
          />
        </div>
      </Section>

      <Section title="举报触发复审（review_trigger）" desc="窗口与 light/standard/urgent 阈值。">
        <div className="rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-3 gap-4">
          <OptionalNumberInput
            label="聚合窗口（分钟）"
            hint={'英文字段：review_trigger.window_minutes\n类型：number\n作用：举报聚合统计窗口长度。\n建议：5~30 分钟。'}
            value={deepGet(config, 'review_trigger.window_minutes')}
            onChange={(v) => setPath('review_trigger.window_minutes', v)}
            disabled={loading || saving}
            placeholder="例如：10"
          />
          <OptionalNumberInput
            label="轻量：唯一举报者阈值"
            hint={'英文字段：review_trigger.light.unique_reporters_min\n类型：number\n作用：达到该唯一举报者数触发“轻量复审”。\n建议：3~6。'}
            value={deepGet(config, 'review_trigger.light.unique_reporters_min')}
            onChange={(v) => setPath('review_trigger.light.unique_reporters_min', v)}
            disabled={loading || saving}
            placeholder="例如：3"
          />
          <OptionalNumberInput
            label="轻量：举报总数阈值"
            hint={'英文字段：review_trigger.light.total_reports_min\n类型：number\n作用：达到该举报总数触发“轻量复审”。\n建议：5~10。'}
            value={deepGet(config, 'review_trigger.light.total_reports_min')}
            onChange={(v) => setPath('review_trigger.light.total_reports_min', v)}
            disabled={loading || saving}
            placeholder="例如：5"
          />
        </div>
        <div className="mt-4 rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-4 gap-4">
          <OptionalNumberInput
            label="标准：唯一举报者阈值"
            hint={'英文字段：review_trigger.standard.unique_reporters_min\n类型：number\n作用：达到该唯一举报者数触发“标准复审”。\n建议：5~12。'}
            value={deepGet(config, 'review_trigger.standard.unique_reporters_min')}
            onChange={(v) => setPath('review_trigger.standard.unique_reporters_min', v)}
            disabled={loading || saving}
            placeholder="例如：5"
          />
          <OptionalNumberInput
            label="标准：举报总数阈值"
            hint={'英文字段：review_trigger.standard.total_reports_min\n类型：number\n作用：达到该举报总数触发“标准复审”。\n建议：10~20。'}
            value={deepGet(config, 'review_trigger.standard.total_reports_min')}
            onChange={(v) => setPath('review_trigger.standard.total_reports_min', v)}
            disabled={loading || saving}
            placeholder="例如：10"
          />
          <OptionalNumberInput
            label="标准：窗口内速度阈值"
            hint={'英文字段：review_trigger.standard.velocity_min_per_window\n类型：number\n作用：窗口内举报速度达到阈值触发“标准复审”。\n建议：结合窗口长度与流量设置。'}
            value={deepGet(config, 'review_trigger.standard.velocity_min_per_window')}
            onChange={(v) => setPath('review_trigger.standard.velocity_min_per_window', v)}
            disabled={loading || saving}
            placeholder="例如：5"
          />
          <OptionalNumberInput
            label="标准：举报可信度聚合阈值"
            hint={'英文字段：review_trigger.standard.trust_min\n类型：number\n范围：0~1\n作用：举报可信度聚合达到阈值触发“标准复审”。\n建议：0.6~0.85。'}
            value={deepGet(config, 'review_trigger.standard.trust_min')}
            onChange={(v) => setPath('review_trigger.standard.trust_min', v)}
            disabled={loading || saving}
            placeholder="例如：0.7"
          />
        </div>
        <div className="mt-4 rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-4 gap-4">
          <OptionalNumberInput
            label="紧急：唯一举报者阈值"
            hint={'英文字段：review_trigger.urgent.unique_reporters_min\n类型：number\n作用：达到该唯一举报者数触发“紧急复审”。\n建议：根据风险容忍度设置（常见 15~30）。'}
            value={deepGet(config, 'review_trigger.urgent.unique_reporters_min')}
            onChange={(v) => setPath('review_trigger.urgent.unique_reporters_min', v)}
            disabled={loading || saving}
            placeholder="例如：20"
          />
          <OptionalNumberInput
            label="紧急：举报总数阈值"
            hint={'英文字段：review_trigger.urgent.total_reports_min\n类型：number\n作用：达到该举报总数触发“紧急复审”。\n建议：与唯一举报者阈值配合。'}
            value={deepGet(config, 'review_trigger.urgent.total_reports_min')}
            onChange={(v) => setPath('review_trigger.urgent.total_reports_min', v)}
            disabled={loading || saving}
            placeholder="例如：20"
          />
          <OptionalNumberInput
            label="紧急：窗口内速度阈值"
            hint={'英文字段：review_trigger.urgent.velocity_min_per_window\n类型：number\n作用：窗口内举报速度达到阈值触发“紧急复审”。\n建议：用于突发爆发场景。'}
            value={deepGet(config, 'review_trigger.urgent.velocity_min_per_window')}
            onChange={(v) => setPath('review_trigger.urgent.velocity_min_per_window', v)}
            disabled={loading || saving}
            placeholder="例如：20"
          />
          <OptionalNumberInput
            label="紧急：举报可信度聚合阈值"
            hint={'英文字段：review_trigger.urgent.trust_min\n类型：number\n范围：0~1\n作用：可信度聚合达到阈值触发“紧急复审”。\n建议：0.75~0.95。'}
            value={deepGet(config, 'review_trigger.urgent.trust_min')}
            onChange={(v) => setPath('review_trigger.urgent.trust_min', v)}
            disabled={loading || saving}
            placeholder="例如：0.85"
          />
        </div>
      </Section>

      <Section title="反垃圾/节流（anti_spam）" desc="仅用于节流/入人工等控制，不直接等价于违规判定。">
        <div className="rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-4 gap-4">
          <OptionalNumberInput
            label="评论：聚合窗口（秒）"
            hint={'英文字段：anti_spam.comment.window_seconds\n类型：number\n作用：评论反垃圾统计窗口（秒）。\n建议：30~120。'}
            value={deepGet(config, 'anti_spam.comment.window_seconds')}
            onChange={(v) => setPath('anti_spam.comment.window_seconds', v)}
            disabled={loading || saving}
            placeholder="例如：60"
          />
          <OptionalNumberInput
            label="评论：单用户短窗上限"
            hint={'英文字段：anti_spam.comment.max_per_author_per_window\n类型：number\n作用：同一用户在窗口内允许的评论次数上限。\n建议：结合社区节奏设置（例如 6~12）。'}
            value={deepGet(config, 'anti_spam.comment.max_per_author_per_window')}
            onChange={(v) => setPath('anti_spam.comment.max_per_author_per_window', v)}
            disabled={loading || saving}
            placeholder="例如：8"
          />
          <OptionalNumberInput
            label="评论：相似度阈值"
            hint={'英文字段：anti_spam.comment.similarity_threshold\n类型：number\n范围：0~1\n作用：评论相似度高于阈值时认为重复/刷屏。\n建议：0.85~0.95。'}
            value={deepGet(config, 'anti_spam.comment.similarity_threshold')}
            onChange={(v) => setPath('anti_spam.comment.similarity_threshold', v)}
            disabled={loading || saving}
            placeholder="例如：0.9"
          />
          <OptionalNumberInput
            label="评论：10 分钟相似次数上限"
            hint={'英文字段：anti_spam.comment.max_similar_count_per_10min\n类型：number\n作用：10 分钟内相似评论出现次数超过阈值则节流/升级。\n建议：2~5。'}
            value={deepGet(config, 'anti_spam.comment.max_similar_count_per_10min')}
            onChange={(v) => setPath('anti_spam.comment.max_similar_count_per_10min', v)}
            disabled={loading || saving}
            placeholder="例如：3"
          />
        </div>
        <div className="mt-4 rounded-lg border border-gray-100 bg-gray-50/50 p-4 grid grid-cols-1 md:grid-cols-4 gap-4">
          <OptionalNumberInput
            label="资料：聚合窗口（分钟）"
            hint={'英文字段：anti_spam.profile.window_minutes\n类型：number\n作用：资料更新反垃圾统计窗口（分钟）。\n建议：30~120。'}
            value={deepGet(config, 'anti_spam.profile.window_minutes')}
            onChange={(v) => setPath('anti_spam.profile.window_minutes', v)}
            disabled={loading || saving}
            placeholder="例如：60"
          />
          <OptionalNumberInput
            label="资料：窗口内更新次数上限"
            hint={'英文字段：anti_spam.profile.max_updates_per_window\n类型：number\n作用：窗口内资料更新次数超过阈值则节流。\n建议：2~5。'}
            value={deepGet(config, 'anti_spam.profile.max_updates_per_window')}
            onChange={(v) => setPath('anti_spam.profile.max_updates_per_window', v)}
            disabled={loading || saving}
            placeholder="例如：3"
          />
          <OptionalNumberInput
            label="资料：日更新次数上限"
            hint={'英文字段：anti_spam.profile.max_updates_per_day\n类型：number\n作用：单日资料更新次数超过阈值则节流。\n建议：3~10。'}
            value={deepGet(config, 'anti_spam.profile.max_updates_per_day')}
            onChange={(v) => setPath('anti_spam.profile.max_updates_per_day', v)}
            disabled={loading || saving}
            placeholder="例如：5"
          />
          <OptionalNumberInput
            label="资料：相似度阈值"
            hint={'英文字段：anti_spam.profile.similarity_threshold\n类型：number\n范围：0~1\n作用：资料更新内容相似度高于阈值时认为刷屏/重复。\n建议：0.85~0.95。'}
            value={deepGet(config, 'anti_spam.profile.similarity_threshold')}
            onChange={(v) => setPath('anti_spam.profile.similarity_threshold', v)}
            disabled={loading || saving}
            placeholder="例如：0.9"
          />
        </div>
      </Section>

      <Section title="原始 config（只读预览）" desc="用于核对最终写入后端的 JSON。">
        <pre className="rounded border bg-gray-50 p-3 text-xs overflow-auto max-h-[420px]">{JSON.stringify(config, null, 2)}</pre>
      </Section>
    </div>
  );
}
