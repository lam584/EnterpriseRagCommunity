import { useEffect, useMemo, useState } from 'react';
import {
  adminGetHotScoreConfig,
  adminGetHotScoreRecomputeLogs,
  adminRecomputeHotScores,
  adminUpdateHotScoreConfig,
  type HotScoreConfigDTO,
  type HotScoreRecomputeLogItem,
  type HotRecomputeWindow,
} from '../../../../services/hotAdminService';

const inputClass = 'w-full border rounded px-3 py-2 text-sm';
const cardClass = 'bg-white rounded-lg border p-4 space-y-4';
const AUTO_REFRESH_PRESETS = ['5', '15', '30', '60'] as const;

type AutoRefreshPreset = (typeof AUTO_REFRESH_PRESETS)[number] | 'custom';

function toStringValue(v: number | null | undefined): string {
  if (v == null || !Number.isFinite(v)) return '';
  return String(v);
}

function toNumberOrNull(v: string): number | null {
  const s = String(v ?? '').trim();
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

function toIntegerOrNull(v: string): number | null {
  const n = toNumberOrNull(v);
  if (n == null) return null;
  return Math.max(1, Math.trunc(n));
}

function resolveAutoRefreshPreset(value: string): AutoRefreshPreset {
  return AUTO_REFRESH_PRESETS.includes(value as (typeof AUTO_REFRESH_PRESETS)[number])
    ? (value as (typeof AUTO_REFRESH_PRESETS)[number])
    : 'custom';
}

function fmtDateTime(v: string | null | undefined): string {
  const s = String(v ?? '').trim();
  if (!s) return '—';
  const d = new Date(s);
  if (!Number.isFinite(d.getTime())) return s;
  return d.toLocaleString();
}

function fmtDelta(v: number | null | undefined): string {
  const n = Number(v ?? 0);
  if (!Number.isFinite(n)) return '0';
  return n.toFixed(4);
}

export default function HotBoardConfigForm() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [recomputing, setRecomputing] = useState<HotRecomputeWindow | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hint, setHint] = useState<string | null>(null);

  const [likeWeight, setLikeWeight] = useState('');
  const [favoriteWeight, setFavoriteWeight] = useState('');
  const [commentWeight, setCommentWeight] = useState('');
  const [viewWeight, setViewWeight] = useState('');
  const [allDecayDays, setAllDecayDays] = useState('');
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(true);
  const [autoRefreshPreset, setAutoRefreshPreset] = useState<AutoRefreshPreset>('60');
  const [autoRefreshCustomMinutes, setAutoRefreshCustomMinutes] = useState('60');
  const [recomputeLogs, setRecomputeLogs] = useState<HotScoreRecomputeLogItem[]>([]);
  const [recomputeLogsPage, setRecomputeLogsPage] = useState(0);
  const [recomputeLogsTotalPages, setRecomputeLogsTotalPages] = useState(0);
  const [recomputeLogsLoading, setRecomputeLogsLoading] = useState(false);

  const effectiveAutoRefreshIntervalMinutes = autoRefreshPreset === 'custom'
    ? autoRefreshCustomMinutes
    : autoRefreshPreset;

  const loadRecomputeLogs = async (page = 0) => {
    setRecomputeLogsLoading(true);
    try {
      const p = await adminGetHotScoreRecomputeLogs(page, 20);
      setRecomputeLogs(p.content ?? []);
      setRecomputeLogsPage(Number.isFinite(p.number) ? p.number : page);
      setRecomputeLogsTotalPages(Number.isFinite(p.totalPages) ? p.totalPages : 0);
    } catch {
      setRecomputeLogs([]);
      setRecomputeLogsPage(0);
      setRecomputeLogsTotalPages(0);
    } finally {
      setRecomputeLogsLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      setLoading(true);
      setError(null);
      setHint(null);
      try {
        const cfg = await adminGetHotScoreConfig();
        if (cancelled) return;
        setLikeWeight(toStringValue(cfg.likeWeight));
        setFavoriteWeight(toStringValue(cfg.favoriteWeight));
        setCommentWeight(toStringValue(cfg.commentWeight));
        setViewWeight(toStringValue(cfg.viewWeight));
        setAllDecayDays(toStringValue(cfg.allDecayDays));
        setAutoRefreshEnabled(cfg.autoRefreshEnabled ?? true);
        const loadedInterval = toStringValue(cfg.autoRefreshIntervalMinutes ?? 60);
        const loadedPreset = resolveAutoRefreshPreset(loadedInterval);
        setAutoRefreshPreset(loadedPreset);
        setAutoRefreshCustomMinutes(loadedPreset === 'custom' ? loadedInterval : '');
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : '加载热榜配置失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    void loadRecomputeLogs(0);
  }, []);

  const payload = useMemo<HotScoreConfigDTO>(() => ({
    likeWeight: toNumberOrNull(likeWeight),
    favoriteWeight: toNumberOrNull(favoriteWeight),
    commentWeight: toNumberOrNull(commentWeight),
    viewWeight: toNumberOrNull(viewWeight),
    allDecayDays: toNumberOrNull(allDecayDays),
    autoRefreshEnabled,
    autoRefreshIntervalMinutes: toIntegerOrNull(effectiveAutoRefreshIntervalMinutes),
  }), [allDecayDays, autoRefreshEnabled, commentWeight, effectiveAutoRefreshIntervalMinutes, favoriteWeight, likeWeight, viewWeight]);

  const onSave = async () => {
    if (saving) return;
    setSaving(true);
    setHint(null);
    setError(null);
    try {
      const saved = await adminUpdateHotScoreConfig(payload);
      setLikeWeight(toStringValue(saved.likeWeight));
      setFavoriteWeight(toStringValue(saved.favoriteWeight));
      setCommentWeight(toStringValue(saved.commentWeight));
      setViewWeight(toStringValue(saved.viewWeight));
      setAllDecayDays(toStringValue(saved.allDecayDays));
      setAutoRefreshEnabled(saved.autoRefreshEnabled ?? true);
      const loadedInterval = toStringValue(saved.autoRefreshIntervalMinutes ?? 60);
      const loadedPreset = resolveAutoRefreshPreset(loadedInterval);
      setAutoRefreshPreset(loadedPreset);
      setAutoRefreshCustomMinutes(loadedPreset === 'custom' ? loadedInterval : '');
      setHint('热榜配置已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存热榜配置失败');
    } finally {
      setSaving(false);
    }
  };

  const onRecompute = async (window: HotRecomputeWindow) => {
    if (recomputing) return;
    setRecomputing(window);
    setHint(null);
    setError(null);
    try {
      const r = await adminRecomputeHotScores(window);
      const changedCount = Number.isFinite(r.changedCount) ? Number(r.changedCount) : 0;
      const increasedCount = Number.isFinite(r.increasedCount) ? Number(r.increasedCount) : 0;
      const decreasedCount = Number.isFinite(r.decreasedCount) ? Number(r.decreasedCount) : 0;
      const durationMs = Number.isFinite(r.durationMs) ? Number(r.durationMs) : 0;
      setHint(
        `重算完成：${r.window ?? window}（${r.at ?? '时间未知'}）· 用时 ${durationMs}ms · 变化 ${changedCount} 条（上升 ${increasedCount} / 下降 ${decreasedCount}）`
      );
      void loadRecomputeLogs(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : '触发重算失败');
    } finally {
      setRecomputing(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className={cardClass}>
        <div>
          <h3 className="text-base font-semibold text-gray-900">热榜配置</h3>
          <p className="text-sm text-gray-600">用于调节热度分计算权重，并控制累计热度时间衰减。</p>
        </div>

        <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-700 space-y-1">
          <div className="font-medium text-gray-800">热度分计算公式</div>
          <div>原始热度 = 点赞权重 × ln(1 + 点赞数) + 收藏权重 × ln(1 + 收藏数) + 评论权重 × ln(1 + 评论数) + 浏览权重 × ln(1 + 浏览数)</div>
          <div>累计热度 = 原始热度 / (1 + 发布天数 / 累计分衰减天数)</div>
          <div>窗口榜单（24h、7d、30d、3m、6m、1y）均按对应时间范围滚动聚合。</div>
        </div>

        {error ? <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div> : null}
        {hint ? <div className="rounded border border-green-300 bg-green-50 text-green-700 px-3 py-2 text-sm">{hint}</div> : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">点赞权重</label>
            <input className={inputClass} value={likeWeight} onChange={(e) => setLikeWeight(e.target.value)} inputMode="decimal" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">收藏权重</label>
            <input className={inputClass} value={favoriteWeight} onChange={(e) => setFavoriteWeight(e.target.value)} inputMode="decimal" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">评论权重</label>
            <input className={inputClass} value={commentWeight} onChange={(e) => setCommentWeight(e.target.value)} inputMode="decimal" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">浏览权重</label>
            <input className={inputClass} value={viewWeight} onChange={(e) => setViewWeight(e.target.value)} inputMode="decimal" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">累计分衰减天数</label>
            <input className={inputClass} value={allDecayDays} onChange={(e) => setAllDecayDays(e.target.value)} inputMode="decimal" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">自动刷新间隔（分钟）</label>
            <select
              className={inputClass}
              value={autoRefreshPreset}
              onChange={(e) => setAutoRefreshPreset(e.target.value as AutoRefreshPreset)}
              disabled={!autoRefreshEnabled}
            >
              <option value="5">每 5 分钟</option>
              <option value="15">每 15 分钟</option>
              <option value="30">每 30 分钟</option>
              <option value="60">每 60 分钟</option>
              <option value="custom">自定义</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">自定义间隔（分钟）</label>
            <input
              className={inputClass}
              value={autoRefreshCustomMinutes}
              onChange={(e) => setAutoRefreshCustomMinutes(e.target.value)}
              inputMode="numeric"
              placeholder="输入 1-1440"
              disabled={!autoRefreshEnabled || autoRefreshPreset !== 'custom'}
            />
            <p className="mt-1 text-xs text-gray-500">选择“自定义”后，该输入框生效。</p>
          </div>
          <div className="md:col-span-2">
            <label className="inline-flex items-center gap-2 text-sm font-medium text-gray-700">
              <input
                type="checkbox"
                checked={autoRefreshEnabled}
                onChange={(e) => setAutoRefreshEnabled(e.target.checked)}
              />
              启用自动刷新
            </label>
            <p className="mt-1 text-xs text-gray-500">关闭后将不再按定时器自动重算热榜，建议保留手动重算。</p>
          </div>
        </div>

        <div className="flex justify-end">
          <button
            type="button"
            className="rounded bg-blue-600 text-white px-4 py-2 text-sm disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onSave()}
          >
            {saving ? '保存中…' : '保存配置'}
          </button>
        </div>
      </div>

      <div className={cardClass}>
        <div>
          <h3 className="text-base font-semibold text-gray-900">重算热点</h3>
          <p className="text-sm text-gray-600">保存权重后可立即触发重算，快速观察热榜变化。</p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('24h')}
          >
            {recomputing === '24h' ? '重算中…' : '重算 24h'}
          </button>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('7d')}
          >
            {recomputing === '7d' ? '重算中…' : '重算 7d'}
          </button>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('30d')}
          >
            {recomputing === '30d' ? '重算中…' : '重算 30d'}
          </button>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('3m')}
          >
            {recomputing === '3m' ? '重算中…' : '重算 3个月'}
          </button>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('6m')}
          >
            {recomputing === '6m' ? '重算中…' : '重算 半年'}
          </button>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('1y')}
          >
            {recomputing === '1y' ? '重算中…' : '重算 1年'}
          </button>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={loading || saving || Boolean(recomputing)}
            onClick={() => void onRecompute('all')}
          >
            {recomputing === 'all' ? '重算中…' : '重算全部'}
          </button>
        </div>
      </div>

      <div className={cardClass}>
        <div className="flex items-center justify-between gap-2">
          <div>
            <h3 className="text-base font-semibold text-gray-900">重算日志</h3>
            <p className="text-sm text-gray-600">记录每次重算的窗口参数、耗时、变化结果与影响条数。</p>
          </div>
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
            disabled={recomputeLogsLoading}
            onClick={() => void loadRecomputeLogs(recomputeLogsPage)}
          >
            {recomputeLogsLoading ? '刷新中…' : '刷新日志'}
          </button>
        </div>

        {recomputeLogs.length === 0 ? (
          <div className="text-sm text-gray-500">暂无重算日志</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b text-left text-gray-500">
                  <th className="py-2 pr-3">时间</th>
                  <th className="py-2 pr-3">窗口</th>
                  <th className="py-2 pr-3">耗时</th>
                  <th className="py-2 pr-3">变化条数</th>
                  <th className="py-2 pr-3">上升</th>
                  <th className="py-2 pr-3">下降</th>
                  <th className="py-2 pr-3">不变</th>
                </tr>
              </thead>
              <tbody>
                {recomputeLogs.map((log) => (
                  <tr key={log.id} className="border-b hover:bg-gray-50">
                    <td className="py-2 pr-3">{fmtDateTime(log.finishedAt || log.createdAt)}</td>
                    <td className="py-2 pr-3">{log.window || '—'}</td>
                    <td className="py-2 pr-3">{Number.isFinite(log.durationMs) ? `${log.durationMs}ms` : '—'}</td>
                    <td className="py-2 pr-3">{log.changedCount ?? 0}</td>
                    <td className="py-2 pr-3 text-green-700">
                      {`${log.increasedCount ?? 0} 条 / +${fmtDelta(log.increasedScoreDelta)}`}
                    </td>
                    <td className="py-2 pr-3 text-red-700">
                      {`${log.decreasedCount ?? 0} 条 / -${fmtDelta(log.decreasedScoreDelta)}`}
                    </td>
                    <td className="py-2 pr-3">{log.unchangedCount ?? 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {recomputeLogsTotalPages > 1 ? (
          <div className="flex items-center gap-2 pt-2">
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
              disabled={recomputeLogsLoading || recomputeLogsPage <= 0}
              onClick={() => void loadRecomputeLogs(recomputeLogsPage - 1)}
            >
              上一页
            </button>
            <span className="text-xs text-gray-500">{recomputeLogsPage + 1} / {recomputeLogsTotalPages}</span>
            <button
              type="button"
              className="rounded border px-3 py-2 text-sm hover:bg-gray-50 disabled:opacity-60"
              disabled={recomputeLogsLoading || recomputeLogsPage >= recomputeLogsTotalPages - 1}
              onClick={() => void loadRecomputeLogs(recomputeLogsPage + 1)}
            >
              下一页
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
