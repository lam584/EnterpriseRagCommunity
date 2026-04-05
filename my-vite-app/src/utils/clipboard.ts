export async function copyTextWithFallback(text: string): Promise<boolean> {
  const t = text ?? '';
  try {
    await navigator.clipboard.writeText(t);
    return true;
  } catch {
    try {
      const el = document.createElement('textarea');
      el.value = t;
      el.style.position = 'fixed';
      el.style.left = '-10000px';
      el.style.top = '0';
      document.body.appendChild(el);
      el.focus();
      el.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(el);
      return ok;
    } catch {
      return false;
    }
  }
}
