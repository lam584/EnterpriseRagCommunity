export async function consumeSseResponse<T>(
  res: Response,
  parseEventBlock: (block: string) => T | null,
  onEvent: (event: T) => void
): Promise<void> {
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `请求失败: ${res.status}`);
  }
  const reader = res.body?.getReader();
  if (!reader) {
    throw new Error('浏览器不支持流式响应');
  }

  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    let idx = buffer.indexOf('\n\n');
    while (idx >= 0) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const event = parseEventBlock(block);
      if (event) onEvent(event);
      idx = buffer.indexOf('\n\n');
    }
  }
}

export type ParsedSseBlock = {
  eventType?: string;
  dataStr: string;
};

export function parseSseBlock(block: string): ParsedSseBlock {
  const lines = block
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.trim() !== '');

  let eventType: string | undefined;
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventType = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    }
  }

  return {
    eventType,
    dataStr: dataLines.join('\n'),
  };
}
