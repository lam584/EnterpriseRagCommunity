export function computeLogsTotalPages(total: number | null | undefined): number {
  return Math.max(1, Math.ceil((total || 0) / 20));
}

export async function copyTextWithFeedback(
  text: string,
  onSuccess: (message: string) => void,
  onError: (message: string) => void,
): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    onSuccess('已复制到剪贴板');
  } catch {
    onError('复制失败（浏览器权限限制）');
  }
}

export function AdminLoadingCard() {
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <div className="text-gray-500">加载中…</div>
    </div>
  );
}
