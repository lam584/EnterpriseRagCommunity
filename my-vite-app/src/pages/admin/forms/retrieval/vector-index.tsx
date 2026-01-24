import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import type { SpringPage } from '../../../../types/page';
import {
  adminBuildPostRagIndex,
  adminDeleteVectorIndex,
  adminGetRagAutoSyncConfig,
  adminListVectorIndices,
  adminRebuildPostRagIndex,
  adminSyncPostRagIndex,
  adminTestQueryPostRagIndex,
  adminUpdateVectorIndex,
  adminUpdateRagAutoSyncConfig,
  type RagPostsBuildResponse,
  type RagPostsTestQueryResponse,
  type VectorIndexDTO,
  type VectorIndexProvider,
  type VectorIndexStatus,
} from '../../../../services/retrievalVectorIndexService';
import { adminListAuditLogs, type AuditLogDTO } from '../../../../services/auditLogService';

function safeNumber(v: unknown): number | undefined {
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

function fmtDateTime(v: unknown): string {
  if (!v) return '—';
  const s = String(v);
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return d.toLocaleString();
}

function metaStr(meta: VectorIndexDTO['metadata'], key: string): unknown {
  if (!meta || typeof meta !== 'object') return undefined;
  return (meta as Record<string, unknown>)[key];
}

const PROVIDER_LABEL: Record<VectorIndexProvider, string> = {
  FAISS: 'FAISS',
  MILVUS: 'Milvus',
  OTHER: 'Elasticsearch',
};

const STATUS_LABEL: Record<VectorIndexStatus, string> = {
  READY: '可用',
  BUILDING: '构建中',
  ERROR: '异常',
};

// Common UI Components Styles
const inputClass = "block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors duration-200";
const btnPrimaryClass = "inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200";
const btnDangerClass = "inline-flex items-center justify-center rounded-md border border-transparent bg-red-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200";
const btnSecondaryClass = "inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200";

const VectorIndexForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_retrieval_index', 'access');
  const canAction = hasPerm('admin_retrieval_index', 'action');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [indicesPage, setIndicesPage] = useState<SpringPage<VectorIndexDTO> | null>(null);

  const indices = indicesPage?.content ?? [];
  const [selectedIndexId, setSelectedIndexId] = useState<number | ''>('');

  const selectedIndex = useMemo(() => {
    if (!selectedIndexId) return undefined;
    return indices.find(it => it.id === selectedIndexId);
  }, [indices, selectedIndexId]);

  const [indexConfig, setIndexConfig] = useState({
    dim: '' as number | '',
    embeddingModel: '',
    defaultChunkMaxChars: 800,
    defaultChunkOverlapChars: 80,
  });
  const [indexConfigEditing, setIndexConfigEditing] = useState(false);

  const [buildForm, setBuildForm] = useState({
    boardId: '' as number | '',
    fromPostId: '' as number | '',
    postBatchSize: 50,
    chunkMaxChars: 800,
    chunkOverlapChars: 80,
    embeddingDims: '' as number | '',
    embeddingModel: '',
    clear: false,
  });
  const [lastBuild, setLastBuild] = useState<RagPostsBuildResponse | null>(null);

  const [autoSync, setAutoSync] = useState({ enabled: false, intervalSeconds: 30 });
  const [autoSyncLoading, setAutoSyncLoading] = useState(false);
  const [autoSyncError, setAutoSyncError] = useState<string | null>(null);

  const [testForm, setTestForm] = useState({
    queryText: '',
    topK: 8,
    numCandidates: '' as number | '',
    embeddingModel: '',
    boardId: '' as number | '',
  });
  const [testResult, setTestResult] = useState<RagPostsTestQueryResponse | null>(null);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyItems, setHistoryItems] = useState<AuditLogDTO[]>([]);

  const resetIndexConfigFromSelectedIndex = useCallback(() => {
    if (!selectedIndex) return;
    const meta = selectedIndex.metadata ?? undefined;
    const metaDefaultMax = safeNumber(metaStr(meta, 'defaultChunkMaxChars'));
    const metaDefaultOverlap = safeNumber(metaStr(meta, 'defaultChunkOverlapChars'));
    const metaLastMax = safeNumber(metaStr(meta, 'lastBuildChunkMaxChars'));
    const metaLastOverlap = safeNumber(metaStr(meta, 'lastBuildChunkOverlapChars'));
    const metaModel = String(metaStr(meta, 'embeddingModel') ?? metaStr(meta, 'lastBuildEmbeddingModel') ?? '').trim();

    const nextDefaultMax = metaDefaultMax ?? metaLastMax ?? 800;
    const nextDefaultOverlap = metaDefaultOverlap ?? metaLastOverlap ?? 80;

    setIndexConfig({
      dim: selectedIndex.dim && selectedIndex.dim > 0 ? selectedIndex.dim : '',
      embeddingModel: metaModel,
      defaultChunkMaxChars: nextDefaultMax,
      defaultChunkOverlapChars: nextDefaultOverlap,
    });
    setIndexConfigEditing(false);

    setBuildForm((v) => ({
      ...v,
      chunkMaxChars: nextDefaultMax,
      chunkOverlapChars: nextDefaultOverlap,
      embeddingDims: selectedIndex.dim && selectedIndex.dim > 0 ? selectedIndex.dim : v.embeddingDims,
      embeddingModel: metaModel || v.embeddingModel,
    }));

    setTestForm((v) => ({
      ...v,
      embeddingModel: metaModel || v.embeddingModel,
    }));
  }, [selectedIndex]);

  useEffect(() => {
    resetIndexConfigFromSelectedIndex();
  }, [resetIndexConfigFromSelectedIndex]);

  const load = useCallback(async () => {
    if (!canAccess) return;
    setLoading(true);
    setError(null);
    try {
      const res = await adminListVectorIndices({ page, size: 50 });
      setIndicesPage(res);
      const first = res.content?.[0];
      if (first && !selectedIndexId) setSelectedIndexId(first.id);
    } catch (e: any) {
      setError(e?.message || '加载向量索引失败');
    } finally {
      setLoading(false);
    }
  }, [canAccess, page, selectedIndexId]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadAutoSync = useCallback(async () => {
    if (!canAccess) return;
    setAutoSyncLoading(true);
    setAutoSyncError(null);
    try {
      const cfg = await adminGetRagAutoSyncConfig();
      setAutoSync({
        enabled: Boolean(cfg.enabled),
        intervalSeconds: Math.max(5, Math.min(3600, Math.trunc(Number(cfg.intervalSeconds ?? 30)))),
      });
    } catch (e: any) {
      setAutoSyncError(e?.message || '加载自动同步配置失败');
    } finally {
      setAutoSyncLoading(false);
    }
  }, [canAccess]);

  useEffect(() => {
    void loadAutoSync();
  }, [loadAutoSync]);

  const loadHistory = useCallback(async () => {
    if (!selectedIndexId) return;
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const pageRes = await adminListAuditLogs({
        page: 1,
        pageSize: 20,
        entityType: 'VECTOR_INDEX',
        entityId: selectedIndexId,
        action: 'RETRIEVAL_',
        sort: 'createdAt,desc',
      });
      setHistoryItems(pageRes.content ?? []);
    } catch (e: any) {
      setHistoryError(e?.message || '加载历史记录失败');
    } finally {
      setHistoryLoading(false);
    }
  }, [selectedIndexId]);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  const onDelete = useCallback(async (id: number) => {
    if (!canAction) return;
    if (!window.confirm(`确定删除索引 #${id} 吗？这不会自动删除 ES 中的历史数据。`)) return;
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      await adminDeleteVectorIndex(id);
      setMessage(`已删除索引 #${id}`);
      if (selectedIndexId === id) setSelectedIndexId('');
      await load();
    } catch (e: any) {
      setError(e?.message || '删除失败');
    } finally {
      setLoading(false);
    }
  }, [canAction, load, selectedIndexId]);

  const onBuild = useCallback(async () => {
    if (!canAction) return;
    if (!selectedIndexId) {
      setError('请先选择一个索引');
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    setLastBuild(null);
    try {
      const res = await adminBuildPostRagIndex({
        id: selectedIndexId,
        boardId: buildForm.boardId === '' ? undefined : Number(buildForm.boardId),
        fromPostId: buildForm.fromPostId === '' ? undefined : Number(buildForm.fromPostId),
        postBatchSize: buildForm.postBatchSize,
        chunkMaxChars: buildForm.chunkMaxChars,
        chunkOverlapChars: buildForm.chunkOverlapChars,
        clear: buildForm.clear,
        embeddingDims: buildForm.embeddingDims === '' ? undefined : Number(buildForm.embeddingDims),
        embeddingModel: buildForm.embeddingModel.trim() ? buildForm.embeddingModel.trim() : undefined,
      });
      setLastBuild(res);
      setMessage(`${res.failedChunks ? '构建完成（部分失败）' : '构建完成'}：成功 ${res.successChunks ?? 0}，失败 ${res.failedChunks ?? 0}，耗时 ${res.tookMs ?? 0}ms`);
      await load();
    } catch (e: any) {
      setError(e?.message || '构建失败');
      await load();
    } finally {
      setLoading(false);
    }
  }, [buildForm, canAction, load, selectedIndexId]);

  const onRebuild = useCallback(async () => {
    if (!canAction) return;
    if (!selectedIndexId) {
      setError('请先选择一个索引');
      return;
    }
    if (!window.confirm('确定执行全量重建吗？将先删除 ES 索引并重新创建，再全量写入。')) return;
    setLoading(true);
    setError(null);
    setMessage(null);
    setLastBuild(null);
    try {
      const res = await adminRebuildPostRagIndex({
        id: selectedIndexId,
        boardId: buildForm.boardId === '' ? undefined : Number(buildForm.boardId),
        postBatchSize: buildForm.postBatchSize,
        chunkMaxChars: buildForm.chunkMaxChars,
        chunkOverlapChars: buildForm.chunkOverlapChars,
        embeddingDims: buildForm.embeddingDims === '' ? undefined : Number(buildForm.embeddingDims),
        embeddingModel: buildForm.embeddingModel.trim() ? buildForm.embeddingModel.trim() : undefined,
      });
      setLastBuild(res);
      const clearedText =
        res.cleared === true ? '已删除并重建索引' : res.cleared === false ? `清空失败：${res.clearError ?? ''}` : '';
      const msgParts = [
        clearedText,
        `成功 ${res.successChunks ?? 0}`,
        `失败 ${res.failedChunks ?? 0}`,
        `耗时 ${res.tookMs ?? 0}ms`,
      ].filter(Boolean);
      setMessage(`全量重建完成：${msgParts.join('，')}`);
      await load();
      await loadHistory();
    } catch (e: any) {
      setError(e?.message || '全量重建失败');
      await load();
      await loadHistory();
    } finally {
      setLoading(false);
    }
  }, [buildForm, canAction, load, loadHistory, selectedIndexId]);

  const onSync = useCallback(async () => {
    if (!canAction) return;
    if (!selectedIndexId) {
      setError('请先选择一个索引');
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    setLastBuild(null);
    try {
      const res = await adminSyncPostRagIndex({
        id: selectedIndexId,
        boardId: buildForm.boardId === '' ? undefined : Number(buildForm.boardId),
        postBatchSize: buildForm.postBatchSize,
        chunkMaxChars: buildForm.chunkMaxChars,
        chunkOverlapChars: buildForm.chunkOverlapChars,
        embeddingDims: buildForm.embeddingDims === '' ? undefined : Number(buildForm.embeddingDims),
        embeddingModel: buildForm.embeddingModel.trim() ? buildForm.embeddingModel.trim() : undefined,
      });
      setLastBuild(res);
      setMessage(`${res.failedChunks ? '增量同步完成（部分失败）' : '增量同步完成'}：新增帖子 ${res.totalPosts ?? 0}，成功 ${res.successChunks ?? 0}，失败 ${res.failedChunks ?? 0}`);
      await load();
      await loadHistory();
    } catch (e: any) {
      setError(e?.message || '增量同步失败');
      await load();
      await loadHistory();
    } finally {
      setLoading(false);
    }
  }, [buildForm, canAction, load, loadHistory, selectedIndexId]);

  const onSaveAutoSync = useCallback(async (next?: { enabled?: boolean; intervalSeconds?: number }) => {
    if (!canAction) return;
    const payload = {
      enabled: next?.enabled ?? autoSync.enabled,
      intervalSeconds: next?.intervalSeconds ?? autoSync.intervalSeconds,
    };
    setAutoSyncLoading(true);
    setAutoSyncError(null);
    setMessage(null);
    try {
      const res = await adminUpdateRagAutoSyncConfig({
        enabled: payload.enabled,
        intervalSeconds: payload.intervalSeconds,
      });
      setAutoSync({
        enabled: Boolean(res.enabled),
        intervalSeconds: Math.max(5, Math.min(3600, Math.trunc(Number(res.intervalSeconds ?? 30)))),
      });
      setMessage('自动同步配置已保存');
    } catch (e: any) {
      setAutoSyncError(e?.message || '保存失败');
    } finally {
      setAutoSyncLoading(false);
    }
  }, [autoSync.enabled, autoSync.intervalSeconds, canAction]);

  const onSaveIndexConfig = useCallback(async (): Promise<boolean> => {
    if (!canAction) return false;
    if (!selectedIndexId) {
      setError('请先选择一个索引');
      return false;
    }

    const mergedMeta = {
      ...(selectedIndex?.metadata ?? {}),
      embeddingModel: indexConfig.embeddingModel.trim() ? indexConfig.embeddingModel.trim() : undefined,
      defaultChunkMaxChars: Math.max(200, Math.trunc(Number(indexConfig.defaultChunkMaxChars))),
      defaultChunkOverlapChars: Math.max(0, Math.trunc(Number(indexConfig.defaultChunkOverlapChars))),
    } as Record<string, unknown>;
    if (!mergedMeta.embeddingModel) delete mergedMeta.embeddingModel;

    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      await adminUpdateVectorIndex({
        id: selectedIndexId,
        dim: indexConfig.dim === '' ? 0 : Math.max(0, Math.trunc(Number(indexConfig.dim))),
        metadata: mergedMeta,
      });
      setMessage('索引配置已保存');
      await load();
      return true;
    } catch (e: any) {
      setError(e?.message || '保存索引配置失败');
      return false;
    } finally {
      setLoading(false);
    }
  }, [canAction, indexConfig.defaultChunkMaxChars, indexConfig.defaultChunkOverlapChars, indexConfig.dim, indexConfig.embeddingModel, load, selectedIndex?.metadata, selectedIndexId]);

  const onTestQuery = useCallback(async () => {
    if (!canAction) return;
    if (!selectedIndexId) {
      setError('请先选择一个索引');
      return;
    }
    if (!testForm.queryText.trim()) {
      setError('请输入查询内容');
      return;
    }
    setLoading(true);
    setError(null);
    setMessage(null);
    setTestResult(null);
    try {
      const res = await adminTestQueryPostRagIndex({
        id: selectedIndexId,
        payload: {
          queryText: testForm.queryText.trim(),
          topK: Math.max(1, Math.min(50, Math.trunc(testForm.topK))),
          numCandidates: testForm.numCandidates === '' ? undefined : Math.max(10, Math.min(10_000, Math.trunc(Number(testForm.numCandidates)))),
          embeddingModel: testForm.embeddingModel.trim() ? testForm.embeddingModel.trim() : undefined,
          boardId: testForm.boardId === '' ? undefined : Number(testForm.boardId),
        },
      });
      setTestResult(res);
      setMessage(`测试查询完成：命中 ${(res.hits ?? []).length} 条，耗时 ${res.tookMs ?? 0}ms`);
      await loadHistory();
    } catch (e: any) {
      setError(e?.message || '测试查询失败');
      await loadHistory();
    } finally {
      setLoading(false);
    }
  }, [canAction, loadHistory, selectedIndexId, testForm.boardId, testForm.embeddingModel, testForm.numCandidates, testForm.queryText, testForm.topK]);

  if (accessLoading) {
    return <div className="bg-white rounded-lg shadow p-4 text-sm text-gray-600 animate-pulse">加载中…</div>;
  }

  if (!canAccess) {
    return (
      <div className="bg-white rounded-lg shadow p-6 space-y-2">
        <div className="text-lg font-semibold text-red-600">403 无权限</div>
        <div className="text-sm text-gray-600">需要权限：admin_retrieval_index:access</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* 1. 索引列表与概览 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <div>
             <h3 className="font-semibold text-gray-800">向量索引管理</h3>
             <div className="text-xs text-gray-500 mt-0.5">管理与构建帖子向量索引（基于 Elasticsearch）</div>
          </div>
          <button className={btnSecondaryClass} disabled={loading} onClick={() => void load()}>刷新</button>
        </div>
        
        {error && <div className="mx-4 mt-3 p-2 rounded bg-red-50 text-xs text-red-700 border border-red-100">{error}</div>}
        {message && <div className="mx-4 mt-3 p-2 rounded bg-green-50 text-xs text-green-700 border border-green-100">{message}</div>}

        <div className="p-4">
          {indices.length === 0 ? (
             <div className="text-sm text-gray-500 py-4 text-center bg-gray-50 rounded border border-dashed">暂无索引。</div>
          ) : (
            <div className="overflow-x-auto border rounded-md">
              <table className="min-w-full text-sm divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr className="text-left text-gray-500 font-medium text-sm uppercase tracking-wider">
                    <th className="py-2 px-3">ID</th>
                    <th className="py-2 px-3">集合名</th>
                    <th className="py-2 px-3">引擎</th>
                    <th className="py-2 px-3">维度</th>
                    <th className="py-2 px-3">度量</th>
                    <th className="py-2 px-3">状态</th>
                    <th className="py-2 px-3">最后构建</th>
                    {indices.length > 1 ? <th className="py-2 px-3 text-right">操作</th> : null}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 bg-white">
                  {indices.map(it => {
                    const lastBuildAt = metaStr(it.metadata, 'lastBuildAt');
                    return (
                      <tr
                        key={it.id}
                        className={`hover:bg-gray-50 transition-colors cursor-pointer ${selectedIndexId === it.id ? 'bg-blue-50/30' : ''}`}
                        onClick={() => setSelectedIndexId((cur) => (cur === it.id ? '' : it.id))}
                      >
                        <td className="py-2 px-3 text-gray-500 text-sm">{it.id}</td>
                        <td className="py-2 px-3 font-mono text-gray-900 break-all text-sm font-medium">{it.collectionName}</td>
                        <td className="py-2 px-3 text-gray-500 text-sm">{PROVIDER_LABEL[it.provider] ?? it.provider}</td>
                        <td className="py-2 px-3 text-gray-500 text-sm">{it.dim}</td>
                        <td className="py-2 px-3 text-gray-500 text-sm">{it.metric}</td>
                        <td className="py-2 px-3">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wide ${
                            it.status === 'READY' ? 'bg-green-100 text-green-800' :
                            it.status === 'ERROR' ? 'bg-red-100 text-red-800' :
                            'bg-yellow-100 text-yellow-800'
                          }`}>
                            {STATUS_LABEL[it.status] ?? it.status}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-gray-400 text-sm whitespace-nowrap">{fmtDateTime(lastBuildAt)}</td>
                        {indices.length > 1 ? (
                          <td className="py-2 px-3 text-right">
                            <button
                              className="text-red-600 hover:text-red-900 hover:bg-red-50 px-3 py-1.5 rounded text-sm transition-colors"
                              disabled={loading || !canAction}
                              onClick={(e) => {
                                e.stopPropagation();
                                void onDelete(it.id);
                              }}
                            >
                              删除
                            </button>
                          </td>
                        ) : null}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          {indicesPage && (indicesPage.totalPages ?? 0) > 1 && (
            <div className="flex items-center justify-end gap-2 mt-2">
              <button
                className="text-xs text-gray-600 hover:text-blue-600 disabled:text-gray-300"
                disabled={loading || page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                上一页
              </button>
              <span className="text-xs text-gray-400">
                {page + 1} / {indicesPage.totalPages}
              </span>
              <button
                className="text-xs text-gray-600 hover:text-blue-600 disabled:text-gray-300"
                disabled={loading || page >= (indicesPage.totalPages ?? 1) - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </button>
            </div>
          )}
        </div>
      </div>

      {/* 2. 核心操作区：仅当选中索引时显示 */}
      {selectedIndexId && (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="px-4 py-2 border-b border-gray-100 bg-blue-50/50 flex items-center justify-between">
            <div className="text-sm font-semibold text-gray-800 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-blue-500"></span>
              操作台：{selectedIndex?.collectionName}
            </div>
            <div className="flex items-center gap-2">
               <button className={btnSecondaryClass} disabled={loading || !canAction} onClick={() => void onSync()}>增量同步</button>
               <button className={btnDangerClass} disabled={loading || !canAction} onClick={() => void onRebuild()}>全量重建</button>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 divide-y lg:divide-y-0 lg:divide-x divide-gray-100">
            {/* 左侧：构建与同步配置 */}
            <div className="p-4 space-y-4">
              <div className="space-y-3">
                 <div className="rounded border border-gray-200 bg-white p-3 space-y-3">
                   <div className="flex items-center justify-between">
                     <h4 className="text-sm font-bold text-gray-500 uppercase tracking-wider">索引默认配置</h4>
                     <div className="flex items-center gap-2">
                       {!indexConfigEditing ? (
                         <button
                           className={btnSecondaryClass}
                           disabled={loading || !canAction}
                           onClick={() => {
                             if (loading || !canAction) return;
                             setIndexConfigEditing(true);
                             setError(null);
                             setMessage(null);
                           }}
                         >
                           编辑
                         </button>
                       ) : (
                         <>
                           <button
                             className={btnSecondaryClass}
                             disabled={loading}
                             onClick={() => {
                               resetIndexConfigFromSelectedIndex();
                               setError(null);
                               setMessage(null);
                             }}
                           >
                             放弃修改
                           </button>
                           <button
                             className={btnSecondaryClass}
                             disabled={loading || !canAction}
                             onClick={async () => {
                               const ok = await onSaveIndexConfig();
                               if (ok) setIndexConfigEditing(false);
                             }}
                           >
                             保存
                           </button>
                         </>
                       )}
                     </div>
                   </div>

                   <div className="grid grid-cols-2 gap-3">
                     <div>
                       <label className="text-xs text-gray-500 font-medium mb-1 block">模型名（默认）</label>
                       <input
                         className={`${inputClass} disabled:bg-gray-50 disabled:text-gray-500`}
                         placeholder="留空使用后端默认"
                         value={indexConfig.embeddingModel}
                         disabled={loading || !canAction || !indexConfigEditing}
                         onChange={(e) => setIndexConfig(v => ({ ...v, embeddingModel: e.target.value }))} />
                     </div>
                     <div>
                       <label className="text-xs text-gray-500 font-medium mb-1 block">维度 dim（0=自动）</label>
                       <input
                         type="number"
                         className={`${inputClass} disabled:bg-gray-50 disabled:text-gray-500`}
                         placeholder="0"
                         value={indexConfig.dim}
                         disabled={loading || !canAction || !indexConfigEditing}
                         onChange={(e) => setIndexConfig(v => ({ ...v, dim: e.target.value ? (safeNumber(e.target.value) ?? '') : '' }))} />
                     </div>
                     <div>
                       <label className="text-xs text-gray-500 font-medium mb-1 block">最大输入长度（字符，默认）</label>
                       <input
                         type="number"
                         className={`${inputClass} disabled:bg-gray-50 disabled:text-gray-500`}
                         placeholder="800"
                         value={indexConfig.defaultChunkMaxChars}
                         disabled={loading || !canAction || !indexConfigEditing}
                         onChange={(e) => setIndexConfig(v => ({ ...v, defaultChunkMaxChars: Math.max(200, Math.trunc(Number(e.target.value))) }))} />
                     </div>
                     <div>
                       <label className="text-xs text-gray-500 font-medium mb-1 block">重叠长度（字符，默认）</label>
                       <input
                         type="number"
                         className={`${inputClass} disabled:bg-gray-50 disabled:text-gray-500`}
                         placeholder="80"
                         value={indexConfig.defaultChunkOverlapChars}
                         disabled={loading || !canAction || !indexConfigEditing}
                         onChange={(e) => setIndexConfig(v => ({ ...v, defaultChunkOverlapChars: Math.max(0, Math.trunc(Number(e.target.value))) }))} />
                     </div>
                   </div>
                 </div>

                 <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold text-gray-500 uppercase tracking-wider">构建参数</h4>
                 </div>
                 
                 <div className="grid grid-cols-2 gap-3">
                    <div className="col-span-2">
                      <label className="text-xs text-gray-500 font-medium mb-1 block">Embedding 模型名（可选，留空用后端默认/索引配置）</label>
                      <input className={inputClass} placeholder="例如：text-embedding-3-large / bge-m3 / ..." value={buildForm.embeddingModel}
                        onChange={(e) => setBuildForm(v => ({ ...v, embeddingModel: e.target.value }))} />
                    </div>
                    <div>
                      <label className="text-xs text-gray-500 font-medium mb-1 block">版块 ID</label>
                      <input className={inputClass} placeholder="可选" value={buildForm.boardId}
                        onChange={(e) => setBuildForm(v => ({ ...v, boardId: e.target.value ? (safeNumber(e.target.value) ?? '') : '' }))} />
                    </div>
                    <div>
                      <label className="text-xs text-gray-500 font-medium mb-1 block">起始帖子 ID</label>
                      <input className={inputClass} placeholder="可选" value={buildForm.fromPostId}
                        onChange={(e) => setBuildForm(v => ({ ...v, fromPostId: e.target.value ? (safeNumber(e.target.value) ?? '') : '' }))} />
                    </div>
                    <div>
                      <label className="text-xs text-gray-500 font-medium mb-1 block">批量大小</label>
                      <input type="number" className={inputClass} placeholder="50" value={buildForm.postBatchSize}
                        onChange={(e) => setBuildForm(v => ({ ...v, postBatchSize: Math.max(1, Math.trunc(Number(e.target.value))) }))} />
                    </div>
                    <div>
                      <label className="text-xs text-gray-500 font-medium mb-1 block">向量维度（可选；用于校验与创建映射）</label>
                      <input type="number" className={inputClass} placeholder="例如：1536" value={buildForm.embeddingDims}
                        onChange={(e) => setBuildForm(v => ({ ...v, embeddingDims: e.target.value ? (safeNumber(e.target.value) ?? '') : '' }))} />
                    </div>
                    <div>
                      <label className="text-xs text-gray-500 font-medium mb-1 block">最大输入长度（字符）</label>
                      <input type="number" className={inputClass} placeholder="800" value={buildForm.chunkMaxChars}
                        onChange={(e) => setBuildForm(v => ({ ...v, chunkMaxChars: Math.max(200, Math.trunc(Number(e.target.value))) }))} />
                    </div>
                    <div>
                      <label className="text-xs text-gray-500 font-medium mb-1 block">重叠长度（字符）</label>
                      <input type="number" className={inputClass} placeholder="80" value={buildForm.chunkOverlapChars}
                        onChange={(e) => setBuildForm(v => ({ ...v, chunkOverlapChars: Math.max(0, Math.trunc(Number(e.target.value))) }))} />
                    </div>
                 </div>

                 {/* 自动同步配置 - Moved here */}
                 <div className="pt-2 pb-2 border-t border-b border-gray-100">
                    <div className="flex items-center justify-between">
                       <label className="inline-flex items-center gap-2 text-sm text-gray-700 cursor-pointer select-none">
                         <input type="checkbox" className="rounded text-blue-600 focus:ring-0 w-4 h-4"
                           checked={autoSync.enabled} disabled={autoSyncLoading || !canAction}
                           onChange={(e) => {
                             const enabled = e.target.checked;
                             setAutoSync(v => ({ ...v, enabled }));
                             void onSaveAutoSync({ enabled });
                           }} />
                        <span className="font-medium">启用自动增量同步</span>
                       </label>
                       {autoSync.enabled && (
                         <div className="flex items-center gap-2">
                            <span className="text-sm text-gray-500">每</span>
                            <input type="number" className="w-16 text-sm border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500 text-center py-1"
                               value={autoSync.intervalSeconds} disabled={autoSyncLoading || !canAction}
                               onChange={(e) => setAutoSync(v => ({ ...v, intervalSeconds: Math.max(5, Math.trunc(Number(e.target.value))) }))}
                               onBlur={() => void onSaveAutoSync()} />
                            <span className="text-sm text-gray-500">秒</span>
                         </div>
                       )}
                    </div>
                    {autoSyncError && <div className="text-xs text-red-600 mt-1">{autoSyncError}</div>}
                 </div>

                 <div className="flex items-center justify-between pt-2">
                    <label className="inline-flex items-center gap-2 text-sm text-gray-600 cursor-pointer select-none">
                      <input type="checkbox" className="rounded text-blue-600 focus:ring-0 w-4 h-4"
                        checked={buildForm.clear} onChange={(e) => setBuildForm(v => ({ ...v, clear: e.target.checked }))} />
                      构建前清空
                    </label>
                    <button className={btnPrimaryClass} disabled={loading || !canAction} onClick={() => void onBuild()}>
                       执行自定义构建
                    </button>
                 </div>

                 {lastBuild && (
                    <div className="rounded border border-blue-100 bg-blue-50/30 p-3 text-sm space-y-1 mt-2">
                      <div className="font-semibold text-blue-800 flex justify-between">
                        <span>上次构建结果</span>
                        <span className="font-mono text-gray-500">{lastBuild.tookMs}ms</span>
                      </div>
                      <div className="grid grid-cols-2 gap-x-2 text-gray-600">
                        <span>帖子数：{lastBuild.totalPosts}</span>
                        <span>分块数：{lastBuild.totalChunks}</span>
                        <span className="text-green-600">成功：{lastBuild.successChunks}</span>
                        <span className={lastBuild.failedChunks ? "text-red-600" : "text-gray-400"}>失败：{lastBuild.failedChunks}</span>
                      </div>
                      {lastBuild.failedDocs && lastBuild.failedDocs.length > 0 ? (
                        <div className="border-t border-blue-100 pt-2 mt-1 space-y-1">
                          <div className="text-red-700 font-medium">错误详情</div>
                          <div className="max-h-24 overflow-y-auto pr-1 space-y-1">
                            {lastBuild.failedDocs.map((fd, idx) => (
                              <div key={fd.docId ?? String(idx)} className="text-red-600 break-words">
                                <span className="font-mono">{fd.docId ?? 'unknown-doc'}</span>
                                <span>：{fd.error ?? '未知错误'}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      ) : (lastBuild.failedDocIds && lastBuild.failedDocIds.length > 0 ? (
                        <div className="text-red-600 truncate border-t border-blue-100 pt-1 mt-1">错误文档：{lastBuild.failedDocIds.join(', ')}</div>
                      ) : null)}
                    </div>
                 )}
              </div>
            </div>

            {/* 右侧：测试查询 */}
            <div className="p-4 space-y-4 bg-gray-50/30">
              <div className="space-y-3">
                 <h4 className="text-sm font-bold text-gray-500 uppercase tracking-wider">语义检索测试</h4>
                 
                 <div className="space-y-3">
                   <textarea 
                     className={`${inputClass} min-h-[100px] resize-y`} 
                     placeholder="输入需要检索的问题或关键词（支持长文本）...
Enter 搜索，Shift + Enter 换行" 
                     value={testForm.queryText}
                     onChange={(e) => setTestForm(v => ({ ...v, queryText: e.target.value }))}
                     onKeyDown={(e) => {
                       if (e.key === 'Enter' && !e.shiftKey) {
                         e.preventDefault();
                         void onTestQuery();
                       }
                     }} 
                   />
                   
                   <div className="flex items-center justify-between">
                     <div className="flex items-center gap-3 flex-wrap">
                        <div className="flex items-center gap-2">
                           <span className="text-xs text-gray-500 font-medium">TopK（条数）</span>
                           <input type="number" className={`${inputClass} w-16 !py-1`} placeholder="8" value={testForm.topK}
                             onChange={(e) => setTestForm(v => ({ ...v, topK: Math.max(1, Math.min(50, Math.trunc(Number(e.target.value)))) }))} />
                        </div>
                        <div className="flex items-center gap-2 text-xs text-gray-500 font-medium">
                           <span>候选数</span>
                           <input type="number" className="border-b border-gray-300 bg-transparent w-20 focus:outline-none focus:border-blue-500 text-center py-1"
                             placeholder="默认" value={testForm.numCandidates}
                             onChange={(e) => setTestForm(v => ({ ...v, numCandidates: e.target.value ? (safeNumber(e.target.value) ?? '') : '' }))} />
                        </div>
                        <div className="flex items-center gap-2 text-xs text-gray-500 font-medium">
                           <span>模型</span>
                           <input className="border-b border-gray-300 bg-transparent w-40 focus:outline-none focus:border-blue-500 py-1"
                             placeholder="可选" value={testForm.embeddingModel}
                             onChange={(e) => setTestForm(v => ({ ...v, embeddingModel: e.target.value }))} />
                        </div>
                        <div className="flex items-center gap-2 text-xs text-gray-500 font-medium">
                           <span>版块 ID</span>
                           <input className="border-b border-gray-300 bg-transparent w-16 focus:outline-none focus:border-blue-500 text-center py-1"
                             placeholder="全部" value={testForm.boardId}
                             onChange={(e) => setTestForm(v => ({ ...v, boardId: e.target.value ? (safeNumber(e.target.value) ?? '') : '' }))} />
                        </div>
                     </div>
                     
                     <button className={btnPrimaryClass} disabled={loading || !testForm.queryText.trim()} onClick={() => void onTestQuery()}>
                       搜索
                     </button>
                   </div>
                 </div>

                 {testResult && (
                    <div className="mt-3 space-y-2 max-h-[300px] overflow-y-auto pr-1 custom-scrollbar">
                       {(testResult.hits ?? []).length === 0 ? <div className="text-sm text-gray-400 italic">无匹配结果</div> : (
                         (testResult.hits ?? []).map((h, idx) => (
                           <div key={`${h.docId ?? idx}`} className="p-3 rounded border border-gray-200 bg-white shadow-sm text-sm hover:border-blue-300 transition-colors">
                             <div className="flex justify-between items-start mb-1">
                               <span className="font-medium text-blue-700 truncate max-w-[70%]">{h.title || '无标题'}</span>
                               <span className="font-mono text-orange-500 bg-orange-50 px-1 rounded">{(h.score ?? 0).toFixed(3)}</span>
                             </div>
                             <div className="text-gray-600 line-clamp-2 leading-relaxed" title={h.contentTextPreview ?? undefined}>{h.contentTextPreview}</div>
                             <div className="mt-1 flex gap-2 text-xs text-gray-400 font-mono">
                               <span>帖子ID：{h.postId}</span>
                               <span>分块：{h.chunkIndex}</span>
                             </div>
                           </div>
                         ))
                       )}
                    </div>
                 )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 3. 历史记录 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="px-4 py-2 border-b border-gray-100 flex items-center justify-between bg-gray-50/50 cursor-pointer hover:bg-gray-100 transition-colors"
             onClick={() => void loadHistory()}>
          <div className="text-sm font-semibold text-gray-500 uppercase tracking-wider">操作审计日志 ({historyItems.length})</div>
          <div className="text-xs text-gray-400">{historyLoading ? '刷新中...' : '点击刷新'}</div>
        </div>
        {historyError && <div className="px-4 py-2 text-sm text-red-600 bg-red-50">{historyError}</div>}
        {historyItems.length > 0 && (
          <div className="max-h-[200px] overflow-y-auto">
            <table className="min-w-full text-sm divide-y divide-gray-100">
              <tbody className="divide-y divide-gray-50 bg-white">
                {historyItems.map((it) => (
                  <tr key={it.id} className="hover:bg-gray-50">
                    <td className="py-2 px-4 text-gray-400 whitespace-nowrap w-32 font-mono">{fmtDateTime(it.createdAt)}</td>
                    <td className="py-2 px-4 text-blue-600 font-mono w-40 truncate" title={it.action}>{it.action?.replace('RETRIEVAL_', '')}</td>
                    <td className="py-2 px-4 text-gray-600 truncate max-w-xs" title={String(it.result)}>{it.result ?? '-'}</td>
                    <td className="py-2 px-4 text-gray-400 text-right">{it.actorName ?? it.actorId}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default VectorIndexForm;
