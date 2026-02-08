import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { uploadFile } from '../../../../services/uploadService';

export type ProfileAvatarUploaderProps = {
  value?: string;
  onChange: (url: string) => void;
  disabled?: boolean;
};

const MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;

export default function ProfileAvatarUploader({ value, onChange, disabled }: ProfileAvatarUploaderProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [uploading, setUploading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [cropOpen, setCropOpen] = useState(false);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [originalPreviewUrl, setOriginalPreviewUrl] = useState<string | null>(null);

  async function handleUpload(file: File) {
    if (disabled) return;

    setErr(null);

    if (!file.type.startsWith('image/')) {
      setErr('请选择图片文件');
      return;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setErr('图片不能超过 2MB');
      return;
    }

    setUploading(true);
    try {
      const r = await uploadFile(file);
      if (disabled) return;
      onChange(r.fileUrl);
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      setErr(message || '上传失败');
    } finally {
      setUploading(false);
    }
  }

  const isDisabled = !!disabled || uploading;

  return (
    <div className="flex items-center gap-4">
      <div className="h-16 w-16 rounded-full overflow-hidden bg-gray-100 border border-gray-200 flex items-center justify-center">
        {value ? (
          <img src={value} alt="avatar" className="h-full w-full object-cover" />
        ) : (
          <span className="text-xs text-gray-500">头像</span>
        )}
      </div>

      <div className="space-y-1">
        {!disabled ? (
          <div className="flex items-center gap-2">
            <input
              ref={inputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) {
                  setErr(null);
                  if (!f.type.startsWith('image/')) {
                    setErr('请选择图片文件');
                    e.currentTarget.value = '';
                    return;
                  }
                  if (f.size > MAX_FILE_SIZE_BYTES) {
                    setErr('图片不能超过 2MB');
                    e.currentTarget.value = '';
                    return;
                  }
                  const nextUrl = URL.createObjectURL(f);
                  setOriginalPreviewUrl((prev) => {
                    if (prev) URL.revokeObjectURL(prev);
                    return nextUrl;
                  });
                  setPendingFile(f);
                  setCropOpen(true);
                }
                // allow re-selecting the same file
                e.currentTarget.value = '';
              }}
            />

            <button
              type="button"
              className="px-3 py-2 rounded-md bg-gray-900 text-white hover:bg-gray-800 disabled:opacity-50"
              disabled={isDisabled}
              onClick={() => inputRef.current?.click()}
            >
              {uploading ? '上传中…' : value ? '更换头像' : '上传头像'}
            </button>

            {value ? (
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={isDisabled}
                onClick={() => onChange('')}
              >
                移除
              </button>
            ) : null}
          </div>
        ) : null}

        {!disabled ? <p className="text-xs text-gray-500">支持图片；最大 2MB。</p> : null}
        {!disabled && err ? <p className="text-sm text-red-600">{err}</p> : null}
      </div>

      <AvatarCropModal
        open={cropOpen}
        src={originalPreviewUrl}
        disabled={isDisabled}
        onClose={() => {
          setCropOpen(false);
          setPendingFile(null);
          setOriginalPreviewUrl((prev) => {
            if (prev) URL.revokeObjectURL(prev);
            return null;
          });
        }}
        onUseOriginal={async () => {
          if (!pendingFile) return;
          setCropOpen(false);
          await handleUpload(pendingFile);
          setPendingFile(null);
          setOriginalPreviewUrl((prev) => {
            if (prev) URL.revokeObjectURL(prev);
            return null;
          });
        }}
        onConfirm={async (file) => {
          setCropOpen(false);
          await handleUpload(file);
          setPendingFile(null);
          setOriginalPreviewUrl((prev) => {
            if (prev) URL.revokeObjectURL(prev);
            return null;
          });
        }}
      />
    </div>
  );
}

type AvatarCropModalProps = {
  open: boolean;
  src: string | null;
  disabled: boolean;
  onClose: () => void;
  onUseOriginal: () => void;
  onConfirm: (file: File) => void;
};

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n));
}

function roundTo(n: number, digits: number) {
  const p = 10 ** digits;
  return Math.round(n * p) / p;
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = (e) => reject(e);
    img.src = src;
  });
}

async function canvasToFile(canvas: HTMLCanvasElement, options: { mimeType: string; quality?: number; filename: string }) {
  const blob = await new Promise<Blob | null>((resolve) => {
    canvas.toBlob(
      (b) => resolve(b),
      options.mimeType,
      typeof options.quality === 'number' ? options.quality : undefined,
    );
  });
  if (!blob) throw new Error('生成头像失败');
  return new File([blob], options.filename, { type: options.mimeType });
}

function AvatarCropModal({ open, src, disabled, onClose, onUseOriginal, onConfirm }: AvatarCropModalProps) {
  const cropAreaRef = useRef<HTMLDivElement | null>(null);
  const [imgInfo, setImgInfo] = useState<{ w: number; h: number } | null>(null);
  const [cropSize, setCropSize] = useState(320);
  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [localErr, setLocalErr] = useState<string | null>(null);

  const [zoom, setZoom] = useState(1);
  const [rotate, setRotate] = useState(0);
  const [flipX, setFlipX] = useState(false);
  const [flipY, setFlipY] = useState(false);
  const [pan, setPan] = useState({ x: 0, y: 0 });

  const [outputSize, setOutputSize] = useState<256 | 384 | 512 | 768 | 1024>(512);
  const [outputMode, setOutputMode] = useState<'circle_png' | 'square_jpeg'>('circle_png');

  const baseScale = useMemo(() => {
    if (!imgInfo) return 1;
    return Math.max(cropSize / imgInfo.w, cropSize / imgInfo.h);
  }, [cropSize, imgInfo]);

  const imgStyle = useMemo(() => {
    const scale = baseScale * zoom;
    const tx = roundTo(pan.x, 2);
    const ty = roundTo(pan.y, 2);
    const r = roundTo(rotate, 2);
    const fx = flipX ? -1 : 1;
    const fy = flipY ? -1 : 1;
    return {
      transform: `translate(-50%, -50%) translate(${tx}px, ${ty}px) rotate(${r}deg) scale(${fx}, ${fy}) scale(${scale})`,
    } as const;
  }, [baseScale, zoom, pan.x, pan.y, rotate, flipX, flipY]);

  const previewStyle = useMemo(() => {
    const previewScale = 0.33;
    const scale = baseScale * zoom * previewScale;
    const tx = pan.x * previewScale;
    const ty = pan.y * previewScale;
    const r = rotate;
    const fx = flipX ? -1 : 1;
    const fy = flipY ? -1 : 1;
    return {
      transform: `translate(-50%, -50%) translate(${tx}px, ${ty}px) rotate(${r}deg) scale(${fx}, ${fy}) scale(${scale})`,
    } as const;
  }, [baseScale, zoom, pan.x, pan.y, rotate, flipX, flipY]);

  useLayoutEffect(() => {
    if (!open) return;
    if (!cropAreaRef.current) return;
    const el = cropAreaRef.current;
    const update = () => {
      const rect = el.getBoundingClientRect();
      const next = Math.max(240, Math.min(420, Math.floor(rect.width)));
      setCropSize(next);
    };
    update();
    const ro = new ResizeObserver(() => update());
    ro.observe(el);
    return () => ro.disconnect();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  useEffect(() => {
    if (!open || !src) return;
    setLoading(true);
    setLocalErr(null);
    setImgInfo(null);
    setZoom(1);
    setRotate(0);
    setFlipX(false);
    setFlipY(false);
    setPan({ x: 0, y: 0 });
    (async () => {
      try {
        const img = await loadImage(src);
        setImgInfo({ w: img.naturalWidth, h: img.naturalHeight });
      } catch (e: unknown) {
        const message = e instanceof Error ? e.message : String(e);
        setLocalErr(message || '图片加载失败');
      } finally {
        setLoading(false);
      }
    })();
  }, [open, src]);

  const constrainPan = useCallback((nextPan: { x: number; y: number }, nextZoom: number, nextRotate: number) => {
    if (!imgInfo) return nextPan;
    const w = imgInfo.w * baseScale * nextZoom;
    const h = imgInfo.h * baseScale * nextZoom;
    const rad = Math.abs((nextRotate * Math.PI) / 180);
    const cos = Math.cos(rad);
    const sin = Math.sin(rad);
    const bboxW = Math.abs(w * cos) + Math.abs(h * sin);
    const bboxH = Math.abs(w * sin) + Math.abs(h * cos);
    const maxX = Math.max(0, (bboxW - cropSize) / 2);
    const maxY = Math.max(0, (bboxH - cropSize) / 2);
    return { x: clamp(nextPan.x, -maxX, maxX), y: clamp(nextPan.y, -maxY, maxY) };
  }, [baseScale, cropSize, imgInfo]);

  useEffect(() => {
    if (!open) return;
    setPan((p) => constrainPan(p, zoom, rotate));
  }, [open, zoom, rotate, constrainPan]);

  if (!open || !src) return null;

  const canInteract = !disabled && !processing && !loading;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="w-full max-w-3xl rounded-lg bg-white shadow-2xl">
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
          <div className="font-medium text-gray-900">编辑头像</div>
          <button
            type="button"
            className="px-2 py-1 rounded-md hover:bg-gray-100 disabled:opacity-50"
            disabled={disabled || processing}
            onClick={onClose}
          >
            关闭
          </button>
        </div>

        <div className="p-4 grid grid-cols-1 md:grid-cols-[1fr_220px] gap-4">
          <div className="space-y-3">
            <div
              ref={cropAreaRef}
              className="w-full aspect-square rounded-lg overflow-hidden bg-gray-900 relative select-none touch-none"
              onWheel={(e) => {
                if (!canInteract) return;
                e.preventDefault();
                const delta = e.deltaY > 0 ? -0.06 : 0.06;
                const nextZoom = clamp(zoom + delta, 1, 4);
                setZoom(nextZoom);
              }}
              onPointerDown={(e) => {
                if (!canInteract) return;
                const el = e.currentTarget;
                el.setPointerCapture(e.pointerId);
                const startX = e.clientX;
                const startY = e.clientY;
                const startPan = pan;
                const onMove = (ev: PointerEvent) => {
                  const dx = ev.clientX - startX;
                  const dy = ev.clientY - startY;
                  const next = constrainPan({ x: startPan.x + dx, y: startPan.y + dy }, zoom, rotate);
                  setPan(next);
                };
                const onUp = () => {
                  window.removeEventListener('pointermove', onMove);
                  window.removeEventListener('pointerup', onUp);
                  window.removeEventListener('pointercancel', onUp);
                };
                window.addEventListener('pointermove', onMove);
                window.addEventListener('pointerup', onUp);
                window.addEventListener('pointercancel', onUp);
              }}
            >
              <div className="absolute inset-0 flex items-center justify-center">
                <img
                  src={src}
                  alt="avatar"
                  draggable={false}
                  className="absolute left-1/2 top-1/2 max-w-none max-h-none"
                  style={{
                    width: imgInfo ? `${imgInfo.w}px` : undefined,
                    height: imgInfo ? `${imgInfo.h}px` : undefined,
                    transformOrigin: 'center',
                    ...imgStyle,
                  }}
                />
              </div>

              <div
                className="absolute inset-0 pointer-events-none"
                style={{
                  background:
                    'radial-gradient(circle at center, rgba(0,0,0,0) 0 48%, rgba(0,0,0,0.55) 49% 100%)',
                }}
              />
              <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
                <div className="h-[96%] w-[96%] rounded-full border border-white/70 shadow-[0_0_0_1px_rgba(0,0,0,0.25)]" />
              </div>

              {loading ? (
                <div className="absolute inset-0 flex items-center justify-center text-sm text-white/90">加载中…</div>
              ) : null}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="space-y-1">
                <div className="text-xs text-gray-600">缩放</div>
                <input
                  type="range"
                  min={1}
                  max={4}
                  step={0.01}
                  value={zoom}
                  disabled={!canInteract}
                  onChange={(e) => setZoom(Number(e.target.value))}
                  className="w-full"
                />
              </label>
              <label className="space-y-1">
                <div className="text-xs text-gray-600">旋转</div>
                <input
                  type="range"
                  min={-180}
                  max={180}
                  step={1}
                  value={rotate}
                  disabled={!canInteract}
                  onChange={(e) => setRotate(Number(e.target.value))}
                  className="w-full"
                />
              </label>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={!canInteract}
                onClick={() => setRotate((r) => r - 90)}
              >
                左转 90°
              </button>
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={!canInteract}
                onClick={() => setRotate((r) => r + 90)}
              >
                右转 90°
              </button>
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={!canInteract}
                onClick={() => setFlipX((v) => !v)}
              >
                水平翻转
              </button>
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={!canInteract}
                onClick={() => setFlipY((v) => !v)}
              >
                垂直翻转
              </button>
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={!canInteract}
                onClick={() => {
                  setZoom(1);
                  setRotate(0);
                  setFlipX(false);
                  setFlipY(false);
                  setPan({ x: 0, y: 0 });
                }}
              >
                重置
              </button>
            </div>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <div className="text-sm font-medium text-gray-900">预览</div>
              <div className="h-24 w-24 rounded-full overflow-hidden bg-gray-100 border border-gray-200 flex items-center justify-center relative">
                <img
                  src={src}
                  alt="preview"
                  draggable={false}
                  className="absolute left-1/2 top-1/2 max-w-none max-h-none"
                  style={{
                    width: imgInfo ? `${imgInfo.w}px` : undefined,
                    height: imgInfo ? `${imgInfo.h}px` : undefined,
                    transformOrigin: 'center',
                    ...previewStyle,
                  }}
                />
              </div>
              <div className="text-xs text-gray-500">拖动图片调整位置；滚轮也可缩放</div>
            </div>

            <div className="space-y-2">
              <div className="text-sm font-medium text-gray-900">输出</div>
              <label className="space-y-1 block">
                <div className="text-xs text-gray-600">尺寸</div>
                <select
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  value={outputSize}
                  disabled={!canInteract}
                  onChange={(e) => setOutputSize(Number(e.target.value) as 256 | 384 | 512 | 768 | 1024)}
                >
                  <option value={256}>256 × 256</option>
                  <option value={384}>384 × 384</option>
                  <option value={512}>512 × 512</option>
                  <option value={768}>768 × 768</option>
                  <option value={1024}>1024 × 1024</option>
                </select>
              </label>
              <label className="space-y-1 block">
                <div className="text-xs text-gray-600">格式</div>
                <select
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                  value={outputMode}
                  disabled={!canInteract}
                  onChange={(e) => setOutputMode(e.target.value as 'circle_png' | 'square_jpeg')}
                >
                  <option value="circle_png">圆形 PNG（透明边角）</option>
                  <option value="square_jpeg">方形 JPEG（更小体积）</option>
                </select>
              </label>
            </div>

            <div className="flex flex-col gap-2">
              <button
                type="button"
                className="px-3 py-2 rounded-md bg-gray-900 text-white hover:bg-gray-800 disabled:opacity-50"
                disabled={!canInteract || !imgInfo}
                onClick={async () => {
                  if (!src || !imgInfo) return;
                  setLocalErr(null);
                  setProcessing(true);
                  try {
                    const img = await loadImage(src);
                    const canvas = document.createElement('canvas');
                    canvas.width = outputSize;
                    canvas.height = outputSize;
                    const ctx = canvas.getContext('2d');
                    if (!ctx) throw new Error('生成头像失败');

                    const ratio = outputSize / cropSize;
                    const scale = baseScale * zoom * ratio;
                    const rad = (rotate * Math.PI) / 180;

                    if (outputMode === 'circle_png') {
                      ctx.save();
                      ctx.beginPath();
                      ctx.arc(outputSize / 2, outputSize / 2, outputSize / 2, 0, Math.PI * 2);
                      ctx.clip();
                    } else {
                      ctx.fillStyle = '#ffffff';
                      ctx.fillRect(0, 0, outputSize, outputSize);
                    }

                    ctx.translate(outputSize / 2 + pan.x * ratio, outputSize / 2 + pan.y * ratio);
                    ctx.rotate(rad);
                    ctx.scale(flipX ? -1 : 1, flipY ? -1 : 1);
                    ctx.scale(scale, scale);
                    ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);

                    if (outputMode === 'circle_png') ctx.restore();

                    const file =
                      outputMode === 'circle_png'
                        ? await canvasToFile(canvas, { mimeType: 'image/png', filename: 'avatar.png' })
                        : await canvasToFile(canvas, { mimeType: 'image/jpeg', quality: 0.92, filename: 'avatar.jpg' });

                    if (file.size > MAX_FILE_SIZE_BYTES) {
                      throw new Error('裁剪后图片超过 2MB，请降低输出尺寸或选择 JPEG');
                    }
                    onConfirm(file);
                  } catch (e: unknown) {
                    const message = e instanceof Error ? e.message : String(e);
                    setLocalErr(message || '生成头像失败');
                  } finally {
                    setProcessing(false);
                  }
                }}
              >
                {processing ? '处理中…' : '裁剪并上传'}
              </button>
              <button
                type="button"
                className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                disabled={disabled || processing}
                onClick={onUseOriginal}
              >
                跳过裁剪，直接上传
              </button>
            </div>

            {localErr ? <div className="text-sm text-red-600">{localErr}</div> : null}
          </div>
        </div>
      </div>
    </div>
  );
}
