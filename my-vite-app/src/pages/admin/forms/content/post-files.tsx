import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  adminGetPostFileDetail,
  adminListPostFiles,
  adminReextractPostFile,
  type ExtractedImageItemDTO,
  type PostFileExtractionAdminDetailDTO,
  type PostFileExtractionAdminListItemDTO,
} from '../../../../services/postFilesAdminService';
import { resolveAssetUrl } from '../../../../utils/urlUtils';
import { estimateVisionImageTokens } from '../../../../utils/visionImageTokens';

function fmtTs(ts?: string | null): string {
  if (!ts) return '—';
  const ms = Date.parse(ts);
  if (!Number.isFinite(ms)) return String(ts);
  return new Date(ms).toLocaleString();
}

function fmtNum(v?: number | null): string {
  if (v === undefined || v === null) return '—';
  if (!Number.isFinite(Number(v))) return '—';
  return String(v);
}

function fmtBytes(v?: number | null): string {
  if (v === undefined || v === null) return '—';
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0) return '—';
  if (n < 1024) return `${n} B`;
  const kb = n / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  const gb = mb / 1024;
  return `${gb.toFixed(2)} GB`;
}

const PostFilesForm: React.FC = () => {
  const [postId, setPostId] = useState('');
  const [fileAssetId, setFileAssetId] = useState('');
  const [keyword, setKeyword] = useState('');
  const [extractStatus, setExtractStatus] = useState<string>('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<{ items: PostFileExtractionAdminListItemDTO[]; totalPages: number; totalElements: number } | null>(null);
  const inflightRef = useRef<AbortController | null>(null);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detail, setDetail] = useState<PostFileExtractionAdminDetailDTO | null>(null);

  const [imageTokenTotal, setImageTokenTotal] = useState<number | null>(null);
  const [imageTokenComputing, setImageTokenComputing] = useState(false);
  const [imageTokenErrorCount, setImageTokenErrorCount] = useState(0);
  const imageTokenSeqRef = useRef(0);
  const imageTokenCacheRef = useRef<Map<string, number | null>>(new Map());

  const queryParams = useMemo(() => {
    const pid = postId.trim() ? Number(postId.trim()) : undefined;
    const fid = fileAssetId.trim() ? Number(fileAssetId.trim()) : undefined;
    return {
      page,
      pageSize,
      postId: Number.isFinite(pid as number) ? (pid as number) : undefined,
      fileAssetId: Number.isFinite(fid as number) ? (fid as number) : undefined,
      keyword: keyword.trim() || undefined,
      extractStatus: extractStatus || undefined,
    };
  }, [postId, fileAssetId, keyword, extractStatus, page, pageSize]);

  const fetchList = useCallback(async () => {
    setError(null);
    setLoading(true);
    inflightRef.current?.abort();
    const ac = new AbortController();
    inflightRef.current = ac;
    try {
      const resp = await adminListPostFiles(queryParams);
      if (ac.signal.aborted) return;
      setData({ items: resp.content ?? [], totalPages: resp.totalPages ?? 0, totalElements: resp.totalElements ?? 0 });
    } catch (e) {
      if (ac.signal.aborted) return;
      setError(e instanceof Error ? e.message : String(e));
      setData(null);
    } finally {
      if (!ac.signal.aborted) setLoading(false);
    }
  }, [queryParams]);

  useEffect(() => {
    fetchList();
    return () => inflightRef.current?.abort();
  }, [fetchList]);

  const openDetail = useCallback(async (attachmentId: number) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetailError(null);
    setDetail(null);
    try {
      const d = await adminGetPostFileDetail(attachmentId);
      setDetail(d);
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : String(e));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const doReextract = useCallback(async () => {
    if (!detail?.attachmentId) return;
    setDetailError(null);
    setDetailLoading(true);
    try {
      const d = await adminReextractPostFile(detail.attachmentId);
      setDetail(d);
      void fetchList();
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : String(e));
    } finally {
      setDetailLoading(false);
    }
  }, [detail?.attachmentId, fetchList, detail]);

  const doCopyPreview = useCallback(async () => {
    const text = (detail?.llmInputPreview ?? '').trim();
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
    } catch {}
  }, [detail?.llmInputPreview]);

  const items = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;

  const extractedImages = useMemo((): ExtractedImageItemDTO[] => {
    const list = detail?.extractedImages;
    if (Array.isArray(list)) return list.filter(Boolean) as ExtractedImageItemDTO[];
    const meta = detail?.extractedMetadata as { extractedImages?: unknown } | null | undefined;
    const v = meta?.extractedImages;
    if (Array.isArray(v)) return v.filter(Boolean) as ExtractedImageItemDTO[];
    return [];
  }, [detail]);

  useEffect(() => {
    if (!detailOpen || !detail) {
      setImageTokenTotal(null);
      setImageTokenComputing(false);
      setImageTokenErrorCount(0);
      return;
    }

    if (extractedImages.length === 0) {
      setImageTokenTotal(0);
      setImageTokenComputing(false);
      setImageTokenErrorCount(0);
      return;
    }

    const loadImageSize = (url: string): Promise<{ width: number; height: number }> => new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight });
      img.onerror = () => reject(new Error('load_failed'));
      img.src = url;
    });

    const seq = imageTokenSeqRef.current + 1;
    imageTokenSeqRef.current = seq;
    setImageTokenComputing(true);
    setImageTokenTotal(null);
    setImageTokenErrorCount(0);

    void (async () => {
      const tasks = extractedImages.map(async (img) => {
        const rawUrl = (img?.url ?? '').trim();
        const url = rawUrl ? resolveAssetUrl(rawUrl) : '';
        if (!url) return null;

        const cached = imageTokenCacheRef.current.get(url);
        if (cached !== undefined) return cached;

        const { width, height } = await loadImageSize(url);
        const tokens = estimateVisionImageTokens({ width, height });
        imageTokenCacheRef.current.set(url, tokens);
        return tokens;
      });

      const results = await Promise.allSettled(tasks);
      if (imageTokenSeqRef.current !== seq) return;

      let total = 0;
      let err = 0;
      for (const r of results) {
        if (r.status === 'fulfilled') {
          if (r.value == null) continue;
          if (Number.isFinite(r.value)) total += r.value;
          else err += 1;
        } else {
          err += 1;
        }
      }

      setImageTokenTotal(total);
      setImageTokenErrorCount(err);
      setImageTokenComputing(false);
    })();
  }, [detailOpen, detail, extractedImages]);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex flex-wrap gap-3 items-end">
        <div className="flex flex-col gap-1">
          <div className="text-xs text-gray-600">postId</div>
          <input value={postId} onChange={(e) => { setPostId(e.target.value); setPage(1); }} className="border rounded px-3 py-2 w-40" placeholder="例如 123" />
        </div>
        <div className="flex flex-col gap-1">
          <div className="text-xs text-gray-600">fileAssetId</div>
          <input value={fileAssetId} onChange={(e) => { setFileAssetId(e.target.value); setPage(1); }} className="border rounded px-3 py-2 w-44" placeholder="例如 456" />
        </div>
        <div className="flex flex-col gap-1">
          <div className="text-xs text-gray-600">文件名关键字</div>
          <input value={keyword} onChange={(e) => { setKeyword(e.target.value); setPage(1); }} className="border rounded px-3 py-2 w-64" placeholder="支持 fileName / originalName" />
        </div>
        <div className="flex flex-col gap-1">
          <div className="text-xs text-gray-600">解析状态</div>
          <select value={extractStatus} onChange={(e) => { setExtractStatus(e.target.value); setPage(1); }} className="border rounded px-3 py-2 w-44">
            <option value="">全部</option>
            <option value="NONE">无记录</option>
            <option value="PENDING">PENDING</option>
            <option value="READY">READY</option>
            <option value="FAILED">FAILED</option>
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <div className="text-xs text-gray-600">每页</div>
          <select value={String(pageSize)} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }} className="border rounded px-3 py-2 w-24">
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
            <option value="100">100</option>
          </select>
        </div>
        <button type="button" onClick={fetchList} className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-60" disabled={loading}>
          查询
        </button>
        {loading && <div className="text-sm text-gray-500">加载中…</div>}
        {error && <div className="text-sm text-red-600">{error}</div>}
      </div>

      <div className="rounded border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-700">
        帖子发布后会按向量索引配置自动增量同步附件内容到帖子文件索引。
      </div>

      <div className="overflow-auto border rounded">
        <table className="min-w-[1100px] w-full text-sm">
          <thead className="bg-gray-50 text-gray-700">
            <tr>
              <th className="text-left px-3 py-2">postId</th>
              <th className="text-left px-3 py-2">文件</th>
              <th className="text-left px-3 py-2">格式</th>
              <th className="text-left px-3 py-2">大小</th>
              <th className="text-left px-3 py-2">状态</th>
              <th className="text-right px-3 py-2">耗时(ms)</th>
              <th className="text-right px-3 py-2">tokens</th>
              <th className="text-right px-3 py-2">图片数</th>
              <th className="text-left px-3 py-2">更新时间</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && (
              <tr>
                <td colSpan={9} className="px-3 py-8 text-center text-gray-500">暂无数据</td>
              </tr>
            )}
            {items.map((it) => (
              <tr key={it.attachmentId} className="border-t hover:bg-gray-50 cursor-pointer" onClick={() => openDetail(it.attachmentId)}>
                <td className="px-3 py-2">{it.postId}</td>
                <td className="px-3 py-2">
                  <div className="max-w-[420px] truncate">{it.originalName || it.fileName || '—'}</div>
                  <div className="text-xs text-gray-400">attachmentId={it.attachmentId} fileAssetId={it.fileAssetId ?? '—'}</div>
                </td>
                <td className="px-3 py-2">{it.ext || it.mimeType || '—'}</td>
                <td className="px-3 py-2">{fmtBytes(it.sizeBytes ?? undefined)}</td>
                <td className="px-3 py-2">{it.extractStatus || '—'}</td>
                <td className="px-3 py-2 text-right">{fmtNum(it.parseDurationMs ?? undefined)}</td>
                <td className="px-3 py-2 text-right">{fmtNum(it.textTokenCount ?? undefined)}</td>
                <td className="px-3 py-2 text-right">{fmtNum(it.imageCount ?? undefined)}</td>
                <td className="px-3 py-2">{fmtTs(it.extractionUpdatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between text-sm text-gray-600">
        <div>共 {data?.totalElements ?? 0} 条</div>
        <div className="flex items-center gap-2">
          <button type="button" className="px-3 py-1.5 border rounded disabled:opacity-50" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>上一页</button>
          <div>{page} / {Math.max(1, totalPages || 1)}</div>
          <button type="button" className="px-3 py-1.5 border rounded disabled:opacity-50" disabled={totalPages > 0 ? page >= totalPages : items.length < pageSize} onClick={() => setPage((p) => p + 1)}>下一页</button>
        </div>
      </div>

      {detailOpen && (
        <div className="fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDetailOpen(false)} />
          <div className="absolute right-0 top-0 h-full w-full max-w-[760px] bg-white shadow-xl flex flex-col">
            <div className="px-4 py-3 border-b flex items-center justify-between">
              <div className="min-w-0">
                <div className="font-semibold truncate">{detail?.originalName || detail?.fileName || '解析详情'}</div>
                <div className="text-xs text-gray-500">
                  postId={detail?.postId ?? '—'} attachmentId={detail?.attachmentId ?? '—'} fileAssetId={detail?.fileAssetId ?? '—'}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button type="button" className="px-3 py-1.5 border rounded disabled:opacity-50" disabled={detailLoading} onClick={doCopyPreview}>
                  复制 LLM 预览
                </button>
                <button type="button" className="px-3 py-1.5 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-60" disabled={detailLoading} onClick={doReextract}>
                  重新解析
                </button>
                <button type="button" className="px-3 py-1.5 border rounded" onClick={() => setDetailOpen(false)}>关闭</button>
              </div>
            </div>

            <div className="flex-1 overflow-auto p-4 space-y-4">
              {detailLoading && <div className="text-sm text-gray-500">加载中…</div>}
              {detailError && <div className="text-sm text-red-600">{detailError}</div>}

              {detail && (
                <>
                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <div className="border rounded p-3">
                      <div className="text-xs text-gray-500">解析状态</div>
                      <div className="font-medium">{detail.extractStatus || '—'}</div>
                      {detail.extractionErrorMessage && <div className="text-xs text-red-600 mt-1 break-words">{detail.extractionErrorMessage}</div>}
                    </div>
                    <div className="border rounded p-3">
                      <div className="text-xs text-gray-500">关键指标</div>
                      <div className="mt-1 grid grid-cols-2 gap-2">
                        <div>耗时: {fmtNum(detail.parseDurationMs ?? undefined)} ms</div>
                        <div>文本 tokens: {fmtNum(detail.textTokenCount ?? undefined)}</div>
                        <div>
                          图片 tokens: {imageTokenComputing ? '计算中…' : fmtNum(imageTokenTotal ?? undefined)}
                          {!imageTokenComputing && imageTokenErrorCount > 0 ? `（失败 ${imageTokenErrorCount} 张）` : ''}
                        </div>
                        <div>图片数: {fmtNum(detail.imageCount ?? undefined)}</div>
                        <div>页数: {fmtNum(detail.pages ?? undefined)}</div>
                      </div>
                      <div className="text-xs text-gray-400 mt-1">tokenCountMode={detail.tokenCountMode || '—'}</div>
                    </div>
                  </div>

                  <div className="border rounded">
                    <div className="px-3 py-2 border-b bg-gray-50 text-sm font-medium">抽取文本</div>
                    <pre className="p-3 whitespace-pre-wrap break-words text-sm max-h-[340px] overflow-auto">{detail.extractedText || ''}</pre>
                  </div>

                  <div className="border rounded">
                    <div className="px-3 py-2 border-b bg-gray-50 text-sm font-medium">元数据</div>
                    <pre className="p-3 whitespace-pre-wrap break-words text-xs max-h-[260px] overflow-auto">
                      {detail.extractedMetadataJson
                        ? (() => {
                          try {
                            return JSON.stringify(JSON.parse(detail.extractedMetadataJson), null, 2);
                          } catch {
                            return detail.extractedMetadataJson;
                          }
                        })()
                        : ''}
                    </pre>
                  </div>

                  <div className="border rounded">
                    <div className="px-3 py-2 border-b bg-gray-50 text-sm font-medium">提取到的图片</div>
                    {extractedImages.length === 0 ? (
                      <div className="p-3 text-sm text-gray-500">无</div>
                    ) : (
                      <div className="p-3 grid grid-cols-2 gap-3">
                        {extractedImages.map((img, idx) => {
                          const rawUrl = (img?.url ?? '').trim();
                          const url = rawUrl ? resolveAssetUrl(rawUrl) : '';
                          return (
                            <div key={`${img?.index ?? idx}-${rawUrl}`} className="border rounded p-2">
                              <div className="text-xs text-gray-500 mb-1">
                                {(img?.placeholder ?? `IMAGE_${(img?.index ?? idx + 1)}`) as string}
                              </div>
                              {url ? (
                                <>
                                  <a href={url} target="_blank" rel="noreferrer" className="text-xs text-blue-600 break-all">{rawUrl}</a>
                                  <div className="mt-2 overflow-hidden rounded bg-gray-50">
                                    <img src={url} alt={rawUrl} className="w-full h-auto" />
                                  </div>
                                  <div className="text-xs text-gray-400 mt-1">
                                    {img?.mimeType ? `${img.mimeType} ` : ''}
                                    {img?.sizeBytes ? `${fmtBytes(img.sizeBytes)}` : ''}
                                  </div>
                                </>
                              ) : (
                                <div className="text-sm text-gray-500">无 URL</div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>

                  <div className="border rounded">
                    <div className="px-3 py-2 border-b bg-gray-50 text-sm font-medium">LLM 输入预览</div>
                    <pre className="p-3 whitespace-pre-wrap break-words text-sm max-h-[260px] overflow-auto">{detail.llmInputPreview || ''}</pre>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PostFilesForm;
