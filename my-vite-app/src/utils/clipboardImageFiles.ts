export function extractImageFilesFromClipboardData(clipboardData: DataTransfer | null | undefined): File[] {
  const items = Array.from(clipboardData?.items ?? []);
  const files: File[] = [];
  for (const item of items) {
    if (item.kind !== 'file') continue;
    if (!String(item.type ?? '').toLowerCase().startsWith('image/')) continue;
    const file = item.getAsFile();
    if (file) files.push(file);
  }
  return files;
}
