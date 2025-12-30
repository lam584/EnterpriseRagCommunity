import { useRef, useState } from 'react';
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

  async function handleFileSelected(file: File) {
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
                if (f) void handleFileSelected(f);
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
    </div>
  );
}
