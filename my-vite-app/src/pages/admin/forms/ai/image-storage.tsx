import React, { useCallback, useEffect, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
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
  const [testPath, setTestPath] = useState('');
  const [testResult, setTestResult] = useState<string | null>(null);
  const [testLoading, setTestLoading] = useState(false);

  // Test compress
  const [compressPath, setCompressPath] = useState('');
  const [compressResult, setCompressResult] = useState<TestCompressResult | null>(null);
  const [compressLoading, setCompressLoading] = useState(false);

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
    if (!testPath.trim()) return;
    setTestLoading(true);
    setTestResult(null);
    try {
      const r = await adminTestUpload(testPath.trim());
      if (r.success) {
        setTestResult(`✅ 上传成功 (${r.elapsedMs}ms)\n${r.remoteUrl}`);
      } else {
        setTestResult(`❌ 上传失败: ${r.error}`);
      }
    } catch (e) {
      setTestResult(`❌ ${e instanceof Error ? e.message : '测试失败'}`);
    } finally {
      setTestLoading(false);
    }
  };

  const handleTestCompress = async () => {
    if (!compressPath.trim()) return;
    setCompressLoading(true);
    setCompressResult(null);
    try {
      const r = await adminTestCompress(compressPath.trim());
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
                <input className={inputClass} type="number" value={config.compressionMaxWidth ?? 1920} disabled={!canWrite || !editing} onChange={e => updateField('compressionMaxWidth', Number(e.target.value) || null)} />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">最大高度 (px)</label>
                <input className={inputClass} type="number" value={config.compressionMaxHeight ?? 1920} disabled={!canWrite || !editing} onChange={e => updateField('compressionMaxHeight', Number(e.target.value) || null)} />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">压缩质量 (0.0 - 1.0)</label>
                <input className={inputClass} type="number" step="0.05" min="0.1" max="1" value={config.compressionQuality ?? 0.85} disabled={!canWrite || !editing} onChange={e => updateField('compressionQuality', Number(e.target.value) || null)} />
              </div>
              <div>
                <label className="block text-xs text-gray-600 mb-1">最大字节数</label>
                <input className={inputClass} type="number" value={config.compressionMaxBytes ?? 500000} disabled={!canWrite || !editing} onChange={e => updateField('compressionMaxBytes', Number(e.target.value) || null)} />
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
              placeholder="输入服务器上的本地文件路径，如 /uploads/2026/01/test.jpg"
              value={compressPath}
              onChange={e => setCompressPath(e.target.value)}
            />
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
                      <img src={compressResult.originalPreview} alt="原始预览" className="mt-2 max-h-48 rounded border" />
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
                      <img src={compressResult.compressedPreview} alt="压缩预览" className="mt-2 max-h-48 rounded border" />
                    )}
                  </div>
                </div>
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
          <div className="flex gap-3">
            <input
              className={inputClass + ' flex-1'}
              placeholder="输入服务器上的本地文件路径，如 /uploads/2026/01/test.jpg"
              value={testPath}
              onChange={e => setTestPath(e.target.value)}
            />
            <button className={btnSecondaryClass} disabled={testLoading || !testPath.trim()} onClick={handleTestUpload}>
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
    </div>
  );
};

export default ImageStorageForm;
