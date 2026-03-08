import FloatingWindow from '../../../../components/ui/FloatingWindow';

type UploadItemStatus = 'uploading' | 'verifying' | 'finalizing' | 'paused' | 'done' | 'error' | 'canceled';

export type UploadItem = {
  id: string;
  kind: 'image' | 'attachment';
  fileName: string;
  fileSize: number;
  status: UploadItemStatus;
  dedupeStatus?: 'hashing' | 'checking' | 'hit' | 'miss' | 'error' | null;
  hashLoaded?: number;
  hashTotal?: number;
  loaded: number;
  total: number;
  speedBps: number | null;
  etaSeconds: number | null;
  verifyLoaded: number;
  verifyTotal: number;
  verifySpeedBps: number | null;
  verifyEtaSeconds: number | null;
  errorMessage: string | null;
};

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '-';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  const mb = kb / 1024;
  const gb = mb / 1024;
  const tb = gb / 1024;
  const fmt = (n: number) => (n >= 100 ? n.toFixed(0) : n >= 10 ? n.toFixed(1) : n.toFixed(2));
  if (tb >= 1) return `${fmt(tb)} TB`;
  if (gb >= 1) return `${fmt(gb)} GB`;
  if (mb >= 1) return `${fmt(mb)} MB`;
  return `${fmt(kb)} KB`;
}

function formatEta(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '-';
  const s = Math.max(0, Math.floor(seconds));
  const hh = Math.floor(s / 3600);
  const mm = Math.floor((s % 3600) / 60);
  const ss = s % 60;
  const pad2 = (n: number) => String(n).padStart(2, '0');
  if (hh > 0) return `${pad2(hh)}:${pad2(mm)}:${pad2(ss)}`;
  return `${pad2(mm)}:${pad2(ss)}`;
}

export default function PostUploadTransferWindow(props: {
  uploadItems: UploadItem[];
  onPause: (id: string) => void;
  onResume: (id: string) => void;
  onRetry: (id: string) => void;
  onCancel: (id: string) => void;
  onClose: () => void;
}) {
  const { uploadItems, onPause, onResume, onRetry, onCancel, onClose } = props;

  const summary = (() => {
    let total = 0;
    let loaded = 0;
    let speedBps = 0;
    let hasSpeed = false;
    for (const u of uploadItems) {
      const isVerify = u.status === 'verifying' || u.status === 'finalizing';
      const t = isVerify ? (u.verifyTotal > 0 ? u.verifyTotal : u.fileSize) : u.total > 0 ? u.total : u.fileSize;
      const l = isVerify ? Math.min(u.verifyLoaded, t || u.verifyLoaded) : Math.min(u.loaded, t || u.loaded);
      total += Math.max(0, t);
      loaded += Math.max(0, l);
      const s = u.status === 'uploading' ? u.speedBps : u.status === 'verifying' ? u.verifySpeedBps : null;
      if (s != null && s > 0) {
        speedBps += s;
        hasSpeed = true;
      }
    }
    const percent = total > 0 ? Math.max(0, Math.min(1, loaded / total)) : 0;
    const speedText = hasSpeed ? `${formatBytes(speedBps)}/s` : '-';
    return { percent, speedText };
  })();

  return (
    <FloatingWindow
      storageKey="portal.posts.compose.uploadTransferWindow"
      title="传输列表"
      titleRight={(collapsed) => {
        if (!collapsed) return null;
        if (uploadItems.length === 0) return null;
        return (
          <div className="flex items-center gap-2">
            <div className="h-1.5 w-24 rounded bg-gray-200 overflow-hidden">
              <div className="h-full bg-blue-600" style={{ width: `${Math.round(summary.percent * 100)}%` }} />
            </div>
            <div className="text-xs text-gray-600 tabular-nums">{summary.speedText}</div>
          </div>
        );
      }}
      defaultRect={{ width: 520, height: 420 }}
      defaultAnchor="bottom-right"
      snapAnchorOnMount
      collapsedWidth={320}
      initialCollapsed={false}
      onClose={onClose}
    >
      <div className="h-full flex flex-col">
        <div className="flex-1 min-h-0 overflow-auto p-3 bg-white">
          {uploadItems.length === 0 ? (
            <div className="text-sm text-gray-500">暂无传输任务</div>
          ) : (
            <div className="space-y-2">
              {uploadItems.map((u) => {
                const isVerify = u.status === 'verifying' || u.status === 'finalizing';
                const total = isVerify ? (u.verifyTotal > 0 ? u.verifyTotal : u.fileSize) : u.total > 0 ? u.total : u.fileSize;
                const loaded = isVerify ? Math.min(u.verifyLoaded, total || u.verifyLoaded) : Math.min(u.loaded, total || u.loaded);
                const remain = Math.max(0, total - loaded);
                const percent = total > 0 ? Math.max(0, Math.min(1, loaded / total)) : 0;
                const speed =
                  u.status === 'uploading' ? u.speedBps : u.status === 'verifying' ? u.verifySpeedBps : null;
                const eta =
                  u.status === 'uploading' ? u.etaSeconds : u.status === 'verifying' ? u.verifyEtaSeconds : null;
                const speedText = speed != null && speed > 0 ? `${formatBytes(speed)}/s` : '-';
                const etaText = eta != null ? formatEta(eta) : '-';
                const statusText =
                  u.status === 'uploading'
                    ? '上传中'
                    : u.status === 'verifying'
                      ? '校验中'
                      : u.status === 'finalizing'
                        ? '归档中'
                    : u.status === 'paused'
                      ? '已暂停'
                      : u.status === 'done'
                        ? '完成'
                        : u.status === 'canceled'
                          ? '已取消'
                          : '失败';
                const statusClass =
                  u.status === 'done'
                    ? 'text-green-700'
                    : u.status === 'error'
                      ? 'text-red-700'
                      : u.status === 'canceled'
                        ? 'text-gray-600'
                        : u.status === 'paused'
                          ? 'text-amber-700'
                      : 'text-gray-700';
                const hashTotal = (u.hashTotal ?? 0) > 0 ? (u.hashTotal as number) : u.fileSize;
                const hashLoadedRaw = u.hashLoaded ?? 0;
                const hashLoaded = Math.min(Math.max(0, hashLoadedRaw), hashTotal || hashLoadedRaw);
                const hashPercent = hashTotal > 0 ? Math.max(0, Math.min(1, hashLoaded / hashTotal)) : 0;
                const dedupeText =
                  u.dedupeStatus === 'hashing'
                    ? `哈希 ${Math.round(hashPercent * 100)}%`
                    : u.dedupeStatus === 'checking'
                      ? '查重中'
                      : u.dedupeStatus === 'hit'
                        ? '命中重复'
                        : u.dedupeStatus === 'error'
                          ? '查重失败'
                          : null;
                const dedupeClass =
                  u.dedupeStatus === 'hit'
                    ? 'text-green-700'
                    : u.dedupeStatus === 'error'
                      ? 'text-amber-700'
                      : 'text-gray-600';

                return (
                  <div key={u.id} className="rounded border border-gray-200 px-3 py-2">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <div className="truncate text-sm text-gray-900" title={u.fileName}>
                            {u.fileName}
                          </div>
                          <div className="text-xs text-gray-500">{formatBytes(u.fileSize)}</div>
                          <div className={`text-xs ${statusClass}`}>{statusText}</div>
                        </div>

                        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-600">
                          <span>
                            {isVerify ? '已校验' : '已上传'} {formatBytes(loaded)} / {formatBytes(total)}（剩余 {formatBytes(remain)}）
                          </span>
                          <span>速度 {speedText}</span>
                          <span>剩余时间 {etaText}</span>
                          {dedupeText ? <span className={dedupeClass}>{dedupeText}</span> : null}
                        </div>

                        <div className="mt-2 h-2 w-full rounded bg-gray-100 overflow-hidden">
                          <div className="h-full bg-blue-600" style={{ width: `${Math.round(percent * 100)}%` }} />
                        </div>
                      </div>

                      {u.status === 'uploading' ? (
                        <div className="shrink-0 flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => onPause(u.id)}
                            className="px-3 py-1.5 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
                          >
                            暂停
                          </button>
                          <button
                            type="button"
                            onClick={() => onCancel(u.id)}
                            className="px-3 py-1.5 rounded-md border border-red-300 text-red-700 hover:bg-red-50"
                          >
                            取消
                          </button>
                        </div>
                      ) : u.status === 'paused' ? (
                        <div className="shrink-0 flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => onResume(u.id)}
                            className="px-3 py-1.5 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
                          >
                            继续
                          </button>
                          <button
                            type="button"
                            onClick={() => onCancel(u.id)}
                            className="px-3 py-1.5 rounded-md border border-red-300 text-red-700 hover:bg-red-50"
                          >
                            取消
                          </button>
                        </div>
                      ) : u.status === 'verifying' || u.status === 'finalizing' ? (
                        <div className="shrink-0 flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => onCancel(u.id)}
                            className="px-3 py-1.5 rounded-md border border-red-300 text-red-700 hover:bg-red-50"
                          >
                            取消
                          </button>
                        </div>
                      ) : u.status === 'error' ? (
                        <div className="shrink-0 flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => onRetry(u.id)}
                            className="px-3 py-1.5 rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
                          >
                            重试
                          </button>
                          <button
                            type="button"
                            onClick={() => onCancel(u.id)}
                            className="px-3 py-1.5 rounded-md border border-red-300 text-red-700 hover:bg-red-50"
                          >
                            取消
                          </button>
                        </div>
                      ) : null}
                    </div>

                    {u.errorMessage && u.status !== 'done' ? (
                      <div className={`mt-2 text-xs ${u.status === 'error' ? 'text-red-700' : 'text-amber-700'}`}>{u.errorMessage}</div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </FloatingWindow>
  );
}
