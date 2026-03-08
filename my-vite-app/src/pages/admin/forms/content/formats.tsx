import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAccess } from '../../../../contexts/AccessContext';
import {
  adminGetContentFormatsConfig,
  adminUpdateContentFormatsConfig,
  type UploadFormatRuleDTO,
  type UploadFormatsConfigDTO,
} from '../../../../services/contentFormatsAdminService';

const inputClass =
  'block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors duration-200';
const btnPrimaryClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnSecondaryClass =
  'inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';

const DEFAULT_CFG: UploadFormatsConfigDTO = {
  enabled: true,
  maxFilesPerRequest: 100000,
  maxFileSizeBytes: 500 * 1024 * 1024 * 1024,
  maxTotalSizeBytes: 2 * 1024 * 1024 * 1024 * 1024,
  parseTimeoutMillis: 86_400_000,
  parseMaxChars: 10_000_000_000,
  formats: [],
};

function safeInt(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'string' && v.trim() === '') return null;
  const n = Number(v);
  if (!Number.isFinite(n)) return null;
  return Math.trunc(n);
}

function safeLong(v: unknown): number | null {
  const n = safeInt(v);
  if (n === null) return null;
  if (n < 0) return null;
  return n;
}

type SizeUnit = 'KB' | 'MB' | 'GB';

const SIZE_UNIT_FACTORS: Record<SizeUnit, number> = {
  KB: 1024,
  MB: 1024 * 1024,
  GB: 1024 * 1024 * 1024,
};

function trimDecimalText(v: number): string {
  return Number.isInteger(v) ? String(v) : String(Number(v.toFixed(3)));
}

function chooseSizeUnit(bytes: number | null | undefined): SizeUnit {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes <= 0) return 'MB';
  if (bytes >= SIZE_UNIT_FACTORS.GB) return 'GB';
  if (bytes >= SIZE_UNIT_FACTORS.MB) return 'MB';
  return 'KB';
}

function bytesToUnitInput(bytes: number | null | undefined, unit: SizeUnit): string {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes)) return '';
  return trimDecimalText(bytes / SIZE_UNIT_FACTORS[unit]);
}

function unitInputToBytes(value: string, unit: SizeUnit): number | null {
  if (value.trim() === '') return null;
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return null;
  return Math.round(n * SIZE_UNIT_FACTORS[unit]);
}

function millisToSecondsInput(millis: number | null | undefined): string {
  if (typeof millis !== 'number' || !Number.isFinite(millis)) return '';
  return trimDecimalText(millis / 1000);
}

function secondsInputToMillis(value: string): number | null {
  if (value.trim() === '') return null;
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return null;
  return Math.round(n * 1000);
}

function formatBytesHint(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  const mb = kb / 1024;
  const gb = mb / 1024;
  const fmt = (n: number) => (n >= 100 ? n.toFixed(0) : n >= 10 ? n.toFixed(1) : n.toFixed(2));
  if (gb >= 1) return `${fmt(gb)} GB（${fmt(mb)} MB）`;
  if (mb >= 1) return `${fmt(mb)} MB（${fmt(kb)} KB）`;
  return `${fmt(kb)} KB`;
}

function normalizeExtensions(input: string): string[] {
  const parts = input
    .split(',')
    .map((s) => s.trim().replace(/^\./, ''))
    .filter(Boolean);
  const out: string[] = [];
  for (const p of parts) {
    const v = p.toLowerCase();
    if (!out.includes(v)) out.push(v);
  }
  return out;
}

function extensionsToInput(exts?: string[] | null): string {
  const list = Array.isArray(exts) ? exts : [];
  return list.filter(Boolean).join(', ');
}

export default function ContentFormatsForm() {
  const { loading: accessLoading, hasPerm } = useAccess();
  const canAccess = hasPerm('admin_content_formats', 'access');
  const canWrite = hasPerm('admin_content_formats', 'write');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [config, setConfig] = useState<UploadFormatsConfigDTO>({ ...DEFAULT_CFG });
  const [committedConfig, setCommittedConfig] = useState<UploadFormatsConfigDTO>({ ...DEFAULT_CFG });
  const [editing, setEditing] = useState(false);
  const [maxFileSizeUnit, setMaxFileSizeUnit] = useState<SizeUnit>(chooseSizeUnit(DEFAULT_CFG.maxFileSizeBytes));
  const [maxTotalSizeUnit, setMaxTotalSizeUnit] = useState<SizeUnit>(chooseSizeUnit(DEFAULT_CFG.maxTotalSizeBytes));
  const [formatSizeUnits, setFormatSizeUnits] = useState<Record<number, SizeUnit>>({});

  const syncUnitsFromConfig = useCallback((cfg: UploadFormatsConfigDTO) => {
    setMaxFileSizeUnit(chooseSizeUnit(cfg.maxFileSizeBytes));
    setMaxTotalSizeUnit(chooseSizeUnit(cfg.maxTotalSizeBytes));
    const next: Record<number, SizeUnit> = {};
    const list = Array.isArray(cfg.formats) ? (cfg.formats as UploadFormatRuleDTO[]) : [];
    list.forEach((item, idx) => {
      next[idx] = chooseSizeUnit(item.maxFileSizeBytes ?? null);
    });
    setFormatSizeUnits(next);
  }, []);

  const hasUnsavedChanges = useMemo(() => JSON.stringify(config) !== JSON.stringify(committedConfig), [config, committedConfig]);

  const loadConfig = useCallback(async () => {
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const cfg = await adminGetContentFormatsConfig();
      const next = { ...DEFAULT_CFG, ...(cfg ?? {}), formats: (cfg?.formats ?? []) as UploadFormatRuleDTO[] };
      setConfig(next);
      setCommittedConfig(next);
      syncUnitsFromConfig(next);
      setEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [syncUnitsFromConfig]);

  useEffect(() => {
    if (!accessLoading && canAccess) {
      loadConfig();
    }
  }, [accessLoading, canAccess, loadConfig]);

  if (accessLoading) return <div className="text-sm text-gray-600">加载权限...</div>;
  if (!canAccess) return <div className="text-sm text-gray-600">无权限访问</div>;

  const formats = Array.isArray(config.formats) ? (config.formats as UploadFormatRuleDTO[]) : [];

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">格式管理</h3>
            <div className="text-sm text-gray-600">管理上传/解析的开关、限制与支持格式</div>
          </div>
          <div className="flex items-center gap-2">
            {!editing ? (
              <button type="button" className={btnSecondaryClass} onClick={() => setEditing(true)} disabled={!canWrite || loading}>
                编辑
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className={btnSecondaryClass}
                  onClick={() => {
                    setConfig(committedConfig);
                    syncUnitsFromConfig(committedConfig);
                    setEditing(false);
                    setMessage(null);
                    setError(null);
                  }}
                  disabled={loading}
                >
                  取消
                </button>
                <button
                  type="button"
                  className={btnPrimaryClass}
                  disabled={!canWrite || loading || !hasUnsavedChanges}
                  onClick={async () => {
                    setLoading(true);
                    setError(null);
                    setMessage(null);
                    try {
                      const saved = await adminUpdateContentFormatsConfig(config);
                      const next = { ...DEFAULT_CFG, ...(saved ?? {}), formats: (saved?.formats ?? []) as UploadFormatRuleDTO[] };
                      setConfig(next);
                      setCommittedConfig(next);
                      syncUnitsFromConfig(next);
                      setEditing(false);
                      setMessage('保存成功');
                    } catch (e) {
                      setError(e instanceof Error ? e.message : String(e));
                    } finally {
                      setLoading(false);
                    }
                  }}
                >
                  保存
                </button>
              </>
            )}
            <button type="button" className={btnSecondaryClass} onClick={loadConfig} disabled={loading}>
              刷新
            </button>
          </div>
        </div>

        {error ? <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}
        {message ? <div className="rounded border border-green-200 bg-green-50 p-3 text-sm text-green-700">{message}</div> : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <label className="space-y-1">
            <div className="text-sm font-medium text-gray-700">全局开关</div>
            <select
              className={inputClass}
              disabled={!editing}
              value={config.enabled === false ? 'false' : 'true'}
              onChange={(e) => setConfig((p) => ({ ...p, enabled: e.target.value === 'true' }))}
            >
              <option value="true">启用</option>
              <option value="false">关闭</option>
            </select>
          </label>

          <label className="space-y-1">
            <div className="text-sm font-medium text-gray-700">单次最多文件数</div>
            <input
              className={inputClass}
              disabled={!editing}
              value={config.maxFilesPerRequest ?? ''}
              onChange={(e) => setConfig((p) => ({ ...p, maxFilesPerRequest: safeInt(e.target.value) }))}
              placeholder="100000"
            />
          </label>

          <label className="space-y-1">
            <div className="text-sm font-medium text-gray-700">单文件最大大小（可空）</div>
            <div className="flex items-center gap-2">
              <input
                className={inputClass}
                disabled={!editing}
                value={bytesToUnitInput(config.maxFileSizeBytes, maxFileSizeUnit)}
                onChange={(e) => setConfig((p) => ({ ...p, maxFileSizeBytes: unitInputToBytes(e.target.value, maxFileSizeUnit) }))}
                placeholder="500"
              />
              <select className={inputClass} disabled={!editing} value={maxFileSizeUnit} onChange={(e) => setMaxFileSizeUnit(e.target.value as SizeUnit)}>
                <option value="KB">KB</option>
                <option value="MB">MB</option>
                <option value="GB">GB</option>
              </select>
            </div>
            {typeof config.maxFileSizeBytes === 'number' ? (
              <div className="text-xs text-gray-500">约 {formatBytesHint(config.maxFileSizeBytes)}</div>
            ) : null}
          </label>

          <label className="space-y-1">
            <div className="text-sm font-medium text-gray-700">单次总大小上限（可空）</div>
            <div className="flex items-center gap-2">
              <input
                className={inputClass}
                disabled={!editing}
                value={bytesToUnitInput(config.maxTotalSizeBytes, maxTotalSizeUnit)}
                onChange={(e) => setConfig((p) => ({ ...p, maxTotalSizeBytes: unitInputToBytes(e.target.value, maxTotalSizeUnit) }))}
                placeholder="2048"
              />
              <select className={inputClass} disabled={!editing} value={maxTotalSizeUnit} onChange={(e) => setMaxTotalSizeUnit(e.target.value as SizeUnit)}>
                <option value="KB">KB</option>
                <option value="MB">MB</option>
                <option value="GB">GB</option>
              </select>
            </div>
            {typeof config.maxTotalSizeBytes === 'number' ? (
              <div className="text-xs text-gray-500">约 {formatBytesHint(config.maxTotalSizeBytes)}</div>
            ) : null}
          </label>

          <label className="space-y-1">
            <div className="text-sm font-medium text-gray-700">解析超时（秒）</div>
            <input
              className={inputClass}
              disabled={!editing}
              value={millisToSecondsInput(config.parseTimeoutMillis)}
              onChange={(e) => setConfig((p) => ({ ...p, parseTimeoutMillis: secondsInputToMillis(e.target.value) }))}
              placeholder="86400"
            />
          </label>

          <label className="space-y-1">
            <div className="text-sm font-medium text-gray-700">解析最多字符（可空）</div>
            <input
              className={inputClass}
              disabled={!editing}
              value={config.parseMaxChars ?? ''}
              onChange={(e) => setConfig((p) => ({ ...p, parseMaxChars: safeLong(e.target.value) }))}
              placeholder="10000000000"
            />
          </label>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h4 className="text-base font-semibold">支持格式</h4>
            <div className="text-sm text-gray-600">每种格式可单独开启上传/解析与扩展名白名单</div>
          </div>
          <button
            type="button"
            className={btnSecondaryClass}
            disabled={!editing}
            onClick={() => {
              setConfig((p) => ({
                ...p,
                formats: [
                  ...(Array.isArray(p.formats) ? (p.formats as UploadFormatRuleDTO[]) : []),
                  { format: '', enabled: true, parseEnabled: true, extensions: [] },
                ],
              }));
              setFormatSizeUnits((prev) => ({ ...prev, [formats.length]: 'MB' }));
            }}
          >
            新增格式
          </button>
        </div>

        <div className="overflow-auto border rounded">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-3 py-2">Format</th>
                <th className="text-left px-3 py-2">启用</th>
                <th className="text-left px-3 py-2">解析</th>
                <th className="text-left px-3 py-2">Extensions（逗号分隔）</th>
                <th className="text-left px-3 py-2">单格式大小上限（可空）</th>
                <th className="text-right px-3 py-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {formats.length === 0 ? (
                <tr>
                  <td className="px-3 py-3 text-sm text-gray-600" colSpan={6}>
                    （暂无配置）
                  </td>
                </tr>
              ) : (
                formats.map((f, idx) => (
                  <tr key={`${idx}-${f.format ?? ''}`} className="border-t">
                    <td className="px-3 py-2">
                      <input
                        className={inputClass}
                        disabled={!editing}
                        value={f.format ?? ''}
                        onChange={(e) => {
                          const v = e.target.value;
                          setConfig((p) => {
                            const list = Array.isArray(p.formats) ? ([...p.formats] as UploadFormatRuleDTO[]) : [];
                            const next = { ...(list[idx] ?? {}) } as UploadFormatRuleDTO;
                            next.format = v;
                            list[idx] = next;
                            return { ...p, formats: list };
                          });
                        }}
                        placeholder="例如 pdf / docx / pptx"
                      />
                    </td>
                    <td className="px-3 py-2">
                      <select
                        className={inputClass}
                        disabled={!editing}
                        value={f.enabled === false ? 'false' : 'true'}
                        onChange={(e) => {
                          const val = e.target.value === 'true';
                          setConfig((p) => {
                            const list = Array.isArray(p.formats) ? ([...p.formats] as UploadFormatRuleDTO[]) : [];
                            const next = { ...(list[idx] ?? {}) } as UploadFormatRuleDTO;
                            next.enabled = val;
                            list[idx] = next;
                            return { ...p, formats: list };
                          });
                        }}
                      >
                        <option value="true">启用</option>
                        <option value="false">关闭</option>
                      </select>
                    </td>
                    <td className="px-3 py-2">
                      <select
                        className={inputClass}
                        disabled={!editing}
                        value={f.parseEnabled === false ? 'false' : 'true'}
                        onChange={(e) => {
                          const val = e.target.value === 'true';
                          setConfig((p) => {
                            const list = Array.isArray(p.formats) ? ([...p.formats] as UploadFormatRuleDTO[]) : [];
                            const next = { ...(list[idx] ?? {}) } as UploadFormatRuleDTO;
                            next.parseEnabled = val;
                            list[idx] = next;
                            return { ...p, formats: list };
                          });
                        }}
                      >
                        <option value="true">启用</option>
                        <option value="false">关闭</option>
                      </select>
                    </td>
                    <td className="px-3 py-2 min-w-[240px]">
                      <input
                        className={inputClass}
                        disabled={!editing}
                        value={extensionsToInput(f.extensions)}
                        onChange={(e) => {
                          const exts = normalizeExtensions(e.target.value);
                          setConfig((p) => {
                            const list = Array.isArray(p.formats) ? ([...p.formats] as UploadFormatRuleDTO[]) : [];
                            const next = { ...(list[idx] ?? {}) } as UploadFormatRuleDTO;
                            next.extensions = exts;
                            list[idx] = next;
                            return { ...p, formats: list };
                          });
                        }}
                        placeholder="pdf, doc, docx"
                      />
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-2 min-w-[220px]">
                        <input
                          className={inputClass}
                          disabled={!editing}
                          value={bytesToUnitInput(f.maxFileSizeBytes ?? null, formatSizeUnits[idx] ?? chooseSizeUnit(f.maxFileSizeBytes ?? null))}
                          onChange={(e) => {
                            const unit = formatSizeUnits[idx] ?? chooseSizeUnit(f.maxFileSizeBytes ?? null);
                            const val = unitInputToBytes(e.target.value, unit);
                            setConfig((p) => {
                              const list = Array.isArray(p.formats) ? ([...p.formats] as UploadFormatRuleDTO[]) : [];
                              const next = { ...(list[idx] ?? {}) } as UploadFormatRuleDTO;
                              next.maxFileSizeBytes = val;
                              list[idx] = next;
                              return { ...p, formats: list };
                            });
                          }}
                          placeholder="例如 10"
                        />
                        <select
                          className={inputClass}
                          disabled={!editing}
                          value={formatSizeUnits[idx] ?? chooseSizeUnit(f.maxFileSizeBytes ?? null)}
                          onChange={(e) => {
                            const unit = e.target.value as SizeUnit;
                            setFormatSizeUnits((prev) => ({ ...prev, [idx]: unit }));
                          }}
                        >
                          <option value="KB">KB</option>
                          <option value="MB">MB</option>
                          <option value="GB">GB</option>
                        </select>
                      </div>
                    </td>
                    <td className="px-3 py-2 text-right">
                      {editing && canWrite ? (
                        <button
                          type="button"
                          className="text-sm text-red-700 hover:underline disabled:opacity-50"
                          disabled={loading}
                          onClick={() => {
                            const nextFormats = formats.filter((_, i) => i !== idx);
                            setConfig((p) => ({ ...p, formats: nextFormats }));
                            setFormatSizeUnits((prev) => {
                              const next: Record<number, SizeUnit> = {};
                              nextFormats.forEach((item, i) => {
                                const prevIndex = i >= idx ? i + 1 : i;
                                next[i] = prev[prevIndex] ?? chooseSizeUnit(item.maxFileSizeBytes ?? null);
                              });
                              return next;
                            });
                          }}
                        >
                          删除
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
