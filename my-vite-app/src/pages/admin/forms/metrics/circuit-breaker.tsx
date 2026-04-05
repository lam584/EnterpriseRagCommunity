import { useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import {
  adminGetContentSafetyCircuitBreakerStatus,
  adminUpdateContentSafetyCircuitBreakerConfig,
  type ContentSafetyCircuitBreakerAutoTriggerDTO,
  type ContentSafetyCircuitBreakerConfigDTO,
  type ContentSafetyCircuitBreakerDependencyIsolationDTO,
  type ContentSafetyCircuitBreakerEventDTO,
  type ContentSafetyCircuitBreakerScopeDTO,
  type ContentSafetyCircuitBreakerStatusDTO,
} from '../../../../services/contentSafetyCircuitBreakerAdminService';
import {
  adminGetDependencyCircuitBreakerConfig,
  adminUpdateDependencyCircuitBreakerConfig,
} from '../../../../services/dependencyCircuitBreakerAdminService';
import { clampMetricInt } from './metricsTimeUtils';

type LoadState = 'idle' | 'loading' | 'saving';

type CommittedState = {
  enabled: boolean;
  mode: 'S1' | 'S2' | 'S3';
  message: string;
  scopeAll: boolean;
  userIdsRaw: string;
  postIdsRaw: string;
  entrypoints: string[];
  diMysql: boolean;
  diEs: boolean;
  autoTrigger: ContentSafetyCircuitBreakerAutoTriggerDTO;
  esFailureThreshold: number;
  esCooldownSeconds: number;
};

function readScopeState(scope: {
  all?: unknown;
  userIds?: unknown;
  postIds?: unknown;
  entrypoints?: unknown;
}) {
  return {
    scopeAll: toBool(scope.all, true),
    userIdsRaw: joinIdList(scope.userIds),
    postIdsRaw: joinIdList(scope.postIds),
    entrypoints: (Array.isArray(scope.entrypoints) ? scope.entrypoints : [])
      .map((x: unknown) => String(x ?? '').trim())
      .filter(Boolean),
  };
}

function toBool(v: unknown, def: boolean): boolean {
  if (typeof v === 'boolean') return v;
  return def;
}

function clampNum(v: unknown, min: number, max: number, def: number): number {
  if (typeof v === 'number' && Number.isFinite(v)) return Math.max(min, Math.min(max, v));
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return def;
    const n = Number(t);
    if (!Number.isFinite(n)) return def;
    return Math.max(min, Math.min(max, n));
  }
  return def;
}

function parseIdList(raw: string): number[] {
  const s = String(raw || '').trim();
  if (!s) return [];
  const parts = s.split(/[\s,，;；]+/g).map(x => x.trim()).filter(Boolean);
  const out: number[] = [];
  for (const p of parts) {
    const n = Number(p);
    if (!Number.isFinite(n)) continue;
    const x = Math.trunc(n);
    if (x <= 0) continue;
    out.push(x);
  }
  return Array.from(new Set(out));
}

function joinIdList(ids: unknown): string {
  const arr = Array.isArray(ids)
    ? ids.filter((x): x is number => typeof x === 'number' && Number.isFinite(x) && x > 0)
    : [];
  return arr.join(', ');
}

function formatIso(ts: string | null | undefined): string {
  if (!ts) return '';
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts;
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${mm}-${dd} ${hh}:${mi}:${ss}`;
}

const ENTRYPOINTS: Array<{ id: string; label: string }> = [
  { id: 'PORTAL_POST_LIST', label: '帖子列表' },
  { id: 'PORTAL_POST_DETAIL', label: '帖子详情' },
  { id: 'PORTAL_SEARCH', label: '检索' },
  { id: 'PORTAL_CHAT', label: '聊天' },
  { id: 'UPLOADS_API', label: '上传 API' },
  { id: 'UPLOADS_STATIC', label: '文件预览/下载' },
  { id: 'PORTAL_HOT', label: '热榜' },
  { id: 'ADMIN_MODERATION_QUEUE', label: '审核队列(后台)' },
];

const DEFAULT_AUTO_TRIGGER: ContentSafetyCircuitBreakerAutoTriggerDTO = {
  enabled: false,
  windowSeconds: 60,
  thresholdCount: 10,
  minConfidence: 0.9,
  verdicts: ['REJECT', 'REVIEW'],
  triggerMode: 'S1',
  coolDownSeconds: 300,
  autoRecoverSeconds: 0,
};

function normalizeConfig(s: ContentSafetyCircuitBreakerStatusDTO | null): Required<ContentSafetyCircuitBreakerConfigDTO> {
  const cfg = (s?.config ?? {}) as ContentSafetyCircuitBreakerConfigDTO;
  const scope0 = (cfg.scope ?? {}) as ContentSafetyCircuitBreakerScopeDTO;
  const di0 = (cfg.dependencyIsolation ?? {}) as ContentSafetyCircuitBreakerDependencyIsolationDTO;
  const at0 = (cfg.autoTrigger ?? {}) as ContentSafetyCircuitBreakerAutoTriggerDTO;

  const scope: ContentSafetyCircuitBreakerScopeDTO = {
    all: toBool(scope0.all, true),
    userIds: Array.isArray(scope0.userIds) ? scope0.userIds : [],
    postIds: Array.isArray(scope0.postIds) ? scope0.postIds : [],
    entrypoints: Array.isArray(scope0.entrypoints) ? scope0.entrypoints : [],
  };
  const dependencyIsolation: ContentSafetyCircuitBreakerDependencyIsolationDTO = {
    mysql: toBool(di0.mysql, false),
    elasticsearch: toBool(di0.elasticsearch, false),
  };
  const autoTrigger: ContentSafetyCircuitBreakerAutoTriggerDTO = {
    enabled: toBool(at0.enabled, false),
    windowSeconds: clampMetricInt(at0.windowSeconds, 5, 3600, 60),
    thresholdCount: clampMetricInt(at0.thresholdCount, 1, 1_000_000, 10),
    minConfidence: clampNum(at0.minConfidence, 0, 1, 0.9),
    verdicts: Array.isArray(at0.verdicts) ? at0.verdicts : ['REJECT', 'REVIEW'],
    triggerMode: String(at0.triggerMode || 'S1'),
    coolDownSeconds: clampMetricInt(at0.coolDownSeconds, 0, 86400, 300),
    autoRecoverSeconds: clampMetricInt(at0.autoRecoverSeconds, 0, 7 * 86400, 0),
  };

  return {
    enabled: toBool(cfg.enabled, false),
    mode: String(cfg.mode || 'S1'),
    message: String(cfg.message || '临时不可用：系统正在进行内容安全处置，请稍后再试。'),
    scope,
    dependencyIsolation,
    autoTrigger,
  };
}

function buildConfig(
  enabled: boolean,
  mode: string,
  message: string,
  scopeAll: boolean,
  userIdsRaw: string,
  postIdsRaw: string,
  entrypoints: Set<string>,
  diMysql: boolean,
  diEs: boolean,
  at: ContentSafetyCircuitBreakerAutoTriggerDTO
): ContentSafetyCircuitBreakerConfigDTO {
  const scope: ContentSafetyCircuitBreakerScopeDTO = {
    all: scopeAll,
    userIds: scopeAll ? [] : parseIdList(userIdsRaw),
    postIds: scopeAll ? [] : parseIdList(postIdsRaw),
    entrypoints: scopeAll ? [] : Array.from(entrypoints),
  };
  const dependencyIsolation: ContentSafetyCircuitBreakerDependencyIsolationDTO = {
    mysql: diMysql,
    elasticsearch: diEs,
  };
  const autoTrigger: ContentSafetyCircuitBreakerAutoTriggerDTO = {
    enabled: toBool(at.enabled, false),
    windowSeconds: clampMetricInt(at.windowSeconds, 5, 3600, 60),
    thresholdCount: clampMetricInt(at.thresholdCount, 1, 1_000_000, 10),
    minConfidence: clampNum(at.minConfidence, 0, 1, 0.9),
    verdicts: Array.isArray(at.verdicts) ? at.verdicts : ['REJECT', 'REVIEW'],
    triggerMode: String(at.triggerMode || 'S1'),
    coolDownSeconds: clampMetricInt(at.coolDownSeconds, 0, 86400, 300),
    autoRecoverSeconds: clampMetricInt(at.autoRecoverSeconds, 0, 7 * 86400, 0),
  };
  return {
    enabled,
    mode,
    message,
    scope,
    dependencyIsolation,
    autoTrigger,
  };
}

export default function CircuitBreakerAdminForm() {
  const [state, setState] = useState<LoadState>('idle');
  const [status, setStatus] = useState<ContentSafetyCircuitBreakerStatusDTO | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState(false);
  const [committed, setCommitted] = useState<CommittedState | null>(null);

  const [reason, setReason] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [mode, setMode] = useState<'S1' | 'S2' | 'S3'>('S1');
  const [message, setMessage] = useState('');

  const [scopeAll, setScopeAll] = useState(true);
  const [userIdsRaw, setUserIdsRaw] = useState('');
  const [postIdsRaw, setPostIdsRaw] = useState('');
  const [entrypoints, setEntrypoints] = useState<Set<string>>(new Set());

  const [diMysql, setDiMysql] = useState(false);
  const [diEs, setDiEs] = useState(false);

  const [autoTrigger, setAutoTrigger] = useState<ContentSafetyCircuitBreakerAutoTriggerDTO>(DEFAULT_AUTO_TRIGGER);

  const [esFailureThreshold, setEsFailureThreshold] = useState<number>(5);
  const [esCooldownSeconds, setEsCooldownSeconds] = useState<number>(30);

  const snapshot = useMemo<CommittedState>(() => {
    return {
      enabled,
      mode,
      message,
      scopeAll,
      userIdsRaw,
      postIdsRaw,
      entrypoints: Array.from(entrypoints).map(x => String(x || '').trim()).filter(Boolean).sort(),
      diMysql,
      diEs,
      autoTrigger,
      esFailureThreshold,
      esCooldownSeconds,
    };
  }, [autoTrigger, diEs, diMysql, enabled, entrypoints, esCooldownSeconds, esFailureThreshold, message, mode, postIdsRaw, scopeAll, userIdsRaw]);

  const hasUnsavedChanges = useMemo(() => {
    if (!committed) return false;
    return JSON.stringify(snapshot) !== JSON.stringify(committed);
  }, [committed, snapshot]);

  const canSave = useMemo(() => {
    if (!editing) return false;
    if (!hasUnsavedChanges) return false;
    if (state !== 'idle') return false;
    if (!reason.trim()) return false;
    return true;
  }, [editing, hasUnsavedChanges, reason, state]);

  const load = useCallback(async () => {
    setError(null);
    setState('loading');
    try {
      const s = await adminGetContentSafetyCircuitBreakerStatus();
      const esCfg = await adminGetDependencyCircuitBreakerConfig('ES').catch(() => null);
      setStatus(s);
      const cfg = normalizeConfig(s);
      const enabledNext = Boolean(cfg.enabled);
      const modeNext = ((String(cfg.mode || 'S1').trim().toUpperCase() as 'S1' | 'S2' | 'S3') || 'S1');
      const messageNext = String(cfg.message || '');
      setEnabled(enabledNext);
      setMode(modeNext);
      setMessage(messageNext);

      const scopeState = readScopeState(cfg.scope || {});
      setScopeAll(scopeState.scopeAll);
      setUserIdsRaw(scopeState.userIdsRaw);
      setPostIdsRaw(scopeState.postIdsRaw);
      setEntrypoints(new Set(scopeState.entrypoints));

      const di = cfg.dependencyIsolation || {};
      const diMysqlNext = toBool(di.mysql, false);
      const diEsNext = toBool(di.elasticsearch, false);
      setDiMysql(diMysqlNext);
      setDiEs(diEsNext);

      const autoTriggerNext = cfg.autoTrigger || DEFAULT_AUTO_TRIGGER;
      setAutoTrigger(autoTriggerNext);

      let esFailureThresholdNext = 5;
      let esCooldownSecondsNext = 30;
      if (esCfg) {
        esFailureThresholdNext = clampMetricInt(esCfg.failureThreshold, 0, 1000, 5);
        esCooldownSecondsNext = clampMetricInt(esCfg.cooldownSeconds, 0, 3600, 30);
        setEsFailureThreshold(esFailureThresholdNext);
        setEsCooldownSeconds(esCooldownSecondsNext);
      }

      setCommitted({
        enabled: enabledNext,
        mode: modeNext,
        message: messageNext,
        scopeAll: scopeState.scopeAll,
        userIdsRaw: scopeState.userIdsRaw,
        postIdsRaw: scopeState.postIdsRaw,
        entrypoints: [...scopeState.entrypoints].sort(),
        diMysql: diMysqlNext,
        diEs: diEsNext,
        autoTrigger: autoTriggerNext,
        esFailureThreshold: esFailureThresholdNext,
        esCooldownSeconds: esCooldownSecondsNext,
      });
      setEditing(false);
      setReason('');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '加载失败';
      setError(msg);
    } finally {
      setState('idle');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const save = useCallback(async () => {
    if (!canSave) return;
    setState('saving');
    setError(null);
    try {
      const cfg = buildConfig(enabled, mode, message, scopeAll, userIdsRaw, postIdsRaw, entrypoints, diMysql, diEs, autoTrigger);
      const next = await adminUpdateContentSafetyCircuitBreakerConfig(cfg, reason.trim());
      await adminUpdateDependencyCircuitBreakerConfig('ES', { dependency: 'ES', failureThreshold: esFailureThreshold, cooldownSeconds: esCooldownSeconds }, reason.trim());
      setStatus(next);
      toast.success('已保存');
      setReason('');
      const normalized = normalizeConfig(next);
      const enabledNext = Boolean(normalized.enabled);
      const modeNext = ((String(normalized.mode || 'S1').trim().toUpperCase() as 'S1' | 'S2' | 'S3') || 'S1');
      const messageNext = String(normalized.message || '');
      setEnabled(enabledNext);
      setMode(modeNext);
      setMessage(messageNext);

      const scopeState = readScopeState(normalized.scope || {});
      setScopeAll(scopeState.scopeAll);
      setUserIdsRaw(scopeState.userIdsRaw);
      setPostIdsRaw(scopeState.postIdsRaw);
      setEntrypoints(new Set(scopeState.entrypoints));

      const di = normalized.dependencyIsolation || {};
      const diMysqlNext = toBool(di.mysql, false);
      const diEsNext = toBool(di.elasticsearch, false);
      const autoTriggerNext = normalized.autoTrigger || DEFAULT_AUTO_TRIGGER;
      setDiMysql(diMysqlNext);
      setDiEs(diEsNext);
      setAutoTrigger(autoTriggerNext);
      setCommitted({
        enabled: enabledNext,
        mode: modeNext,
        message: messageNext,
        scopeAll: scopeState.scopeAll,
        userIdsRaw: scopeState.userIdsRaw,
        postIdsRaw: scopeState.postIdsRaw,
        entrypoints: [...scopeState.entrypoints].sort(),
        diMysql: diMysqlNext,
        diEs: diEsNext,
        autoTrigger: autoTriggerNext,
        esFailureThreshold,
        esCooldownSeconds,
      });
      setEditing(false);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '保存失败';
      setError(msg);
      toast.error(msg);
    } finally {
      setState('idle');
    }
  }, [autoTrigger, canSave, diEs, diMysql, enabled, entrypoints, esCooldownSeconds, esFailureThreshold, message, mode, postIdsRaw, reason, scopeAll, userIdsRaw]);

  const toggleEntrypoint = useCallback((id: string) => {
    setEntrypoints((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const events: ContentSafetyCircuitBreakerEventDTO[] = useMemo(() => {
    return Array.isArray(status?.recentEvents) ? status!.recentEvents!.filter(Boolean) : [];
  }, [status]);

  const editable = editing && state === 'idle';

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-lg font-semibold">内容安全熔断</div>
          <div className="text-xs text-gray-500">
            统一拦截内容访问链路，支持按范围生效、自动触发与依赖隔离（S3）。
          </div>
        </div>
        <div className="flex items-center gap-2">
          {!editing ? (
            <button
              type="button"
              onClick={() => setEditing(true)}
              disabled={state !== 'idle'}
              className="px-3 py-1.5 rounded border text-sm hover:bg-gray-50 disabled:opacity-50"
            >
              编辑
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  if (!committed) return;
                  setEnabled(committed.enabled);
                  setMode(committed.mode);
                  setMessage(committed.message);
                  setScopeAll(committed.scopeAll);
                  setUserIdsRaw(committed.userIdsRaw);
                  setPostIdsRaw(committed.postIdsRaw);
                  setEntrypoints(new Set(committed.entrypoints));
                  setDiMysql(committed.diMysql);
                  setDiEs(committed.diEs);
                  setAutoTrigger(committed.autoTrigger);
                  setEsFailureThreshold(committed.esFailureThreshold);
                  setEsCooldownSeconds(committed.esCooldownSeconds);
                  setReason('');
                  setEditing(false);
                  setError(null);
                }}
                disabled={state !== 'idle'}
                className="px-3 py-1.5 rounded border text-sm hover:bg-gray-50 disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void save()}
                disabled={!canSave}
                className="px-3 py-1.5 rounded bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:opacity-50"
              >
                保存
              </button>
            </>
          )}
          <button
            type="button"
            onClick={() => void load()}
            disabled={state !== 'idle'}
            className="px-3 py-1.5 rounded border text-sm hover:bg-gray-50 disabled:opacity-50"
          >
            刷新
          </button>
        </div>
      </div>

      {error ? (
        <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>
      ) : null}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div className="rounded border p-3 space-y-3">
          <div className="text-sm font-semibold text-gray-800">当前状态</div>
          <div className="text-xs text-gray-600 space-y-1">
            <div>启用：{enabled ? '是' : '否'}</div>
            <div>模式：{mode}</div>
            <div>最近更新：{formatIso(status?.updatedAt)} {status?.updatedBy ? `by ${status.updatedBy}` : ''}</div>
            <div>持久化：{status?.persisted ? '成功' : '未确认'} {status?.lastPersistAt ? `(${formatIso(status.lastPersistAt)})` : ''}</div>
            <div>
              拦截：{status?.runtimeMetrics?.blockedLast60s ?? 0}/60s，总计 {status?.runtimeMetrics?.blockedTotal ?? 0}
            </div>
          </div>
          <div className="space-y-2">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={enabled} disabled={!editable} onChange={(e) => setEnabled(e.target.checked)} />
              <span>启用熔断</span>
            </label>
            <div className="flex items-center gap-2">
              <div className="text-sm text-gray-700 w-12">模式</div>
              <select
                value={mode}
                onChange={(e) => setMode((e.target.value as 'S1' | 'S2' | 'S3') || 'S1')}
                disabled={!editable}
                className="border rounded px-2 py-1 text-sm"
              >
                <option value="S1">S1 内容访问熔断</option>
                <option value="S2">S2 全站锁定</option>
                <option value="S3">S3 依赖隔离</option>
              </select>
            </div>
            <div className="space-y-1">
              <div className="text-sm text-gray-700">用户侧提示文案</div>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                rows={3}
                disabled={!editable}
                className="w-full border rounded px-2 py-1 text-sm"
              />
            </div>
          </div>
        </div>

        <div className="rounded border p-3 space-y-3">
          <div className="text-sm font-semibold text-gray-800">保存原因</div>
          <div className="text-xs text-gray-500">必填，将写入审计（若数据库不可用则降级为内存事件）。</div>
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={!editable}
            className="w-full border rounded px-2 py-1 text-sm"
            placeholder="例如：发现疑似漏洞扩散，临时锁定入口"
          />

          <div className="rounded bg-gray-50 p-2">
            <div className="text-xs text-gray-600">
              S1：只拦截内容相关入口（帖子/检索/聊天/文件）。S2：除后台与认证外均拦截。S3：只保留熔断控制台入口，并阻断后台业务 API。
            </div>
          </div>
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="text-sm font-semibold text-gray-800">范围控制</div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={scopeAll} disabled={!editable} onChange={(e) => setScopeAll(e.target.checked)} />
          <span>全局生效</span>
        </label>

        {!scopeAll ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="space-y-1">
              <div className="text-sm text-gray-700">用户 ID（逗号分隔）</div>
              <input
                value={userIdsRaw}
                onChange={(e) => setUserIdsRaw(e.target.value)}
                disabled={!editable}
                className="w-full border rounded px-2 py-1 text-sm"
                placeholder="例如：12, 34, 56"
              />
            </div>
            <div className="space-y-1">
              <div className="text-sm text-gray-700">帖子 ID（逗号分隔）</div>
              <input
                value={postIdsRaw}
                onChange={(e) => setPostIdsRaw(e.target.value)}
                disabled={!editable}
                className="w-full border rounded px-2 py-1 text-sm"
                placeholder="例如：1001, 1002"
              />
            </div>
            <div className="md:col-span-2 space-y-2">
              <div className="text-sm text-gray-700">入口</div>
              <div className="flex flex-wrap gap-3">
                {ENTRYPOINTS.map(ep => (
                  <label key={ep.id} className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={entrypoints.has(ep.id)}
                      disabled={!editable}
                      onChange={() => toggleEntrypoint(ep.id)}
                    />
                    <span>{ep.label}</span>
                  </label>
                ))}
              </div>
              <div className="text-xs text-gray-500">范围选择为空时，将视为全局生效。</div>
            </div>
          </div>
        ) : null}
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="text-sm font-semibold text-gray-800">依赖隔离（S3）</div>
        <div className="text-xs text-gray-500">启用 S3 后将优先通过熔断 Filter 拦截请求，避免继续打到依赖。</div>
        <div className="flex flex-wrap gap-4">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={diMysql} disabled={!editable} onChange={(e) => setDiMysql(e.target.checked)} />
            <span>隔离 MySQL（仅保留控制台）</span>
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={diEs} disabled={!editable} onChange={(e) => setDiEs(e.target.checked)} />
            <span>隔离 Elasticsearch</span>
          </label>
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="text-sm font-semibold text-gray-800">依赖熔断（Elasticsearch）</div>
        <div className="text-xs text-gray-500">连续失败达到阈值后进入冷却窗口，期间会快速失败并返回 503/502。</div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="space-y-1">
            <div className="text-sm text-gray-700">失败阈值（0=关闭）</div>
            <input
              value={esFailureThreshold}
              onChange={(e) => setEsFailureThreshold(clampMetricInt(e.target.value, 0, 1000, 5))}
              disabled={!editable}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
          <div className="space-y-1">
            <div className="text-sm text-gray-700">冷却窗口（秒）</div>
            <input
              value={esCooldownSeconds}
              onChange={(e) => setEsCooldownSeconds(clampMetricInt(e.target.value, 0, 3600, 30))}
              disabled={!editable}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="text-sm font-semibold text-gray-800">自动触发</div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={toBool(autoTrigger.enabled, false)}
            disabled={!editable}
            onChange={(e) => setAutoTrigger((prev) => ({ ...prev, enabled: e.target.checked }))}
          />
          <span>启用自动触发（基于 LLM 审核判定阈值）</span>
        </label>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="space-y-1">
            <div className="text-sm text-gray-700">窗口（秒）</div>
            <input
              value={autoTrigger.windowSeconds ?? 60}
              disabled={!editable}
              onChange={(e) => setAutoTrigger((prev) => ({ ...prev, windowSeconds: clampMetricInt(e.target.value, 5, 3600, 60) }))}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
          <div className="space-y-1">
            <div className="text-sm text-gray-700">阈值（条）</div>
            <input
              value={autoTrigger.thresholdCount ?? 10}
              disabled={!editable}
              onChange={(e) => setAutoTrigger((prev) => ({ ...prev, thresholdCount: clampMetricInt(e.target.value, 1, 1_000_000, 10) }))}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
          <div className="space-y-1">
            <div className="text-sm text-gray-700">最小置信度</div>
            <input
              value={autoTrigger.minConfidence ?? 0.9}
              disabled={!editable}
              onChange={(e) => setAutoTrigger((prev) => ({ ...prev, minConfidence: clampNum(e.target.value, 0, 1, 0.9) }))}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
          <div className="space-y-1">
            <div className="text-sm text-gray-700">触发模式</div>
            <select
              value={(autoTrigger.triggerMode || 'S1').toString().trim().toUpperCase()}
              disabled={!editable}
              onChange={(e) => setAutoTrigger((prev) => ({ ...prev, triggerMode: e.target.value }))}
              className="w-full border rounded px-2 py-1 text-sm"
            >
              <option value="S1">S1</option>
              <option value="S2">S2</option>
              <option value="S3">S3</option>
            </select>
          </div>
          <div className="space-y-1">
            <div className="text-sm text-gray-700">冷却（秒）</div>
            <input
              value={autoTrigger.coolDownSeconds ?? 300}
              disabled={!editable}
              onChange={(e) => setAutoTrigger((prev) => ({ ...prev, coolDownSeconds: clampMetricInt(e.target.value, 0, 86400, 300) }))}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
          <div className="space-y-1">
            <div className="text-sm text-gray-700">自动解除（秒，0=关闭）</div>
            <input
              value={autoTrigger.autoRecoverSeconds ?? 0}
              disabled={!editable}
              onChange={(e) => setAutoTrigger((prev) => ({ ...prev, autoRecoverSeconds: clampMetricInt(e.target.value, 0, 7 * 86400, 0) }))}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </div>
          <div className="space-y-2 md:col-span-3">
            <div className="text-sm text-gray-700">判定类型</div>
            <div className="flex flex-wrap gap-4">
              {['REJECT', 'REVIEW'].map(v => (
                <label key={v} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={Array.isArray(autoTrigger.verdicts) ? autoTrigger.verdicts.includes(v) : false}
                    disabled={!editable}
                    onChange={(e) => {
                      setAutoTrigger((prev) => {
                        const cur = Array.isArray(prev.verdicts) ? prev.verdicts : [];
                        const next = new Set(cur);
                        if (e.target.checked) next.add(v);
                        else next.delete(v);
                        return { ...prev, verdicts: Array.from(next) };
                      });
                    }}
                  />
                  <span>{v}</span>
                </label>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="rounded border p-3 space-y-2">
        <div className="text-sm font-semibold text-gray-800">最近事件</div>
        {events.length ? (
          <div className="space-y-2">
            {events.slice(0, 20).map((e, idx) => (
              <div key={`${e.at || idx}`} className="rounded bg-gray-50 p-2">
                <div className="text-xs text-gray-600 flex items-center justify-between">
                  <span>{formatIso(e.at || '')}</span>
                  <span className="font-mono">{e.type}</span>
                </div>
                <div className="text-sm text-gray-800">{e.message}</div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm text-gray-500">暂无</div>
        )}
      </div>
    </div>
  );
}
