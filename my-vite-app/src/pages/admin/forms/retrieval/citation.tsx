import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import {
  adminGetCitationConfig,
  adminTestCitation,
  adminUpdateCitationConfig,
  type CitationConfigDTO,
  type CitationTestItem,
  type CitationTestResponse,
} from '../../../../services/retrievalCitationService';

const inputClass =
  'block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors duration-200';
const btnPrimaryClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnSecondaryClass =
  'inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';

function safeNumber(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'string' && v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

const DEFAULT_CFG: CitationConfigDTO = {
  enabled: true,
  citationMode: 'MODEL_INLINE',
  instructionTemplate: '回答时在引用资料的句子末尾使用 [n] 引用编号（n 对应参考资料序号）；如资料不足请明确说明。',
  sourcesTitle: '来源',
  maxSources: 6,
  includeUrl: true,
  includeScore: false,
  includeTitle: true,
  includePostId: false,
  includeChunkIndex: false,
  postUrlTemplate: '/portal/posts/detail/{postId}',
};

const CitationForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_retrieval_citation', 'access');
  const canWrite = hasPerm('admin_retrieval_citation', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<CitationConfigDTO>({ ...DEFAULT_CFG });
  const [configLoaded, setConfigLoaded] = useState(false);

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetCitationConfig();
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
      const saved = await adminUpdateCitationConfig(config);
      setConfig({ ...DEFAULT_CFG, ...(saved ?? {}) });
      setMessage('配置已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setLoading(false);
    }
  }, [config]);

  const [useSavedConfig, setUseSavedConfig] = useState(false);
  const [items, setItems] = useState<CitationTestItem[]>([
    { postId: 1, chunkIndex: 0, score: 0.8, title: '示例标题 A' },
    { postId: 2, chunkIndex: 0, score: 0.7, title: '示例标题 B' },
  ]);
  const [testResult, setTestResult] = useState<CitationTestResponse | null>(null);

  const onTest = useCallback(async () => {
    setError(null);
    setMessage(null);
    setTestResult(null);
    setLoading(true);
    try {
      const res = await adminTestCitation({
        useSavedConfig,
        config: useSavedConfig ? null : config,
        items,
      });
      setTestResult(res);
      setMessage('测试完成');
    } catch (e) {
      setError(e instanceof Error ? e.message : '测试失败');
    } finally {
      setLoading(false);
    }
  }, [config, items, useSavedConfig]);

  const addItem = useCallback(() => {
    setItems((v) => [...v, { postId: null, chunkIndex: null, score: null, title: '' }]);
  }, []);

  const removeItem = useCallback((idx: number) => {
    setItems((v) => v.filter((_, i) => i !== idx));
  }, []);

  const copyText = useCallback(async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setMessage('已复制到剪贴板');
    } catch {
      setError('复制失败（浏览器权限限制）');
    }
  }, []);

  const effectiveSourcesPreview = useMemo(() => String(testResult?.sourcesPreview ?? ''), [testResult?.sourcesPreview]);

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
        <div className="text-red-600 font-medium">无权限访问：引用与来源展示配置</div>
        <div className="text-gray-600 text-sm mt-1">需要 admin_retrieval_citation:access</div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">引用与来源展示配置</h3>
          <div className="text-xs text-gray-500">控制“引用指令”与“来源区”输出格式</div>
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

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="font-medium">基础配置</div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={Boolean(config.enabled)}
            onChange={(e) => setConfig((v) => ({ ...v, enabled: e.target.checked }))}
            disabled={!canWrite}
          />
          启用引用与来源功能
        </label>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          <div>
            <div className="text-xs text-gray-500 mb-1">citationMode</div>
            <select
              className={inputClass}
              value={config.citationMode ?? 'MODEL_INLINE'}
              onChange={(e) => setConfig((v) => ({ ...v, citationMode: e.target.value }))}
              disabled={!canWrite}
            >
              <option value="MODEL_INLINE">MODEL_INLINE（仅要求模型用 [n] 引用）</option>
              <option value="SOURCES_SECTION">SOURCES_SECTION（自动追加“来源区”）</option>
              <option value="BOTH">BOTH（两者都要）</option>
            </select>
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">maxSources</div>
            <input
              className={inputClass}
              value={config.maxSources ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, maxSources: safeNumber(e.target.value) }))}
              disabled={!canWrite}
            />
          </div>
        </div>
        <div>
          <div className="text-xs text-gray-500 mb-1">instructionTemplate（会拼到 system prompt）</div>
          <textarea
            className={inputClass}
            value={config.instructionTemplate ?? ''}
            onChange={(e) => setConfig((v) => ({ ...v, instructionTemplate: e.target.value }))}
            disabled={!canWrite}
            rows={3}
          />
        </div>
      </div>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="font-medium">来源区渲染</div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          <div>
            <div className="text-xs text-gray-500 mb-1">sourcesTitle</div>
            <input
              className={inputClass}
              value={config.sourcesTitle ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, sourcesTitle: e.target.value }))}
              disabled={!canWrite}
            />
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">postUrlTemplate（{`{postId}` }）</div>
            <input
              className={inputClass}
              value={config.postUrlTemplate ?? ''}
              onChange={(e) => setConfig((v) => ({ ...v, postUrlTemplate: e.target.value }))}
              disabled={!canWrite}
            />
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-2">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.includeTitle)}
              onChange={(e) => setConfig((v) => ({ ...v, includeTitle: e.target.checked }))}
              disabled={!canWrite}
            />
            title
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.includeUrl)}
              onChange={(e) => setConfig((v) => ({ ...v, includeUrl: e.target.checked }))}
              disabled={!canWrite}
            />
            url
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.includeScore)}
              onChange={(e) => setConfig((v) => ({ ...v, includeScore: e.target.checked }))}
              disabled={!canWrite}
            />
            score
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.includePostId)}
              onChange={(e) => setConfig((v) => ({ ...v, includePostId: e.target.checked }))}
              disabled={!canWrite}
            />
            postId
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(config.includeChunkIndex)}
              onChange={(e) => setConfig((v) => ({ ...v, includeChunkIndex: e.target.checked }))}
              disabled={!canWrite}
            />
            chunkIndex
          </label>
        </div>
      </div>

      <div className="space-y-3 rounded border border-gray-200 p-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="font-medium">测试预览</div>
            <div className="text-xs text-gray-500">输入若干条来源，预览“来源区”渲染</div>
          </div>
          <div className="flex items-center gap-2">
            <button className={btnSecondaryClass} onClick={addItem} disabled={loading}>
              添加一条
            </button>
            <button className={btnPrimaryClass} onClick={onTest} disabled={loading || !canWrite}>
              运行测试
            </button>
          </div>
        </div>

        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={useSavedConfig} onChange={(e) => setUseSavedConfig(e.target.checked)} />
          使用已保存配置（忽略当前编辑）
        </label>

        <div className="overflow-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="py-1 pr-2">postId</th>
                <th className="py-1 pr-2">chunk</th>
                <th className="py-1 pr-2">score</th>
                <th className="py-1 pr-2">title</th>
                <th className="py-1 pr-2"></th>
              </tr>
            </thead>
            <tbody>
              {items.map((it, idx) => (
                <tr key={idx} className="border-t">
                  <td className="py-1 pr-2">
                    <input
                      className={inputClass}
                      value={it.postId ?? ''}
                      onChange={(e) =>
                        setItems((v) => v.map((x, i) => (i === idx ? { ...x, postId: safeNumber(e.target.value) } : x)))
                      }
                    />
                  </td>
                  <td className="py-1 pr-2">
                    <input
                      className={inputClass}
                      value={it.chunkIndex ?? ''}
                      onChange={(e) =>
                        setItems((v) =>
                          v.map((x, i) => (i === idx ? { ...x, chunkIndex: safeNumber(e.target.value) } : x))
                        )
                      }
                    />
                  </td>
                  <td className="py-1 pr-2">
                    <input
                      className={inputClass}
                      value={it.score ?? ''}
                      onChange={(e) =>
                        setItems((v) => v.map((x, i) => (i === idx ? { ...x, score: safeNumber(e.target.value) } : x)))
                      }
                    />
                  </td>
                  <td className="py-1 pr-2">
                    <input
                      className={inputClass}
                      value={it.title ?? ''}
                      onChange={(e) => setItems((v) => v.map((x, i) => (i === idx ? { ...x, title: e.target.value } : x)))}
                    />
                  </td>
                  <td className="py-1 pr-2 text-right">
                    <button className={btnSecondaryClass} onClick={() => removeItem(idx)} disabled={loading || items.length <= 1}>
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {testResult && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="font-medium">引用指令预览</div>
              <button className={btnSecondaryClass} onClick={() => copyText(String(testResult.instructionPreview ?? ''))}>
                复制
              </button>
            </div>
            <pre className="rounded border border-gray-200 bg-gray-50 p-3 text-xs overflow-auto max-h-52">
              {String(testResult.instructionPreview ?? '')}
            </pre>
            <div className="flex items-center justify-between">
              <div className="font-medium">来源区预览</div>
              <button className={btnSecondaryClass} onClick={() => copyText(effectiveSourcesPreview)} disabled={!effectiveSourcesPreview}>
                复制
              </button>
            </div>
            <pre className="rounded border border-gray-200 bg-gray-50 p-3 text-xs overflow-auto max-h-60">{effectiveSourcesPreview}</pre>
          </div>
        )}
      </div>
    </div>
  );
};

export default CitationForm;
