import React from 'react';
import type {
  HybridRerankTestResponse,
  HybridRetrievalConfigDTO,
  HybridRetrievalTestResponse,
} from '../../../../services/retrievalHybridService';

type UiClasses = {
  inputClass: string;
  btnPrimaryClass: string;
  btnSecondaryClass: string;
};

type RerankDatasetOption = { key: string; name: string };

type Props = {
  ui: UiClasses;
  loading: boolean;
  config: HybridRetrievalConfigDTO;

  rerankDatasets: RerankDatasetOption[];
  rerankDatasetKey: string;
  setRerankDatasetKey: (v: string) => void;
  rerankQuery: string;
  setRerankQuery: (v: string) => void;
  rerankTopN: number | '';
  setRerankTopN: (v: number | '') => void;
  rerankDocLimit: number | '';
  setRerankDocLimit: (v: number | '') => void;
  rerankDebug: boolean;
  setRerankDebug: (v: boolean) => void;
  rerankDocsJson: string;
  setRerankDocsJson: (v: string) => void;
  rerankJsonError: string | null;
  rerankResult: HybridRerankTestResponse | null;
  setRerankResult: (v: HybridRerankTestResponse | null) => void;
  onTestRerank: () => void;
  onRestoreRerankDataset: () => void;

  testQuery: string;
  setTestQuery: (v: string) => void;
  testBoardId: number | '';
  setTestBoardId: (v: number | '') => void;
  testDebug: boolean;
  setTestDebug: (v: boolean) => void;
  testResult: HybridRetrievalTestResponse | null;
  setTestResult: (v: HybridRetrievalTestResponse | null) => void;
  onTest: () => void;
};

const HybridTestSection: React.FC<Props> = ({
  ui,
  loading,
  config,
  rerankDatasets,
  rerankDatasetKey,
  setRerankDatasetKey,
  rerankQuery,
  setRerankQuery,
  rerankTopN,
  setRerankTopN,
  rerankDocLimit,
  setRerankDocLimit,
  rerankDebug,
  setRerankDebug,
  rerankDocsJson,
  setRerankDocsJson,
  rerankJsonError,
  rerankResult,
  setRerankResult,
  onTestRerank,
  onRestoreRerankDataset,
  testQuery,
  setTestQuery,
  testBoardId,
  setTestBoardId,
  testDebug,
  setTestDebug,
  testResult,
  setTestResult,
  onTest,
}) => {
  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="rounded border border-gray-200 p-3 space-y-3">
          <div className="font-medium">重排模型测试（仅重排，不依赖数据库）</div>
          <div className="text-xs text-gray-500">使用当前配置的重排模型：{(config.rerankModel ?? '—').toString()}</div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
            <div className="md:col-span-2">
              <div className="text-xs text-gray-500 mb-1">测试集合</div>
              <select className={ui.inputClass} value={rerankDatasetKey} onChange={e => setRerankDatasetKey(e.target.value)}>
                {rerankDatasets.map(d => (
                  <option key={d.key} value={d.key}>
                    {d.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">Top N</div>
              <input
                className={ui.inputClass}
                value={rerankTopN}
                onChange={e => setRerankTopN(e.target.value === '' ? '' : Number(e.target.value))}
                placeholder="例如 10"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
            <div className="md:col-span-2">
              <div className="text-xs text-gray-500 mb-1">查询</div>
              <input className={ui.inputClass} value={rerankQuery} onChange={e => setRerankQuery(e.target.value)} placeholder="输入重排 query…" />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">发送文档数</div>
              <input
                className={ui.inputClass}
                value={rerankDocLimit}
                onChange={e => setRerankDocLimit(e.target.value === '' ? '' : Number(e.target.value))}
                placeholder="例如 12"
              />
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={rerankDebug} onChange={e => setRerankDebug(e.target.checked)} />
            返回 调试信息（候选数量/预算截断）
          </label>

          <div>
            <div className="text-xs text-gray-500 mb-1">documents（JSON 数组，支持 docId/title/text）</div>
            <textarea className={`${ui.inputClass} font-mono h-60`} value={rerankDocsJson} onChange={e => setRerankDocsJson(e.target.value)} spellCheck={false} />
            {rerankJsonError && <div className="text-red-600 text-sm mt-1">JSON 错误：{rerankJsonError}</div>}
          </div>

          <div className="flex items-center gap-2">
            <button className={ui.btnPrimaryClass} onClick={onTestRerank} disabled={loading || !rerankQuery.trim()}>
              开始重排测试
            </button>
            <button className={ui.btnSecondaryClass} onClick={() => setRerankResult(null)} disabled={loading}>
              清空结果
            </button>
            <button className={ui.btnSecondaryClass} onClick={onRestoreRerankDataset} disabled={loading}>
              还原测试数据
            </button>
          </div>

          {rerankResult && (
            <div className="space-y-2 text-sm">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                <div className="rounded bg-gray-50 border px-2 py-1">耗时: {rerankResult.latencyMs ?? '—'} 毫秒</div>
                <div className="rounded bg-gray-50 border px-2 py-1">Provider: {rerankResult.usedProviderId ?? '—'}</div>
                <div className="rounded bg-gray-50 border px-2 py-1">Model: {rerankResult.usedModel ?? '—'}</div>
                <div className="rounded bg-gray-50 border px-2 py-1">Tokens: {rerankResult.totalTokens ?? '—'}</div>
              </div>
              {rerankResult.errorMessage && <div className="text-red-600">错误：{rerankResult.errorMessage}</div>}

              <details className="rounded border border-gray-200 p-2" open>
                <summary className="cursor-pointer font-medium">重排结果（按相关性）</summary>
                <div className="mt-2 space-y-2">
                  {(rerankResult.results ?? []).map((h, idx) => (
                    <div key={`${h.docId ?? h.index ?? idx}`} className="rounded border border-gray-100 p-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="font-medium">#{idx + 1}</div>
                        <div className="text-gray-600">docId: {h.docId ?? '—'}</div>
                        <div className="text-gray-600">index: {h.index ?? '—'}</div>
                        <div className="text-gray-600">score: {h.relevanceScore ?? '—'}</div>
                      </div>
                      {h.title && <div className="mt-1">{h.title}</div>}
                      {h.text && <div className="mt-1 text-xs text-gray-600 line-clamp-4">{h.text}</div>}
                    </div>
                  ))}
                  {(rerankResult.results ?? []).length === 0 && <div className="text-gray-500">无结果</div>}
                </div>
              </details>

              <details className="rounded border border-gray-200 p-2">
                <summary className="cursor-pointer font-medium">原始输出（JSON）</summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(rerankResult ?? {}, null, 2)}</pre>
              </details>
              {rerankResult.debugInfo && (
                <details className="rounded border border-gray-200 p-2">
                  <summary className="cursor-pointer font-medium">调试信息</summary>
                  <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(rerankResult.debugInfo ?? {}, null, 2)}</pre>
                </details>
              )}
            </div>
          )}
        </div>

        <div className="rounded border border-gray-200 p-3 space-y-3">
          <div className="font-medium">测试（跑通验证）</div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
            <div className="md:col-span-2">
              <div className="text-xs text-gray-500 mb-1">查询</div>
              <input className={ui.inputClass} value={testQuery} onChange={e => setTestQuery(e.target.value)} placeholder="输入测试问题…" />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">板块编号（可选）</div>
              <input
                className={ui.inputClass}
                value={testBoardId}
                onChange={e => setTestBoardId(e.target.value === '' ? '' : Number(e.target.value))}
                placeholder="例如 1"
              />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={testDebug} onChange={e => setTestDebug(e.target.checked)} />
            返回 调试信息（耗时/错误）
          </label>
          <div className="flex items-center gap-2">
            <button className={ui.btnPrimaryClass} onClick={onTest} disabled={loading || !testQuery.trim()}>
              开始测试
            </button>
            <button className={ui.btnSecondaryClass} onClick={() => setTestResult(null)} disabled={loading}>
              清空结果
            </button>
          </div>

          {testResult && (
            <div className="space-y-2 text-sm">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                <div className="rounded bg-gray-50 border px-2 py-1">BM25: {testResult.bm25LatencyMs ?? '—'} 毫秒</div>
                <div className="rounded bg-gray-50 border px-2 py-1">向量: {testResult.vecLatencyMs ?? '—'} 毫秒</div>
                <div className="rounded bg-gray-50 border px-2 py-1">融合: {testResult.fuseLatencyMs ?? '—'} 毫秒</div>
                <div className="rounded bg-gray-50 border px-2 py-1">重排: {testResult.rerankLatencyMs ?? '—'} 毫秒</div>
              </div>
              {testResult.bm25Error && <div className="text-red-600">BM25 错误：{testResult.bm25Error}</div>}
              {testResult.vecError && <div className="text-red-600">向量 错误：{testResult.vecError}</div>}
              {testResult.rerankError && <div className="text-red-600">重排 错误：{testResult.rerankError}</div>}

              <details className="rounded border border-gray-200 p-2" open>
                <summary className="cursor-pointer font-medium">最终命中（最终命中）</summary>
                <div className="mt-2 space-y-2">
                  {(testResult.finalHits ?? []).map((h, idx) => (
                    <div key={`${h.docId ?? idx}`} className="rounded border border-gray-100 p-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="font-medium">#{idx + 1}</div>
                        <div className="text-gray-600">文档编号: {h.docId ?? '—'}</div>
                        <div className="text-gray-600">帖子编号: {h.postId ?? '—'}</div>
                        <div className="text-gray-600">得分: {h.score ?? '—'}</div>
                        {h.fusedScore != null && <div className="text-gray-600">融合得分: {h.fusedScore}</div>}
                        {h.rerankRank != null && <div className="text-gray-600">重排排名: {h.rerankRank}</div>}
                      </div>
                      {h.title && <div className="mt-1">{h.title}</div>}
                      {h.contentText && <div className="mt-1 text-xs text-gray-600 line-clamp-3">{h.contentText}</div>}
                    </div>
                  ))}
                </div>
              </details>

              <details className="rounded border border-gray-200 p-2">
                <summary className="cursor-pointer font-medium">BM25 命中（BM25命中）</summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.bm25Hits ?? [], null, 2)}</pre>
              </details>
              <details className="rounded border border-gray-200 p-2">
                <summary className="cursor-pointer font-medium">向量命中（向量命中）</summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.vecHits ?? [], null, 2)}</pre>
              </details>
              <details className="rounded border border-gray-200 p-2">
                <summary className="cursor-pointer font-medium">融合命中（融合命中）</summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.fusedHits ?? [], null, 2)}</pre>
              </details>
              <details className="rounded border border-gray-200 p-2">
                <summary className="cursor-pointer font-medium">重排输出（重排输出）</summary>
                <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.rerankHits ?? [], null, 2)}</pre>
              </details>
              {testResult.debugInfo && (
                <details className="rounded border border-gray-200 p-2">
                  <summary className="cursor-pointer font-medium">调试信息</summary>
                  <pre className="mt-2 whitespace-pre-wrap text-xs">{JSON.stringify(testResult.debugInfo ?? {}, null, 2)}</pre>
                </details>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default HybridTestSection;

