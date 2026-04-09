import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import { uploadFile } from '../../../../services/uploadService';
import {
  adminGetImageStorageConfig,
  adminUpdateImageStorageConfig,
  adminGetUploadLogs,
  adminTestUpload,
  adminTestCompress,
  adminDeleteExpiredLogs,
  type ImageStorageConfig,
  type ImageUploadLog,
  type TestCompressResult,
} from '../../../../services/imageStorageAdminService';

const inputClass =
  'block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors duration-200';
const btnPrimaryClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnSecondaryClass =
  'inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnDangerClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-red-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const PREVIEW_ZOOM_MIN = 0.1;
const PREVIEW_ZOOM_MAX = 10;
const PREVIEW_ZOOM_STEP = 0.1;
const BYTE_UNIT_FACTORS = {
  KB: 1024,
  MB: 1024 * 1024,
} as const;

type ByteUnit = keyof typeof BYTE_UNIT_FACTORS;

type StorageMode = 'LOCAL' | 'DASHSCOPE_TEMP' | 'ALIYUN_OSS';

const MODE_LABELS: Record<StorageMode, string> = {
  LOCAL: '本地 URL（需公网 IP）',
  DASHSCOPE_TEMP: '百炼临时存储（48h 有效期）',
  ALIYUN_OSS: '阿里云 OSS',
};

function fmtDateTime(v: unknown): string {
  if (!v) return '—';
  const s = String(v);
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  return d.toLocaleString();
}

function clampPreviewZoomWithMax(value: number, maxZoom: number): number {
  const safeMax = Number.isFinite(maxZoom) ? Math.max(PREVIEW_ZOOM_MIN, maxZoom) : PREVIEW_ZOOM_MAX;
  return Math.min(safeMax, Math.max(PREVIEW_ZOOM_MIN, value));
}

const ImageStorageForm: React.FC = () => {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_ai_image_storage', 'access');
  const canWrite = hasPerm('admin_ai_image_storage', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<ImageStorageConfig>({});
  const [committedConfig, setCommittedConfig] = useState<ImageStorageConfig>({});
  const [configLoaded, setConfigLoaded] = useState(false);
  const [editing, setEditing] = useState(false);

  // Upload logs
  const [logs, setLogs] = useState<ImageUploadLog[]>([]);
  const [logsPage, setLogsPage] = useState(0);
  const [logsTotalPages, setLogsTotalPages] = useState(0);
  const [logsLoading, setLogsLoading] = useState(false);

  // Test upload
  const [selectedTestFile, setSelectedTestFile] = useState<File | null>(null);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [lastTestUploadRemoteUrl, setLastTestUploadRemoteUrl] = useState('');
  const [testLoading, setTestLoading] = useState(false);
  const testFileInputRef = useRef<HTMLInputElement | null>(null);

  // Test compress
  const [compressPath, setCompressPath] = useState('');
  const [compressResult, setCompressResult] = useState<TestCompressResult | null>(null);
  const [compressLoading, setCompressLoading] = useState(false);
  const [maxBytesUnit, setMaxBytesUnit] = useState<ByteUnit>('MB');
  const [previewImage, setPreviewImage] = useState<{ thumbnailSrc: string; fullSrc: string; title: string } | null>(null);
  const [previewZoom, setPreviewZoom] = useState(1);
  const [previewNaturalSize, setPreviewNaturalSize] = useState<{ width: number; height: number }>({ width: 0, height: 0 });
  const [previewViewport, setPreviewViewport] = useState<{ width: number; height: number }>({ width: 0, height: 0 });

  const viewportWidth = previewViewport.width > 0 ? previewViewport.width : 1280;
  const viewportHeight = previewViewport.height > 0 ? previewViewport.height : 720;
  const maxPreviewWidth = Math.max(320, viewportWidth - 32);
  const maxPreviewHeight = Math.max(240, viewportHeight - 128);
  const hasNaturalSize = previewNaturalSize.width > 0 && previewNaturalSize.height > 0;
  const fitScale = hasNaturalSize
    ? Math.min(maxPreviewWidth / previewNaturalSize.width, maxPreviewHeight / previewNaturalSize.height, 1)
    : 1;
  // Keep the "fit-to-screen" default, but allow enlarging up to PREVIEW_ZOOM_MAX times the original image.
  const previewZoomMax = fitScale > 0 ? Math.max(PREVIEW_ZOOM_MAX, PREVIEW_ZOOM_MAX / fitScale) : PREVIEW_ZOOM_MAX;
  const renderScale = fitScale * previewZoom;
  const renderedWidth = hasNaturalSize ? Math.max(1, Math.round(previewNaturalSize.width * renderScale)) : undefined;
  const renderScalePercent = Math.round(renderScale * 100);

  useEffect(() => {
    if (editing) return;
    const value = config.compressionMaxBytes;
    if (typeof value !== 'number' || Number.isNaN(value)) return;
    setMaxBytesUnit(value >= BYTE_UNIT_FACTORS.MB ? 'MB' : 'KB');
  }, [config.compressionMaxBytes, editing]);

  useEffect(() => {
    if (!previewImage) return;
    const syncViewport = () => {
      setPreviewViewport({ width: window.innerWidth, height: window.innerHeight });
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setPreviewImage(null);
        return;
      }
      if (event.key === '+' || event.key === '=' || event.key === 'Add') {
        event.preventDefault();
        setPreviewZoom(prev => clampPreviewZoomWithMax(prev + PREVIEW_ZOOM_STEP, previewZoomMax));
        return;
      }
      if (event.key === '-' || event.key === 'Subtract') {
        event.preventDefault();
        setPreviewZoom(prev => clampPreviewZoomWithMax(prev - PREVIEW_ZOOM_STEP, previewZoomMax));
        return;
      }
      if (event.key === '0') {
        event.preventDefault();
        setPreviewZoom(1);
      }
    };
    syncViewport();
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('resize', syncViewport);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('resize', syncViewport);
    };
  }, [previewImage, previewZoomMax]);

  const openPreview = (thumbnailSrc: string, fullSrc: string | undefined, title: string) => {
    const resolvedFullSrc = String(fullSrc ?? '').trim() || thumbnailSrc;
    setPreviewImage({ thumbnailSrc, fullSrc: resolvedFullSrc, title });
    setPreviewZoom(1);
    setPreviewNaturalSize({ width: 0, height: 0 });
  };

  const closePreview = () => {
    setPreviewImage(null);
  };

  const changePreviewZoom = (delta: number, maxZoom: number = PREVIEW_ZOOM_MAX) => {
    setPreviewZoom(prev => clampPreviewZoomWithMax(prev + delta, maxZoom));
  };

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const cfg = await adminGetImageStorageConfig();
      setConfig(cfg);
      setCommittedConfig(cfg);
      setConfigLoaded(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadLogs = useCallback(async (page: number) => {
    setLogsLoading(true);
    try {
      const data = await adminGetUploadLogs(page);
      setLogs(data.content);
      setLogsTotalPages(data.totalPages);
      setLogsPage(data.number);
    } catch {
      // silent
    } finally {
      setLogsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!accessLoading && canAccess && !configLoaded) {
      loadConfig();
      loadLogs(0);
    }
  }, [accessLoading, canAccess, configLoaded, loadConfig, loadLogs]);

  const handleSave = async () => {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await adminUpdateImageStorageConfig(config);
      setConfig(updated);
      setCommittedConfig(updated);
      setEditing(false);
      setMessage('保存成功');
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    setConfig({ ...committedConfig });
    setEditing(false);
    setError(null);
    setMessage(null);
  };

  const handleStartEdit = () => {
    setEditing(true);
    setError(null);
    setMessage(null);
  };

  const handleTestUpload = async () => {
    if (!selectedTestFile) return;
    setTestLoading(true);
    setTestResult(null);
    setLastTestUploadRemoteUrl('');
    try {
      const uploaded = await uploadFile(selectedTestFile);
      const localPath = toServerLocalPath(uploaded.fileUrl);
      if (!localPath) {
        throw new Error('无法解析上传后的本地路径');
      }
      const mimeType = uploaded.mimeType ?? selectedTestFile.type ?? undefined;
      const r = await adminTestUpload(localPath, mimeType);
      if (r.success) {
        const remoteUrl = String(r.remoteUrl ?? '').trim();
        setLastTestUploadRemoteUrl(remoteUrl);
        setTestResult(`✅ 上传成功 (${r.elapsedMs}ms)\n本地文件: ${selectedTestFile.name}\n服务器路径: ${localPath}\n远程URL: ${r.remoteUrl}`);
      } else {
        setTestResult(`❌ 上传失败: ${r.error}`);
      }
    } catch (e) {
      setTestResult(`❌ ${e instanceof Error ? e.message : '测试失败'}`);
    } finally {
      setTestLoading(false);
    }
  };

  const toServerLocalPath = (url: string | null | undefined): string | null => {
    const raw = String(url ?? '').trim();
    if (!raw) return null;
    try {
      const parsed = new URL(raw, window.location.origin);
      const pathname = String(parsed.pathname ?? '').replace(/^\/+/, '').trim();
      return pathname || null;
    } catch {
      return null;
    }
  };

  const handleTestCompress = async () => {
    if (!compressPath.trim()) return;
    setCompressLoading(true);
    setCompressResult(null);
    try {
      const r = await adminTestCompress(compressPath.trim(), {
        compressionEnabled: config.compressionEnabled ?? true,
        compressionMaxWidth: config.compressionMaxWidth ?? null,
        compressionMaxHeight: config.compressionMaxHeight ?? null,
        compressionQuality: config.compressionQuality ?? null,
        compressionMaxBytes: config.compressionMaxBytes ?? null,
      });
      setCompressResult(r);
    } catch (e) {
      setCompressResult({ success: false, error: e instanceof Error ? e.message : '测试压缩失败' });
    } finally {
      setCompressLoading(false);
    }
  };

  const handleDeleteExpired = async () => {
    try {
      const r = await adminDeleteExpiredLogs();
      setMessage(`已清理 ${r.deleted} 条过期日志`);
      loadLogs(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : '清理失败');
    }
  };

  const updateField = <K extends keyof ImageStorageConfig>(key: K, value: ImageStorageConfig[K]) => {
    setConfig(prev => ({ ...prev, [key]: value }));
    if (!editing) setEditing(true);
  };

  if (accessLoading) return <div className="text-sm text-gray-500">加载权限中…</div>;
  if (!canAccess) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold mb-2">图片存储管理</h3>
        <div className="text-red-600 text-sm">无权限访问</div>
      </div>
    );
  }

  const mode = (config.mode ?? 'DASHSCOPE_TEMP') as StorageMode;
  const maxBytesValue = config.compressionMaxBytes ?? 5000000;
  const maxBytesDisplayValue = Number((maxBytesValue / BYTE_UNIT_FACTORS[maxBytesUnit]).toFixed(maxBytesUnit === 'MB' ? 2 : 0));

  return (
    <div className="space-y-6">
      {/* Config section */}
      <div className="bg-white rounded-lg shadow p-6 space-y-5">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold">图片存储管理</h3>
          <div className="text-sm text-gray-500">
            当前模式: <span className="font-medium text-blue-600">{MODE_LABELS[mode] ?? mode}</span>
          </div>
        </div>

        {error && <div className="text-sm text-red-600 bg-red-50 rounded p-3">{error}</div>}
        {message && <div className="text-sm text-green-600 bg-green-50 rounded p-3">{message}</div>}

        {/* Storage Mode */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">存储模式</label>
          <select
            className={inputClass}
            value={mode}
            disabled={!canWrite || !editing}
            onChange={e => updateField('mode', e.target.value)}
          >
            {(Object.keys(MODE_LABELS) as StorageMode[]).map(k => (
              <option key={k} value={k}>{MODE_LABELS[k]}</option>
            ))}
          </select>
          <p className="mt-1 text-xs text-gray-500">
            {mode === 'LOCAL' && '将本地文件路径拼接公网域名，适合服务器有公网 IP 的生产环境'}
            {mode === 'DASHSCOPE_TEMP' && '通过百炼 API 上传到临时存储（oss:// URL），48 小时后过期；适合开发/测试环境'}
            {mode === 'ALIYUN_OSS' && '上传到自有阿里云 OSS 存储桶，永久有效；适合无公网 IP 的生产环境'}
          </p>
        </div>

        {/* LOCAL fields */}
        {mode === 'LOCAL' && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">公网基础 URL</label>
            <input
              className={inputClass}
              placeholder="https://example.com"
              value={config.localBaseUrl ?? ''}
              disabled={!canWrite || !editing}
              onChange={e => updateField('localBaseUrl', e.target.value)}
            />
            <p className="mt-1 text-xs text-gray-500">本地路径前缀，如 https://example.com 则图片 URL = 基础URL + /uploads/xxx.jpg</p>
          </div>
        )}

        {/* DASHSCOPE_TEMP fields */}
        {mode === 'DASHSCOPE_TEMP' && (
          <div className="rounded-md bg-blue-50 border border-blue-200 p-3">
            <p className="text-xs text-blue-800 leading-relaxed">
              百炼临时存储将在调用 LLM 时按需上传图片，自动绑定到实际使用的模型。
              上传后的 oss:// URL 48 小时后过期。本地模型（LLM-Studio / Ollama）将自动跳过上传，使用 base64 编码。
            </p>
          </div>
        )}

        {/* ALIYUN_OSS fields */}
        {mode === 'ALIYUN_OSS' && (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">OSS Endpoint</label>
                <input className={inputClass} placeholder="oss-cn-hangzhou.aliyuncs.com" value={config.ossEndpoint ?? ''} disabled={!canWrite || !editing} onChange={e => updateField('ossEndpoint', e.target.value)} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Bucket</label>
                <input className={inputClass} placeholder="my-bucket" value={config.ossBucket ?? ''} disabled={!canWrite || !editing} onChange={e => updateField('ossBucket', e.target.value)} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">AccessKey ID</label>
                <input className={inputClass} value={config.ossAccessKeyId ?? ''} disabled={!canWrite || !editing} onChange={e => updateField('ossAccessKeyId', e.target.value)} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">AccessKey Secret</label>
                <input className={inputClass} type="password" value={config.ossAccessKeySecret ?? ''} disabled={!canWrite || !editing} onChange={e => updateField('ossAccessKeySecret', e.target.value)} />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Region</label>
              <input className={inputClass} placeholder="cn-hangzhou" value={config.ossRegion ?? ''} disabled={!canWrite || !editing} onChange={e => updateField('ossRegion', e.target.value)} />
            </div>
          </div>
        )}

        {/* Compression */}
        <div className="border-t pt-4">
          <h4 className="text-sm font-semibold text-gray-700 mb-3">图片压缩配置</h4>
          <div className="flex items-center gap-3 mb-3">
            <input
              type="checkbox"
              id="compressionEnabled"
              checked={config.compressionEnabled ?? true}
              disabled={!canWrite || !editing}
              onChange={e => updateField('compressionEnabled', e.target.checked)}
            />
            <label htmlFor="compressionEnabled" className="text-sm text-gray-700">启用上传前压缩</label>
          </div>
          {config.compressionEnabled !== false && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-600 mb-1">最大宽度 (px)</label>
                <input className={inputClass} type="number" value={config.compressionMaxWidth ?? 7680} disabled={!canWrite || !editing} onChange={e => updateField('compressionMaxWidth', Number(e.target.value) || null)} />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">最大高度 (px)</label>
                <input className={inputClass} type="number" value={config.compressionMaxHeight ?? 7680} disabled={!canWrite || !editing} onChange={e => updateField('compressionMaxHeight', Number(e.target.value) || null)} />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">压缩质量 (0.0 - 1.0)</label>
                <input className={inputClass} type="number" step="0.9" min="0.1" max="1" value={config.compressionQuality ?? 0.85} disabled={!canWrite || !editing} onChange={e => updateField('compressionQuality', Number(e.target.value) || null)} />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">最大大小</label>
                <div className="flex gap-2">
                  <input
                    className={inputClass}
                    type="number"
                    min={maxBytesUnit === 'MB' ? 0.1 : 1}
                    step={maxBytesUnit === 'MB' ? 0.1 : 1}
                    value={maxBytesDisplayValue}
                    disabled={!canWrite || !editing}
                    onChange={e => {
                      const raw = Number(e.target.value);
                      updateField('compressionMaxBytes', Number.isFinite(raw) && raw > 0
                        ? Math.round(raw * BYTE_UNIT_FACTORS[maxBytesUnit])
                        : null);
                    }}
                  />
                  <select
                    className={inputClass + ' max-w-[96px]'}
                    value={maxBytesUnit}
                    disabled={!canWrite || !editing}
                    onChange={e => setMaxBytesUnit(e.target.value as ByteUnit)}
                  >
                    <option value="KB">KB</option>
                    <option value="MB">MB</option>
                  </select>
                </div>
                <p className="mt-1 text-xs text-gray-500">保存时会自动换算为字节数</p>
              </div>
            </div>
          )}
        </div>

        {/* Save/Cancel */}
        {canWrite && (
          <div className="flex gap-3 pt-2">
            <button className={btnPrimaryClass} disabled={loading} onClick={editing ? handleSave : handleStartEdit}>
              {editing ? (loading ? '保存中…' : '保存配置') : '编辑'}
            </button>
            {editing && (
              <button className={btnSecondaryClass} onClick={handleCancel}>取消</button>
            )}
          </div>
        )}
      </div>

      {/* Test compress */}
      {canWrite && (
        <div className="bg-white rounded-lg shadow p-6 space-y-3">
          <h4 className="text-sm font-semibold text-gray-700">测试压缩</h4>
          <div className="flex gap-3">
            <input
              className={inputClass + ' flex-1'}
              placeholder="输入本地文件路径，或粘贴测试上传返回链接"
              value={compressPath}
              onChange={e => setCompressPath(e.target.value)}
            />
            <button
              className={btnSecondaryClass}
              disabled={!lastTestUploadRemoteUrl || compressLoading}
              onClick={() => setCompressPath(lastTestUploadRemoteUrl)}
              type="button"
              title={lastTestUploadRemoteUrl ? '使用最近一次测试上传返回链接' : '请先执行测试上传'}
            >
              复用上传链接
            </button>
            <button className={btnSecondaryClass} disabled={compressLoading || !compressPath.trim()} onClick={handleTestCompress}>
              {compressLoading ? '压缩中…' : '测试压缩'}
            </button>
          </div>
          {compressResult && (
            compressResult.success ? (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-4 text-xs">
                  <div className="bg-gray-50 rounded p-3 space-y-1">
                    <div className="font-medium text-gray-700">原始图片</div>
                    <div>尺寸: {compressResult.originalWidth} × {compressResult.originalHeight}</div>
                    <div>大小: {compressResult.originalSize != null ? `${Math.round(compressResult.originalSize / 1024)} KB` : '—'}</div>
                    {compressResult.originalPreview && (
                      <button
                        type="button"
                        className="mt-2 block focus:outline-none focus:ring-2 focus:ring-blue-500 rounded"
                        onClick={() => openPreview(compressResult.originalPreview!, compressResult.originalFullImage, '原始图片')}
                        title="点击放大查看"
                      >
                        <img src={compressResult.originalPreview} alt="原始预览" className="max-h-48 rounded border cursor-zoom-in" />
                      </button>
                    )}
                  </div>
                  <div className="bg-gray-50 rounded p-3 space-y-1">
                    <div className="font-medium text-gray-700">压缩后</div>
                    <div>尺寸: {compressResult.compressedWidth} × {compressResult.compressedHeight}</div>
                    <div>大小: {compressResult.compressedSize != null ? `${Math.round(compressResult.compressedSize / 1024)} KB` : '—'}</div>
                    <div>格式: {compressResult.format ?? '—'}</div>
                    <div>压缩率: {compressResult.compressionRatio ?? '—'}</div>
                    <div>是否压缩: {compressResult.wasCompressed ? '是' : '否（未超过阈值）'}</div>
                    {compressResult.compressedPreview && (
                      <button
                        type="button"
                        className="mt-2 block focus:outline-none focus:ring-2 focus:ring-blue-500 rounded"
                        onClick={() => openPreview(compressResult.compressedPreview!, compressResult.compressedFullImage, '压缩后图片')}
                        title="点击放大查看"
                      >
                        <img src={compressResult.compressedPreview} alt="压缩预览" className="max-h-48 rounded border cursor-zoom-in" />
                      </button>
                    )}
                  </div>
                </div>
                <div className="text-xs text-gray-500">提示：点击缩略图可弹窗放大查看</div>
              </div>
            ) : (
              <pre className="text-xs bg-gray-50 rounded p-3 whitespace-pre-wrap break-all text-red-600">❌ {compressResult.error}</pre>
            )
          )}
        </div>
      )}

      {/* Test upload */}
      {canWrite && (
        <div className="bg-white rounded-lg shadow p-6 space-y-3">
          <h4 className="text-sm font-semibold text-gray-700">测试上传</h4>
          <input
            ref={testFileInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0] ?? null;
              setSelectedTestFile(file);
              e.target.value = '';
            }}
          />
          <div className="flex gap-3">
            <button
              type="button"
              className={btnSecondaryClass}
              disabled={testLoading}
              onClick={() => testFileInputRef.current?.click()}
            >
              选择图片
            </button>
            <div className="flex-1 rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700">
              {selectedTestFile ? `已选择：${selectedTestFile.name}` : '未选择图片文件'}
            </div>
            <button className={btnSecondaryClass} disabled={testLoading || !selectedTestFile} onClick={handleTestUpload}>
              {testLoading ? '测试中…' : '测试上传'}
            </button>
          </div>
          {testResult && (
            <pre className="text-xs bg-gray-50 rounded p-3 whitespace-pre-wrap break-all">{testResult}</pre>
          )}
        </div>
      )}

      {/* Upload logs */}
      <div className="bg-white rounded-lg shadow p-6 space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-semibold text-gray-700">上传日志</h4>
          <div className="flex gap-2">
            <button className={btnSecondaryClass} disabled={logsLoading} onClick={() => loadLogs(logsPage)}>刷新</button>
            {canWrite && (
              <button className={btnDangerClass} onClick={handleDeleteExpired}>清理过期</button>
            )}
          </div>
        </div>

        {logs.length === 0 ? (
          <div className="text-sm text-gray-500">暂无上传日志</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b text-left text-gray-500">
                  <th className="py-2 pr-3">路径</th>
                  <th className="py-2 pr-3">远程 URL</th>
                  <th className="py-2 pr-3">模式</th>
                  <th className="py-2 pr-3">大小</th>
                  <th className="py-2 pr-3">耗时</th>
                  <th className="py-2 pr-3">上传时间</th>
                  <th className="py-2 pr-3">过期时间</th>
                  <th className="py-2">状态</th>
                </tr>
              </thead>
              <tbody>
                {logs.map(log => (
                  <tr key={log.id} className="border-b hover:bg-gray-50">
                    <td className="py-2 pr-3 max-w-[180px] truncate" title={log.localPath}>{log.localPath}</td>
                    <td className="py-2 pr-3 max-w-[200px] truncate" title={log.remoteUrl}>{log.remoteUrl}</td>
                    <td className="py-2 pr-3">{log.storageMode}</td>
                    <td className="py-2 pr-3">{log.fileSizeBytes != null ? `${Math.round(log.fileSizeBytes / 1024)}KB` : '—'}</td>
                    <td className="py-2 pr-3">{log.uploadDurationMs != null ? `${log.uploadDurationMs}ms` : '—'}</td>
                    <td className="py-2 pr-3">{fmtDateTime(log.uploadedAt)}</td>
                    <td className="py-2 pr-3">{fmtDateTime(log.expiresAt)}</td>
                    <td className="py-2">
                      <span className={`px-1.5 py-0.5 rounded text-xs ${log.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : log.status === 'FAILED' ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-600'}`}>
                        {log.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {logsTotalPages > 1 && (
          <div className="flex items-center gap-2 pt-2">
            <button className={btnSecondaryClass} disabled={logsPage <= 0} onClick={() => loadLogs(logsPage - 1)}>上一页</button>
            <span className="text-xs text-gray-500">{logsPage + 1} / {logsTotalPages}</span>
            <button className={btnSecondaryClass} disabled={logsPage >= logsTotalPages - 1} onClick={() => loadLogs(logsPage + 1)}>下一页</button>
          </div>
        )}
      </div>

      {previewImage && (
        <div
          className="fixed inset-0 z-50 bg-black/90"
          role="dialog"
          aria-modal="true"
          aria-label={`${previewImage.title}放大预览`}
        >
          <div className="flex h-full w-full flex-col">
            <div className="flex items-center justify-between gap-3 border-b border-white/20 bg-black/50 px-4 py-3">
              <div className="text-sm text-white">
                <div className="font-medium">{previewImage.title}</div>
                <div className="text-xs text-white/80">全清晰度预览 | 鼠标滚轮或快捷键 +/- 缩放，0 重置</div>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-white/90">显示倍率 {renderScalePercent}%</span>
                <button
                  type="button"
                  className={btnSecondaryClass}
                  disabled={previewZoom <= PREVIEW_ZOOM_MIN}
                  onClick={() => changePreviewZoom(-PREVIEW_ZOOM_STEP, previewZoomMax)}
                >
                  缩小
                </button>
                <button
                  type="button"
                  className={btnSecondaryClass}
                  onClick={() => setPreviewZoom(1)}
                >
                  重置
                </button>
                <button
                  type="button"
                  className={btnSecondaryClass}
                  disabled={previewZoom >= previewZoomMax}
                  onClick={() => changePreviewZoom(PREVIEW_ZOOM_STEP, previewZoomMax)}
                >
                  放大
                </button>
                <button
                  type="button"
                  className="inline-flex items-center justify-center rounded-md border border-red-300 bg-red-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
                  onClick={closePreview}
                >
                  关闭
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-auto p-3" onWheel={(event) => {
              event.preventDefault();
              changePreviewZoom(event.deltaY < 0 ? PREVIEW_ZOOM_STEP : -PREVIEW_ZOOM_STEP, previewZoomMax);
            }}>
              <div className="inline-flex min-h-full min-w-full items-center justify-center">
                <img
                  src={previewImage.fullSrc}
                  alt={`${previewImage.title}放大预览`}
                  className="h-auto max-w-none select-none rounded border border-white/30 bg-white shadow-2xl"
                  style={renderedWidth ? { width: `${renderedWidth}px` } : undefined}
                  onLoad={event => {
                    const target = event.currentTarget;
                    setPreviewNaturalSize({ width: target.naturalWidth, height: target.naturalHeight });
                  }}
                  onError={() => {
                    setPreviewImage(prev => {
                      if (!prev || prev.fullSrc === prev.thumbnailSrc) return prev;
                      return { ...prev, fullSrc: prev.thumbnailSrc };
                    });
                  }}
                />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImageStorageForm;
