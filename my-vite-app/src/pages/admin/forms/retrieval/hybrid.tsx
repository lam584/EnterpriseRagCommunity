import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import {
  adminGetHybridRetrievalConfig,
  adminListHybridRetrievalEvents,
  adminListHybridRetrievalHits,
  adminTestHybridRerank,
  adminTestHybridRetrieval,
  adminUpdateHybridRetrievalConfig,
  type HybridRerankTestResponse,
  type HybridRetrievalConfigDTO,
  type HybridRetrievalTestResponse,
  type RetrievalEventLogDTO,
  type RetrievalHitLogDTO,
} from '../../../../services/retrievalHybridService';
import HybridLogsSection from './hybridLogs';
import HybridTestSection from './hybridTest';

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

type RerankTestDoc = { docId: string; title: string; text: string };
type RerankTestDataset = { key: string; name: string; defaultQuery: string; docs: RerankTestDoc[] };

const RERANK_TEST_DATASETS: RerankTestDataset[] = [
  {
    key: 'cors-cookie',
    name: '跨域 + Cookie（前后端联调）',
    defaultQuery: '如何在 Spring Security / Spring Boot 里配置跨域，让前端 fetch 能携带 cookie？',
    docs: [
      {
        docId: 'cors-01',
        title: 'Spring Security 跨域（CORS）允许携带 Cookie',
        text: '需要同时满足：后端 CORS 配置 allowCredentials=true，且 Access-Control-Allow-Origin 不能是 *，必须是具体 origin。前端 fetch/axios 要带 credentials: include。若开启 CSRF，则需要携带 XSRF token 或对相关接口关闭 CSRF。',
      },
      {
        docId: 'cors-02',
        title: 'Vite 开发环境跨域：代理 vs 真实跨域',
        text: '开发期可以用 Vite proxy 避免跨域；但生产环境仍要正确配置 CORS。若后端使用 cookie session，需要设置 SameSite=None; Secure（HTTPS）并确认 domain/path。',
      },
      {
        docId: 'cors-03',
        title: 'Fetch credentials/include 的常见坑',
        text: '浏览器默认不会在跨域请求里带 cookie，需要 fetch(url, { credentials: \"include\" })；同时后端要回 Access-Control-Allow-Credentials: true，且明确允许的 Origin。',
      },
      {
        docId: 'cors-04',
        title: 'Spring WebMvcConfigurer 配置 CORS 示例',
        text: '可以在 addCorsMappings 中配置 allowedOrigins、allowedMethods、allowedHeaders，并设置 allowCredentials(true)。注意如果你使用 Spring Security，还需要在 SecurityFilterChain 中启用 cors() 并提供 CorsConfigurationSource。',
      },
      {
        docId: 'cors-05',
        title: '关于 CSRF：Cookie 登录场景为什么经常失败',
        text: 'CSRF 防护常用双提交 cookie 或 header token。前端登录后需要读取 XSRF cookie 并在写操作请求头携带 X-XSRF-TOKEN。若是纯 API Token 认证可考虑禁用 CSRF。',
      },
      {
        docId: 'cors-06',
        title: '如何定位跨域问题：从响应头入手',
        text: '先看浏览器 Network：预检 OPTIONS 是否 2xx；响应里是否有 Access-Control-Allow-Origin / Allow-Credentials；是否因为 Vary: Origin 或缓存导致错配；是否被反向代理改写了响应头。',
      },
      {
        docId: 'cors-07',
        title: '无关：RRF 融合策略简介',
        text: 'RRF（Reciprocal Rank Fusion）通过 1/(k+rank) 融合多个召回列表的排名，不需要分数同尺度。常用于 BM25 + 向量召回的融合。',
      },
      {
        docId: 'cors-08',
        title: '无关：OpenAI 兼容 rerank 端点差异',
        text: '有的平台提供 /v1/rerank（responses 风格），也有 /compatible-api/v1/reranks（DashScope 兼容）。前者可能需要不同的 JSON 格式与字段。',
      },
      { docId: 'cors-09', title: '无关：如何做番茄炒蛋', text: '番茄切块炒出汁后下蛋回锅，最后加盐糖调味。与跨域无关。' },
      { docId: 'cors-10', title: '无关：Git rebase 与 merge 区别', text: 'rebase 会重写提交历史，merge 保留分支结构。与跨域无关。' },
      {
        docId: 'cors-11',
        title: '反向代理层的 CORS：Nginx/Ingress 常见做法',
        text: '如果在网关层做 CORS，要确保对 OPTIONS 预检直接返回并带全套头；同时不要和应用层重复/冲突。Cookie 场景也要确保 Set-Cookie 不被过滤。',
      },
      { docId: 'cors-12', title: '无关：Java 线程池参数怎么选', text: '根据任务类型（CPU/IO）、队列长度、最大并发等进行配置。与跨域无关。' },
      { docId: 'cors-13', title: '同源策略小结：什么算跨域', text: 'scheme/host/port 任意一个不同就算跨域。localhost:5173 与 127.0.0.1:8080 属于跨域。预检触发与自定义 header、非简单方法有关。' },
      {
        docId: 'cors-14',
        title: 'Cookie 的 SameSite/Secure/Domain/Path 与跨站请求',
        text: '跨站带 cookie 通常需要 SameSite=None 且 Secure；在 HTTP 环境下浏览器可能拒绝。Domain 设置不当也会导致 cookie 不回传。',
      },
      {
        docId: 'cors-15',
        title: '排查 “看起来设置了 allowCredentials 但还是不行”',
        text: '确认响应头不是 Access-Control-Allow-Origin: *；确认浏览器没有把请求当作不可信上下文（HTTP + SameSite=None）；确认后端没有在异常路径漏掉 CORS 头。',
      },
      { docId: 'cors-16', title: '无关：SQL 索引为什么会失效', text: '函数/隐式类型转换/不符合最左前缀等会导致索引失效。与跨域无关。' },
      { docId: 'cors-17', title: '前端常见：axios withCredentials 与 fetch credentials', text: 'axios 需要 withCredentials: true；fetch 需要 credentials: \"include\"。两端都要配合后端的 allowCredentials 与 origin 白名单。' },
      { docId: 'cors-18', title: '无关：向量检索里 cosine 与 dot 的区别', text: 'cosine 关注方向相似度，dot 还受向量模长影响。与跨域无关。' },
      { docId: 'cors-19', title: '无关：HTTP 缓存 ETag 的原理', text: 'ETag 用于协商缓存，If-None-Match 与 304。与跨域无关。' },
      { docId: 'cors-20', title: '无关：Windows 端口被占用怎么排查', text: '可以用 netstat/PowerShell 查监听进程，再定位 PID。与跨域无关。' },
      { docId: 'cors-21', title: '安全提示：不要在日志里打印 Cookie/Token', text: '排查跨域时经常打印请求头，但注意不要把 Cookie、Authorization 等敏感信息写入日志或前端控制台。' },
      { docId: 'cors-22', title: '无关：如何提高写作表达', text: '多读多写，结构化表达。与跨域无关。' },
      { docId: 'cors-23', title: 'Spring Boot 设置 CORS 的两种方式', text: '方式一：WebMvcConfigurer；方式二：自定义 CorsConfigurationSource 并在 SecurityFilterChain 里启用 cors。不要忘了 OPTIONS 方法。' },
      { docId: 'cors-24', title: '无关：如何做一杯手冲咖啡', text: '控制水温、粉水比、研磨度与注水方式。与跨域无关。' },
    ],
  },
  {
    key: 'rerank-general',
    name: 'Rerank 基础（端点/参数/输出）',
    defaultQuery: 'rerank 接口一般需要哪些字段？输出结果通常长什么样？',
    docs: [
      { docId: 'rr-01', title: 'Rerank 输入', text: '通常包含 query 与 documents（候选文本列表），可选 top_n、return_documents、instruction 等。' },
      { docId: 'rr-02', title: 'Rerank 输出', text: '通常返回按相关性排序的结果列表，包含 index 与 relevance_score；index 对应输入 documents 的下标。' },
      { docId: 'rr-03', title: '端点差异', text: '有的平台提供 /compatible-api/v1/reranks，也有 /v1/rerank（responses 风格）；需要根据提供方文档选用。' },
      { docId: 'rr-04', title: '无关：Embedding 维度不匹配', text: '向量检索需要保证索引维度与模型输出维度一致。' },
      { docId: 'rr-05', title: '无关：BM25 的优势', text: 'BM25 对关键词匹配强，尤其在短查询上表现稳定。' },
      { docId: 'rr-06', title: '无关：如何做缓存', text: '缓存需要考虑 TTL、一致性与淘汰策略。' },
    ],
  },
];

function getRerankDataset(key: string): RerankTestDataset {
  return RERANK_TEST_DATASETS.find(d => d.key === key) ?? RERANK_TEST_DATASETS[0];
}

function normalizeRerankDocs(raw: unknown): { docs: RerankTestDoc[]; error: string | null } {
  if (!Array.isArray(raw)) return { docs: [], error: 'JSON 必须是数组' };
  const out: RerankTestDoc[] = [];
  for (let i = 0; i < raw.length; i++) {
    const v = raw[i] as unknown;
    if (typeof v === 'string') {
      const t = v.trim();
      if (!t) continue;
      out.push({ docId: `doc-${i + 1}`, title: '', text: t });
      continue;
    }
    if (!v || typeof v !== 'object') continue;
    const obj = v as Record<string, unknown>;
    const docId = typeof obj.docId === 'string' && obj.docId.trim() ? obj.docId.trim() : `doc-${i + 1}`;
    const title = typeof obj.title === 'string' ? obj.title : '';
    const text =
      typeof obj.text === 'string'
        ? obj.text
        : typeof obj.contentText === 'string'
          ? obj.contentText
          : typeof obj.content === 'string'
            ? obj.content
            : '';
    if (!String(text).trim()) continue;
    out.push({ docId, title, text });
  }
  if (out.length === 0) return { docs: [], error: '没有可用的 documents（至少需要 1 条非空 text）' };
  return { docs: out, error: null };
}

const HybridSearchForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_retrieval_hybrid', 'access');
  const canWrite = hasPerm('admin_retrieval_hybrid', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<HybridRetrievalConfigDTO>({ ...DEFAULT_CFG });
  const [committedConfig, setCommittedConfig] = useState<HybridRetrievalConfigDTO>({ ...DEFAULT_CFG });
  const [configLoaded, setConfigLoaded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);

  const hasUnsavedChanges = useMemo(() => JSON.stringify(config) !== JSON.stringify(committedConfig), [config, committedConfig]);

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetHybridRetrievalConfig();
      const next = { ...DEFAULT_CFG, ...(cfg ?? {}) };
      setConfig(next);
      setCommittedConfig(next);
      setEditing(false);
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

  useEffect(() => {
    if (!canAccess) return;
    let cancelled = false;
    (async () => {
      try {
        const cfg = await adminGetAiProvidersConfig();
        if (cancelled) return;
        setProviders((cfg.providers ?? []).filter(Boolean) as AiProviderDTO[]);
        setActiveProviderId(cfg.activeProviderId ?? '');
      } catch {
        if (cancelled) return;
        setProviders([]);
        setActiveProviderId('');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [canAccess]);

  useEffect(() => {
    if (!canAccess) return;
    let cancelled = false;
    (async () => {
      try {
        const opts = await getAiChatOptions();
        if (cancelled) return;
        setChatProviders((opts.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[]);
      } catch {
        if (cancelled) return;
        setChatProviders([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [canAccess]);

  const onSave = useCallback(async () => {
    if (!canWrite || !editing) return;
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const saved = await adminUpdateHybridRetrievalConfig(config);
      const next = { ...DEFAULT_CFG, ...(saved ?? {}) };
      setConfig(next);
      setCommittedConfig(next);
      setEditing(false);
      setMessage('配置已保存');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setLoading(false);
    }
  }, [canWrite, config, editing]);

  const [testQuery, setTestQuery] = useState('');
  const [testBoardId, setTestBoardId] = useState<number | ''>('');
  const [testDebug, setTestDebug] = useState(false);
  const [testResult, setTestResult] = useState<HybridRetrievalTestResponse | null>(null);

  const ds0 = getRerankDataset(RERANK_TEST_DATASETS[0].key);
  const [rerankDatasetKey, setRerankDatasetKey] = useState<string>(ds0.key);
  const [rerankQuery, setRerankQuery] = useState<string>(ds0.defaultQuery);
  const [rerankTopN, setRerankTopN] = useState<number | ''>(10);
  const [rerankDocLimit, setRerankDocLimit] = useState<number | ''>(12);
  const [rerankDebug, setRerankDebug] = useState(false);
  const [rerankDocsJson, setRerankDocsJson] = useState<string>(() => JSON.stringify(ds0.docs, null, 2));
  const [rerankJsonError, setRerankJsonError] = useState<string | null>(null);
  const [rerankResult, setRerankResult] = useState<HybridRerankTestResponse | null>(null);

  useEffect(() => {
    const ds = getRerankDataset(rerankDatasetKey);
    setRerankQuery(ds.defaultQuery);
    setRerankDocsJson(JSON.stringify(ds.docs, null, 2));
    setRerankJsonError(null);
    setRerankResult(null);
  }, [rerankDatasetKey]);

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

  const onTestRerank = useCallback(async () => {
    setError(null);
    setMessage(null);
    setRerankJsonError(null);
    setRerankResult(null);

    let raw: unknown;
    try {
      raw = JSON.parse(rerankDocsJson);
    } catch (e) {
      setRerankJsonError(e instanceof Error ? e.message : 'JSON 解析失败');
      return;
    }

    const normalized = normalizeRerankDocs(raw);
    if (normalized.error) {
      setRerankJsonError(normalized.error);
      return;
    }

    const limit = rerankDocLimit === '' ? normalized.docs.length : Math.max(1, Number(rerankDocLimit));
    const docsToSend = normalized.docs.slice(0, Math.min(limit, normalized.docs.length));
    const topN = rerankTopN === '' ? null : Math.max(1, Number(rerankTopN));

    setLoading(true);
    try {
      const res = await adminTestHybridRerank({
        queryText: rerankQuery,
        topN,
        debug: rerankDebug,
        useSavedConfig: false,
        config,
        documents: docsToSend.map(d => ({ docId: d.docId, title: d.title, text: d.text })),
      });
      setRerankResult(res);
      setMessage('重排测试完成');
    } catch (e) {
      setError(e instanceof Error ? e.message : '重排测试失败');
    } finally {
      setLoading(false);
    }
  }, [config, rerankDebug, rerankDocLimit, rerankDocsJson, rerankQuery, rerankTopN]);

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
    <div className="space-y-6">
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
          {!editing ? (
            <button
              className={btnSecondaryClass}
              onClick={() => {
                setEditing(true);
                setError(null);
                setMessage(null);
              }}
              disabled={loading || !canWrite}
            >
              编辑
            </button>
          ) : (
            <>
              <button
                className={btnSecondaryClass}
                onClick={() => {
                  setConfig(committedConfig);
                  setEditing(false);
                  setError(null);
                  setMessage(null);
                }}
                disabled={loading}
              >
                取消
              </button>
              <button className={btnPrimaryClass} onClick={onSave} disabled={loading || !canWrite || !hasUnsavedChanges}>
                保存
              </button>
            </>
          )}
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
              disabled={!canWrite || !editing}
            />
            启用 Hybrid 检索（用于 Chat RAG）
          </label>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">BM25 数量</div>
              <input
                className={inputClass}
                value={config.bm25K ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25K: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="50"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">向量 数量</div>
              <input
                className={inputClass}
                value={config.vecK ?? ''}
                onChange={e => setConfig(v => ({ ...v, vecK: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="50"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最终返回 数量（Hybrid 数量）</div>
              <input
                className={inputClass}
                value={config.hybridK ?? ''}
                onChange={e => setConfig(v => ({ ...v, hybridK: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="30"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">最大 文档 数量</div>
              <input
                className={inputClass}
                value={config.maxDocs ?? ''}
                onChange={e => setConfig(v => ({ ...v, maxDocs: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="500"
              />
            </div>
          </div>
        </div>

        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">BM25 字段权重</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">标题 权重</div>
              <input
                className={inputClass}
                value={config.bm25TitleBoost ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25TitleBoost: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="2.0"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">内容文本 权重</div>
              <input
                className={inputClass}
                value={config.bm25ContentBoost ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25ContentBoost: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="1.0"
              />
            </div>
          </div>

          <div className="font-medium mt-2">融合策略</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">融合模式</div>
              <select
                className={inputClass}
                value={String(config.fusionMode ?? 'RRF')}
                onChange={e => setConfig(v => ({ ...v, fusionMode: e.target.value }))}
                disabled={!canWrite || !editing}
              >
                <option value="RRF">RRF（推荐）</option>
                <option value="LINEAR">线性加权（min-max）</option>
              </select>
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">RRF 参数</div>
              <input
                className={inputClass}
                value={config.rrfK ?? ''}
                onChange={e => setConfig(v => ({ ...v, rrfK: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="60"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">BM25 权重</div>
              <input
                className={inputClass}
                value={config.bm25Weight ?? ''}
                onChange={e => setConfig(v => ({ ...v, bm25Weight: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="1.0"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">向量 权重</div>
              <input
                className={inputClass}
                value={config.vecWeight ?? ''}
                onChange={e => setConfig(v => ({ ...v, vecWeight: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
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
              disabled={!canWrite || !editing}
            />
            启用重排
          </label>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div className="md:col-span-2">
              <ProviderModelSelect
                providers={providers}
                activeProviderId={activeProviderId}
                chatProviders={chatProviders}
                mode="chat"
                includeProviderOnlyOptions={false}
                providerId=""
                model={config.rerankModel ?? ''}
                disabled={!canWrite || !editing}
                selectClassName={inputClass}
                onChange={(next) => setConfig((v) => ({ ...v, rerankModel: next.model }))}
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">重排 数量</div>
              <input
                className={inputClass}
                value={config.rerankK ?? ''}
                onChange={e => setConfig(v => ({ ...v, rerankK: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="30"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">温度</div>
              <input
                className={inputClass}
                value={config.rerankTemperature ?? ''}
                onChange={e => setConfig(v => ({ ...v, rerankTemperature: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="0.0"
              />
            </div>
          </div>
        </div>

        <div className="space-y-3 rounded border border-gray-200 p-3">
          <div className="font-medium">输入限制</div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            <div>
              <div className="text-xs text-gray-500 mb-1">单 文档 最大输入 词元</div>
              <input
                className={inputClass}
                value={config.perDocMaxTokens ?? ''}
                onChange={e => setConfig(v => ({ ...v, perDocMaxTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="4000"
              />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">总最大输入 词元</div>
              <input
                className={inputClass}
                value={config.maxInputTokens ?? ''}
                onChange={e => setConfig(v => ({ ...v, maxInputTokens: safeNumber(e.target.value) }))}
                disabled={!canWrite || !editing}
                placeholder="30000"
              />
            </div>
          </div>
        </div>
      </div>
      </div>

      <HybridTestSection
        ui={{ inputClass, btnPrimaryClass, btnSecondaryClass }}
        loading={loading}
        config={config}
        rerankDatasets={RERANK_TEST_DATASETS}
        rerankDatasetKey={rerankDatasetKey}
        setRerankDatasetKey={setRerankDatasetKey}
        rerankQuery={rerankQuery}
        setRerankQuery={setRerankQuery}
        rerankTopN={rerankTopN}
        setRerankTopN={setRerankTopN}
        rerankDocLimit={rerankDocLimit}
        setRerankDocLimit={setRerankDocLimit}
        rerankDebug={rerankDebug}
        setRerankDebug={setRerankDebug}
        rerankDocsJson={rerankDocsJson}
        setRerankDocsJson={setRerankDocsJson}
        rerankJsonError={rerankJsonError}
        rerankResult={rerankResult}
        setRerankResult={setRerankResult}
        onTestRerank={onTestRerank}
        onRestoreRerankDataset={() => {
          const ds = getRerankDataset(rerankDatasetKey);
          setRerankDocsJson(JSON.stringify(ds.docs, null, 2));
          setRerankJsonError(null);
        }}
        testQuery={testQuery}
        setTestQuery={setTestQuery}
        testBoardId={testBoardId}
        setTestBoardId={setTestBoardId}
        testDebug={testDebug}
        setTestDebug={setTestDebug}
        testResult={testResult}
        setTestResult={setTestResult}
        onTest={onTest}
      />

      <HybridLogsSection
        ui={{ btnSecondaryClass }}
        loading={loading}
        logs={logs}
        logsPage={logsPage}
        logsTotal={logsTotal}
        logsTotalPages={logsTotalPages}
        selectedEventId={selectedEventId}
        selectedHits={selectedHits}
        fmtDateTime={fmtDateTime}
        onRefresh={loadLogs}
        onSelectEvent={onSelectEvent}
        onPrevPage={() => setLogsPage(p => Math.max(0, p - 1))}
        onNextPage={() => setLogsPage(p => Math.min(logsTotalPages - 1, p + 1))}
      />
    </div>
  );
};

export default HybridSearchForm;
