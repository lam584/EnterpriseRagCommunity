import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import {
  adminGetHybridRetrievalConfig,
  adminListHybridRetrievalEvents,
  adminListHybridRetrievalHits,
  adminTestHybridRetrieval,
  adminUpdateHybridRetrievalConfig,
  type HybridRetrievalConfigDTO,
  type HybridRetrievalTestResponse,
  type RetrievalEventLogDTO,
  type RetrievalHitLogDTO,
} from '../../../../services/retrievalHybridService';

const inputClass =
  'block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors duration-200';
const btnPrimaryClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnSecondaryClass =
  'inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';

function fmtDateTime(v: unknown): string {
  if (!v) return '—';
  const s = String(v);
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return d.toLocaleString();
}

function safeNumber(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

const DEFAULT_CFG: HybridRetrievalConfigDTO = {
  enabled: true,
  bm25K: 50,
  bm25TitleBoost: 2,
  bm25ContentBoost: 1,
  vecK: 50,
  hybridK: 30,
  fusionMode: 'RRF',
  bm25Weight: 1,
  vecWeight: 1,
  rrfK: 60,
  rerankEnabled: true,
  rerankModel: 'qwen3-rerank',
  rerankTemperature: 0,
  rerankK: 30,
  maxDocs: 500,
  perDocMaxTokens: 4000,
  maxInputTokens: 30000,
};

const HybridSearchForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_retrieval_hybrid', 'access');
  const canWrite = hasPerm('admin_retrieval_hybrid', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<HybridRetrievalConfigDTO>({ ...DEFAULT_CFG });
  const [configLoaded, setConfigLoaded] = useState(false);

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetHybridRetrievalConfig();
      setConfig({ ...DEFAULT_CFG, ...(cfg ?? {}) });
      setConfigLoaded(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载配置失败');
      setConfigLoaded(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!canAccess) return;
    loadConfig();
  }, [canAccess, loadConfig]);

  const onSave = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const saved = await adminUpdateHybridRetrievalConfig(config);
      setConfig({ ...DEFAULT_CFG, ...(saved ?? {}) });
      setMessage('配置已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setLoading(false);
    }
  }, [config]);

  const [testQuery, setTestQuery] = useState('');
  const [testBoardId, setTestBoardId] = useState<number | ''>('');
  const [testDebug, setTestDebug] = useState(false);
  const [testResult, setTestResult] = useState<HybridRetrievalTestResponse | null>(null);

  const onTest = useCallback(async () => {
    setError(null);
    setMessage(null);
    setTestResult(null);
    setLoading(true);
    try {
      const res = await adminTestHybridRetrieval({
        queryText: testQuery,
        boardId: testBoardId === '' ? null : testBoardId,
        debug: testDebug,
        useSavedConfig: false,
        config,
      });
      setTestResult(res);
      setMessage('测试完成');
    } catch (e) {
      setError(e instanceof Error ? e.message : '测试失败');
    } finally {
      setLoading(false);
    }
  }, [config, testBoardId, testDebug, testQuery]);

  const [logsPage, setLogsPage] = useState(0);
  const [logs, setLogs] = useState<RetrievalEventLogDTO[]>([]);
  const [logsTotal, setLogsTotal] = useState(0);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [selectedHits, setSelectedHits] = useState<RetrievalHitLogDTO[] | null>(null);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    try {
      const page = await adminListHybridRetrievalEvents({ page: logsPage, size: 20 });
      setLogs(page.content ?? []);
      setLogsTotal(page.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载日志失败');
    } finally {
      setLoading(false);
    }
  }, [logsPage]);

  useEffect(() => {
    if (!canAccess) return;
    loadLogs();
  }, [canAccess, loadLogs]);

  const onSelectEvent = useCallback(
    async (eventId: number) => {
      setSelectedEventId(eventId);
      setSelectedHits(null);
      setLoading(true);
      try {
        const hits = await adminListHybridRetrievalHits(eventId);
        setSelectedHits(hits);
      } catch (e) {
        setError(e instanceof Error ? e.message : '加载命中详情失败');
      } finally {
        setLoading(false);
      }
    },
    []
  );

  const logsTotalPages = useMemo(() => Math.max(1, Math.ceil((logsTotal || 0) / 20)), [logsTotal]);

  if (accessLoading || !configLoaded) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        <div className="text-gray-500">加载中…</div>
      </div>
    );
  }

  if (!canAccess) {
    return (
      <div className="bg-white rounded-lg shadow p-4">
        <div className="text-red-600 font-medium">无权限访问：Hybrid 检索配置</div>
        <div className="text-gray-600 text-sm mt-1">需要 admin_retrieval_hybrid:access</div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">Hybrid 检索配置</h3>
          <div className="text-xs text-gray-500">BM25 召回 + 向量召回 + 融合 +（可选）重排</div>
        </div>
        <div className="flex items-center gap-2">
          <button className={btnSecondaryClass} onClick={loadConfig} disabled={loading}>
            刷新
          </button>
          <button className={btnPrimaryClass} onClick={onSave} disabled={loading || !canWrite}>
            保存配置
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
      )}
      {message && (
        <div className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{message}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">召回配置</div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.enabled)}
              onChange={e => setConfig(v => ({ ...v, enabled: e.target.checked }))}
              disabled={!canWrite}
            />
            启用 Hybrid 检索（用于 Chat RAG）
          </label>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">BM25 K</div>
              <input
                className={inputClass}
                value={config.bm25K ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25K: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="50"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">向量 K</div>
              <input
                className={inputClass}
                value={config.vecK ?? ''}
                onChange={e => setConfig(v => ({ ...v, vecK: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="50"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最终返回 K（Hybrid K）</div>
              <input
                className={inputClass}
                value={config.hybridK ?? ''}
                onChange={e => setConfig(v => ({ ...v, hybridK: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="30"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最大 Document 数量</div>
              <input
                className={inputClass}
                value={config.maxDocs ?? ''}
                onChange={e => setConfig(v => ({ ...v, maxDocs: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="500"
              />
            </div>
          </div>
        </div>

        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">BM25 字段权重</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">title boost</div>
              <input
                className={inputClass}
                value={config.bm25TitleBoost ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25TitleBoost: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="2.0"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">content_text boost</div>
              <input
                className={inputClass}
                value={config.bm25ContentBoost ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25ContentBoost: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="1.0"
              />
            </div>
          </div>

          <div className="font-medium mt-2">融合策略</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">fusionMode</div>
              <select
                className={inputClass}
                value={String(config.fusionMode ?? 'RRF')}
                onChange={e => setConfig(v => ({ ...v, fusionMode: e.target.value }))}
                disabled={!canWrite}
              >
                <option value="RRF">RRF（推荐）</option>
                <option value="LINEAR">线性加权（min-max）</option>
              </select>
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">RRF k</div>
              <input
                className={inputClass}
                value={config.rrfK ?? ''}
                onChange={e => setConfig(v => ({ ...v, rrfK: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="60"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">BM25 权重</div>
              <input
                className={inputClass}
                value={config.bm25Weight ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25Weight: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="1.0"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">向量权重</div>
              <input
                className={inputClass}
                value={config.vecWeight ?? ''}
                onChange={e => setConfig(v => ({ ...v, vecWeight: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="1.0"
              />
            </div>
          </div>
        </div>

        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">重排（Rerank）</div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.rerankEnabled)}
              onChange={e => setConfig(v => ({ ...v, rerankEnabled: e.target.checked }))}
              disabled={!canWrite}
            />
            启用重排
          </label>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div className="md:col-span-2">
              <div className="text-xs text-gray-500 mb-1">文本排序模型</div>
              <input
                className={inputClass}
                value={config.rerankModel ?? ''}
                onChange={e => setConfig(v => ({ ...v, rerankModel: e.target.value }))}
                disabled={!canWrite}
                placeholder="qwen3-rerank"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">rerank K</div>
              <input
                className={inputClass}
                value={config.rerankK ?? ''}
                onChange={e => setConfig(v => ({ ...v, rerankK: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="30"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">temperature</div>
              <input
                className={inputClass}
                value={config.rerankTemperature ?? ''}
                onChange={e => setConfig(v => ({ ...v, rerankTemperature: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="0.0"
              />
            </div>
          </div>
        </div>

        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">输入限制</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">单 Document 最大输入 Token</div>
              <input
                className={inputClass}
                value={config.perDocMaxTokens ?? ''}
                onChange={e => setConfig(v => ({ ...v, perDocMaxTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="4000"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">总最大输入 Token</div>
              <input
                className={inputClass}
                value={config.maxInputTokens ?? ''}
                onChange={e => setConfig(v => ({ ...v, maxInputTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite}
                placeholder="30000"
              />
            </div>
          </div>
        </div>
      </div>

      <div className="rounded border border-gray-200 p-3 space-y-3">
        <div className="font-medium">测试（跑通验证）</div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
          <div className="md:col-span-2">
            <div className="text-xs text-gray-500 mb-1">查询</div>
            <input className={inputClass} value={testQuery} onChange={e => setTestQuery(e.target.value)} placeholder="输入测试问题…" />
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">boardId（可选）</div>
            <input
              className={inputClass}
              value={testBoardId}
              onChange={e => setTestBoardId(e.target.value === '' ? '' : Number(e.target.value))}
              placeholder="例如 1"
            />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={testDebug} onChange={e => setTestDebug(e.target.checked)} />
          返回 debugInfo（耗时/错误）
        </label>
        <div className="flex items-center gap-2">
          <button className={btnPrimaryClass} onClick={onTest} disabled={loading || !testQuery.trim()}>
            开始测试
          </button>
          <button className={btnSecondaryClass} onClick={() => setTestResult(null)} disabled={loading}>
            清空结果
          </button>
        </div>

        {testResult && (
          <div className="space-y-2 text-sm">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              <div className="rounded bg-gray-50 border px-2 py-1">BM25: {testResult.bm25LatencyMs ?? '—'} ms</div>
              <div className="rounded bg-gray-50 border px-2 py-1">Vec: {testResult.vecLatencyMs ?? '—'} ms</div>
              <div className="rounded bg-gray-50 border px-2 py-1">Fuse: {testResult.fuseLatencyMs ?? '—'} ms</div>
              <div className="rounded bg-gray-50 border px-2 py-1">Rerank: {testResult.rerankLatencyMs ?? '—'} ms</div>
            </div>
            {testResult.bm25Error && <div className="text-red-600">BM25 错误：{testResult.bm25Error}</div>}
            {testResult.vecError && <div className="text-red-600">向量错误：{testResult.vecError}</div>}
            {testResult.rerankError && <div className="text-red-600">重排错误：{testResult.rerankError}</div>}

            <details className="rounded border border-gray-200 p-2" open>
              <summary className="cursor-pointer font-medium">最终命中（finalHits）</summary>
              <div className="mt-2 space-y-2">
                {(testResult.finalHits ?? []).map((h, idx) => (
                  <div key={`${h.docId ?? idx}`} className="rounded border border-gray-100 p-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <div className="font-medium">#{idx + 1}</div>
                      <div className="text-gray-600">docId: {h.docId ?? '—'}</div>
                      <div className="text-gray-600">postId: {h.postId ?? '—'}</div>
                      <div className="text-gray-600">score: {h.score ?? '—'}</div>
                      {h.fusedScore != null && <div className="text-gray-600">fused: {h.fusedScore}</div>}
                      {h.rerankRank != null && <div className="text-gray-600">rerankRank: {h.rerankRank}</div>}
                    </div>
                    {h.title && <div className="mt-1">{h.title}</div>}
                    {h.contentText && <div className="mt-1 text-xs text-gray-600 line-clamp-3">{h.contentText}</div>}
                  </div>
                ))}
              </div>
            </details>

            <details className="rounded border border-gray-200 p-2">
              <summary className="cursor-pointer font-medium">BM25 命中（bm25Hits）</summary>
              <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.bm25Hits ?? [], null, 2)}</pre>
            </details>
            <details className="rounded border border-gray-200 p-2">
              <summary className="cursor-pointer font-medium">向量命中（vecHits）</summary>
              <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.vecHits ?? [], null, 2)}</pre>
            </details>
            <details className="rounded border border-gray-200 p-2">
              <summary className="cursor-pointer font-medium">融合命中（fusedHits）</summary>
              <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.fusedHits ?? [], null, 2)}</pre>
            </details>
            <details className="rounded border border-gray-200 p-2">
              <summary className="cursor-pointer font-medium">重排输出（rerankHits）</summary>
              <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.rerankHits ?? [], null, 2)}</pre>
            </details>
            {testResult.debugInfo && (
              <details className="rounded border border-gray-200 p-2">
                <summary className="cursor-pointer font-medium">debugInfo</summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.debugInfo ?? {}, null, 2)}</pre>
              </details>
            )}
          </div>
        )}
      </div>

      <div className="rounded border border-gray-200 p-3 space-y-3">
        <div className="flex items-center justify-between">
          <div className="font-medium">日志（retrieval_events / retrieval_hits）</div>
          <button className={btnSecondaryClass} onClick={loadLogs} disabled={loading}>
            刷新
          </button>
        </div>
        <div className="overflow-auto border border-gray-100 rounded">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-2 py-2 text-left">时间</th>
                <th className="px-2 py-2 text-left">eventId</th>
                <th className="px-2 py-2 text-left">K（bm25/vec/hybrid）</th>
                <th className="px-2 py-2 text-left">rerank</th>
                <th className="px-2 py-2 text-left">query</th>
                <th className="px-2 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {logs.map(ev => (
                <tr key={ev.id} className="border-t">
                  <td className="px-2 py-2 whitespace-nowrap">{fmtDateTime(ev.createdAt)}</td>
                  <td className="px-2 py-2 whitespace-nowrap">{ev.id}</td>
                  <td className="px-2 py-2 whitespace-nowrap">
                    {(ev.bm25K ?? 0).toString()}/{(ev.vecK ?? 0).toString()}/{(ev.hybridK ?? '—').toString()}
                  </td>
                  <td className="px-2 py-2 whitespace-nowrap">
                    {(ev.rerankModel ?? '—').toString()} / {(ev.rerankK ?? '—').toString()}
                  </td>
                  <td className="px-2 py-2">{(ev.queryText ?? '').toString().slice(0, 120)}</td>
                  <td className="px-2 py-2 whitespace-nowrap text-right">
                    <button className={btnSecondaryClass} onClick={() => onSelectEvent(ev.id)} disabled={loading}>
                      查看命中
                    </button>
                  </td>
                </tr>
              ))}
              {logs.length === 0 && (
                <tr>
                  <td className="px-2 py-6 text-center text-gray-500" colSpan={6}>
                    暂无日志
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between">
          <div className="text-xs text-gray-500">
            第 {logsPage + 1} / {logsTotalPages} 页，共 {logsTotal} 条
          </div>
          <div className="flex items-center gap-2">
            <button className={btnSecondaryClass} disabled={loading || logsPage <= 0} onClick={() => setLogsPage(p => Math.max(0, p - 1))}>
              上一页
            </button>
            <button
              className={btnSecondaryClass}
              disabled={loading || logsPage + 1 >= logsTotalPages}
              onClick={() => setLogsPage(p => Math.min(logsTotalPages - 1, p + 1))}
            >
              下一页
            </button>
          </div>
        </div>

        {selectedEventId != null && (
          <div className="rounded border border-gray-100 p-2">
            <div className="font-medium">命中详情：eventId={selectedEventId}</div>
            {!selectedHits && <div className="text-gray-500 text-sm mt-1">加载中…</div>}
            {selectedHits && (
              <div className="mt-2 overflow-auto border border-gray-100 rounded">
                <table className="min-w-full text-sm">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-2 py-2 text-left">rank</th>
                      <th className="px-2 py-2 text-left">type</th>
                      <th className="px-2 py-2 text-left">documentId</th>
                      <th className="px-2 py-2 text-left">score</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selectedHits.map(h => (
                      <tr key={h.id} className="border-t">
                        <td className="px-2 py-2">{h.rank ?? '—'}</td>
                        <td className="px-2 py-2">{h.hitType ?? '—'}</td>
                        <td className="px-2 py-2">{h.documentId ?? '—'}</td>
                        <td className="px-2 py-2">{h.score ?? '—'}</td>
                      </tr>
                    ))}
                    {selectedHits.length === 0 && (
                      <tr>
                        <td className="px-2 py-6 text-center text-gray-500" colSpan={4}>
                          无命中
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default HybridSearchForm;
