import { useMemo } from 'react';
import type { AiProviderDTO } from '../../services/aiProvidersAdminService';
import type { AiChatProviderOptionDTO } from '../../services/aiChatOptionsService';

type Mode = 'chat' | 'embedding';

type ProviderModelValue = {
  providerId: string;
  model: string;
};

type OptionRow = {
  value: string;
  label: string;
  sortKey: string;
};

function trimStr(v: unknown): string {
  return String(v ?? '').trim();
}

function enc(v: string): string {
  return encodeURIComponent(v);
}

function dec(v: string): string {
  return decodeURIComponent(v);
}

function encodeProviderModelValue(providerId: string, model: string): string {
  const p = trimStr(providerId);
  const m = trimStr(model);
  if (!p && !m) return '';
  if (p && !m) return `p|${enc(p)}`;
  if (!p && m) return `m|${enc(m)}`;
  return `pm|${enc(p)}|${enc(m)}`;
}

function parseProviderModelValue(value: string): ProviderModelValue | null {
  const v = trimStr(value);
  if (!v) return null;
  try {
    if (v.startsWith('p|')) {
      const p = dec(v.slice(2)).trim();
      if (!p) return null;
      return { providerId: p, model: '' };
    }
    if (v.startsWith('m|')) {
      const m = dec(v.slice(2)).trim();
      if (!m) return null;
      return { providerId: '', model: m };
    }
    if (v.startsWith('pm|')) {
      const rest = v.slice(3);
      const idx = rest.indexOf('|');
      if (idx <= 0) return null;
      const p = dec(rest.slice(0, idx)).trim();
      const m = dec(rest.slice(idx + 1)).trim();
      if (!p || !m) return null;
      return { providerId: p, model: m };
    }
    const idx = v.indexOf('|');
    if (idx >= 0) {
      const p = dec(v.slice(0, idx)).trim();
      const m = dec(v.slice(idx + 1)).trim();
      if (!p) return null;
      return { providerId: p, model: m };
    }
    const p = dec(v).trim();
    if (!p) return null;
    return { providerId: p, model: '' };
  } catch {
    return null;
  }
}

function formatProviderLabel(p?: AiProviderDTO | null): string {
  const id = trimStr(p?.id);
  const name = trimStr(p?.name);
  if (name) return name;
  return id || '—';
}

function findProviderById(providers: AiProviderDTO[], providerId: string): AiProviderDTO | undefined {
  const pid = trimStr(providerId);
  if (!pid) return undefined;
  return providers.find((x) => trimStr(x.id) === pid);
}

function providerDisabledSuffix(p?: AiProviderDTO | null): string {
  return p?.enabled === false ? ' [禁用]' : '';
}

function buildChatModelsByProviderId(chatProviders: AiChatProviderOptionDTO[]): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const p of chatProviders) {
    const id = trimStr(p.id);
    if (!id) continue;
    const list: string[] = [];
    const rows = Array.isArray(p.chatModels) ? p.chatModels.filter(Boolean) : [];
    for (const m of rows) {
      const name = trimStr((m as { name?: unknown }).name);
      if (!name) continue;
      list.push(name);
    }
    out.set(id, list);
  }
  return out;
}

function sortProvidersForOptions(providers: AiProviderDTO[]): AiProviderDTO[] {
  const copy = [...providers];
  copy.sort((a, b) => formatProviderLabel(a).localeCompare(formatProviderLabel(b), 'zh-Hans-CN'));
  return copy;
}

function buildOptions(params: {
  providers: AiProviderDTO[];
  activeProviderId: string;
  chatProviders?: AiChatProviderOptionDTO[];
  mode: Mode;
  includeModelOnlyOptions: boolean;
  includeProviderOnlyOptions: boolean;
  current: ProviderModelValue;
}): { options: OptionRow[]; currentValue: string; autoLabel: string } {
  const { providers, activeProviderId, chatProviders, mode, includeModelOnlyOptions, includeProviderOnlyOptions, current } = params;

  const globalProvider = findProviderById(providers, activeProviderId);
  const globalProviderLabel = globalProvider ? `${formatProviderLabel(globalProvider)}${providerDisabledSuffix(globalProvider)}` : (trimStr(activeProviderId) || '—');
  const autoLabel = '自动（均衡负载）';

  const chatProvidersList = (chatProviders ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
  const chatModelsMap = buildChatModelsByProviderId(chatProvidersList);

  const uniq = new Map<string, OptionRow>();
  const add = (row: OptionRow) => {
    if (!row.value) return;
    if (uniq.has(row.value)) return;
    uniq.set(row.value, row);
  };

  const curProviderId = trimStr(current.providerId);
  const curModel = trimStr(current.model);
  const isModelOnly = Boolean(!curProviderId && curModel);
  const isProviderOnly = Boolean(curProviderId && !curModel);
  const providerExists = curProviderId ? Boolean(findProviderById(providers, curProviderId)) : true;

  const normalizedCurrent: ProviderModelValue = (() => {
    if (isModelOnly) return { providerId: '', model: '' };
    if (isProviderOnly) {
      if (!includeProviderOnlyOptions) return { providerId: '', model: '' };
      if (mode === 'embedding' && !providerExists) return { providerId: '', model: '' };
      return { providerId: curProviderId, model: '' };
    }
    if (curProviderId && !providerExists && mode === 'embedding') return { providerId: '', model: '' };
    return { providerId: curProviderId, model: curModel };
  })();

  const currentValue = encodeProviderModelValue(normalizedCurrent.providerId, normalizedCurrent.model);

  if (includeModelOnlyOptions && mode === 'chat') {
    const globalModelsRaw = chatModelsMap.get(trimStr(activeProviderId)) ?? [];
    const globalModels = [...new Set(globalModelsRaw.map((x) => trimStr(x)).filter(Boolean))];
    globalModels.sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'));
    for (const m of globalModels) {
      const value = encodeProviderModelValue('', m);
      add({ value, label: `全局（${globalProviderLabel}）：${m}`, sortKey: `0|${m}` });
    }
  }

  for (const p of sortProvidersForOptions(providers)) {
    const pid = trimStr(p.id);
    if (!pid) continue;
    const pLabel = `${formatProviderLabel(p)}${providerDisabledSuffix(p)}`;
    if (includeProviderOnlyOptions) {
      const providerOnlyLabel = mode === 'embedding' ? '自动（使用该Provider默认嵌入模型）' : '自动（模型跟随全局）';
      add({
        value: encodeProviderModelValue(pid, ''),
        label: `${pLabel}：${providerOnlyLabel}`,
        sortKey: `1|${pLabel}`
      });
    }

    if (mode === 'embedding') {
      const dm = trimStr(p.defaultEmbeddingModel);
      if (dm) {
        add({
          value: encodeProviderModelValue(pid, dm),
          label: `${pLabel}：${dm}`,
          sortKey: `2|${pLabel}|${dm}`
        });
      }
      continue;
    }

    const seenModels = new Set<string>();
    const list = chatModelsMap.get(pid) ?? [];
    for (const m0 of list) {
      const m = trimStr(m0);
      if (!m) continue;
      if (seenModels.has(m)) continue;
      seenModels.add(m);
      add({
        value: encodeProviderModelValue(pid, m),
        label: `${pLabel}：${m}`,
        sortKey: `2|${pLabel}|${m}`
      });
    }

    const fallback = trimStr(p.defaultChatModel);
    if (fallback && !seenModels.has(fallback)) {
      add({
        value: encodeProviderModelValue(pid, fallback),
        label: `${pLabel}：${fallback}`,
        sortKey: `2|${pLabel}|${fallback}`
      });
    }
  }

  if (currentValue && !uniq.has(currentValue)) {
    const curProvider = normalizedCurrent.providerId ? findProviderById(providers, normalizedCurrent.providerId) : undefined;
    const curProviderLabel = curProvider
      ? `${formatProviderLabel(curProvider)}${providerDisabledSuffix(curProvider)}`
      : trimStr(normalizedCurrent.providerId);
    const providerOnlyLabel = mode === 'embedding' ? '自动（使用该Provider默认嵌入模型）' : '自动（模型跟随全局）';
    const label = normalizedCurrent.providerId
      ? normalizedCurrent.model
        ? `${curProviderLabel || '—'}：${normalizedCurrent.model}`
        : `${curProviderLabel || '—'}：${providerOnlyLabel}`
      : normalizedCurrent.model
        ? `全局（${globalProviderLabel}）：${normalizedCurrent.model}`
        : autoLabel;
    add({ value: currentValue, label, sortKey: `-1|${label}` });
  }

  const options = Array.from(uniq.values());
  options.sort((a, b) => a.sortKey.localeCompare(b.sortKey, 'zh-Hans-CN'));
  return { options, currentValue, autoLabel };
}

export function ProviderModelSelect(props: {
  providers: AiProviderDTO[];
  activeProviderId: string;
  chatProviders?: AiChatProviderOptionDTO[];
  mode: Mode;
  providerId: string;
  model: string;
  disabled?: boolean;
  label?: string | null;
  includeModelOnlyOptions?: boolean;
  includeProviderOnlyOptions?: boolean;
  autoOptionLabel?: string;
  disableAutoOption?: boolean;
  selectClassName?: string;
  onChange: (next: ProviderModelValue) => void;
}) {
  const {
    providers,
    activeProviderId,
    chatProviders,
    mode,
    providerId,
    model,
    disabled,
    label,
    includeModelOnlyOptions = false,
    includeProviderOnlyOptions = false,
    autoOptionLabel,
    disableAutoOption = false,
    selectClassName,
    onChange
  } = props;

  const { options, currentValue, autoLabel } = useMemo(
    () =>
      buildOptions({
        providers,
        activeProviderId,
        chatProviders,
        mode,
        includeModelOnlyOptions,
        includeProviderOnlyOptions,
        current: { providerId: trimStr(providerId), model: trimStr(model) }
      }),
    [providers, activeProviderId, chatProviders, mode, includeModelOnlyOptions, includeProviderOnlyOptions, providerId, model]
  );

  const selectValue = currentValue || '';

  return (
    <div className="flex flex-col gap-1">
      {label === '' ? null : <label className="text-xs text-gray-600 whitespace-nowrap">{label ?? '模型:'}</label>}
      <select
        className={selectClassName ?? 'w-full border rounded px-3 py-1.5 text-sm bg-white disabled:bg-gray-50'}
        value={selectValue}
        disabled={disabled}
        onChange={(e) => {
          if (disabled) return;
          const v = trimStr(e.target.value);
          const parsed = parseProviderModelValue(v);
          if (!parsed) {
            onChange({ providerId: '', model: '' });
            return;
          }
          if (parsed.providerId === '' && parsed.model) {
            onChange({ providerId: '', model: '' });
            return;
          }
          onChange(parsed);
        }}
      >
        <option value="" disabled={disableAutoOption}>
          {autoOptionLabel ?? autoLabel}
        </option>
        {options.map((it) => (
          <option key={it.value} value={it.value}>
            {it.label}
          </option>
        ))}
      </select>
    </div>
  );
}
