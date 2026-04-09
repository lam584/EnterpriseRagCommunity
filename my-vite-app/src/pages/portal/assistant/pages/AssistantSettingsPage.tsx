import { useEffect, useMemo, useState } from 'react';
import { CircleHelp } from 'lucide-react';
import { getMyAssistantPreferences, updateMyAssistantPreferences } from '../../../../services/assistantPreferencesService';
import { getAiChatOptions, type AiChatOptionsDTO, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { normAssistantValue, pickAssistantModel, pickAssistantProviderId } from './assistantOptionsUtils';

function buildProviderModelValue(providerId: string, model: string): string {
  const p = String(providerId ?? '').trim();
  const m = String(model ?? '').trim();
  if (!p || !m) return '';
  return `${encodeURIComponent(p)}|${encodeURIComponent(m)}`;
}

function parseProviderModelValue(value: string): { providerId: string; model: string } | null {
  const v = String(value ?? '').trim();
  if (!v) return null;
  const idx = v.indexOf('|');
  if (idx <= 0) return null;
  const p = v.slice(0, idx);
  const m = v.slice(idx + 1);
  try {
    const providerId = decodeURIComponent(p).trim();
    const model = decodeURIComponent(m).trim();
    if (!providerId || !model) return null;
    return { providerId, model };
  } catch {
    return null;
  }
}

export default function AssistantSettingsPage() {
  type PrefSnapshot = {
    defaultProviderId: string;
    defaultModel: string;
    defaultDeepThink: boolean;
    autoLoadLastSession: boolean;
    defaultUseRag: boolean;
    ragTopK: number;
    stream: boolean;
    temperature: string;
    topP: string;
    defaultSystemPrompt: string;
  };

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [baseline, setBaseline] = useState<PrefSnapshot | null>(null);

  const [chatOptions, setChatOptions] = useState<AiChatOptionsDTO | null>(null);

  const [defaultProviderId, setDefaultProviderId] = useState('');
  const [defaultModel, setDefaultModel] = useState('');
  const [defaultDeepThink, setDefaultDeepThink] = useState(false);
  const [autoLoadLastSession, setAutoLoadLastSession] = useState(false);
  const [defaultUseRag, setDefaultUseRag] = useState(true);
  const [ragTopK, setRagTopK] = useState(6);
  const [stream, setStream] = useState(true);
  const [temperature, setTemperature] = useState<string>('');
  const [topP, setTopP] = useState<string>('');
  const [defaultSystemPrompt, setDefaultSystemPrompt] = useState<string>('');

  const assistantManualModelSelectionEnabled = chatOptions?.assistantManualModelSelectionEnabled !== false;

  const makeSnapshot = (overrides?: Partial<PrefSnapshot>): PrefSnapshot => {
    return {
      defaultProviderId,
      defaultModel,
      defaultDeepThink,
      autoLoadLastSession,
      defaultUseRag,
      ragTopK,
      stream,
      temperature,
      topP,
      defaultSystemPrompt,
      ...overrides,
    };
  };

  const applySnapshot = (s: PrefSnapshot) => {
    setDefaultProviderId(s.defaultProviderId);
    setDefaultModel(s.defaultModel);
    setDefaultDeepThink(s.defaultDeepThink);
    setAutoLoadLastSession(s.autoLoadLastSession);
    setDefaultUseRag(s.defaultUseRag);
    setRagTopK(s.ragTopK);
    setStream(s.stream);
    setTemperature(s.temperature);
    setTopP(s.topP);
    setDefaultSystemPrompt(s.defaultSystemPrompt);
  };

  const providerOptions = useMemo(() => {
    const providers = (chatOptions?.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
    const enabled = providers.filter((p) => {
      const id = String(p.id ?? '').trim();
      return Boolean(id);
    });
    const copy = [...enabled];
    copy.sort((a, b) => {
      const la = String(a.name ?? '').trim() ? `${String(a.name).trim()} (${String(a.id ?? '').trim()})` : String(a.id ?? '').trim();
      const lb = String(b.name ?? '').trim() ? `${String(b.name).trim()} (${String(b.id ?? '').trim()})` : String(b.id ?? '').trim();
      return la.localeCompare(lb, 'zh-Hans-CN');
    });
    return copy;
  }, [chatOptions]);

  const flatModelOptions = useMemo(() => {
    const uniq: { providerId: string; providerLabel: string; model: string; value: string }[] = [];
    const seen = new Set<string>();
    for (const p of providerOptions) {
      const providerId = String(p.id ?? '').trim();
      if (!providerId) continue;
      const providerName = String(p.name ?? '').trim();
      const providerLabel = providerName ? `${providerName} (${providerId})` : providerId;
      const rows = Array.isArray(p.chatModels) ? p.chatModels.filter(Boolean) : [];
      for (const m of rows) {
        const modelName = String((m as { name?: unknown }).name ?? '').trim();
        if (!modelName) continue;
        const key = `${providerId}::${modelName}`;
        if (seen.has(key)) continue;
        seen.add(key);
        uniq.push({
          providerId,
          providerLabel,
          model: modelName,
          value: buildProviderModelValue(providerId, modelName),
        });
      }
    }
    uniq.sort((a, b) => {
      const pCmp = a.providerLabel.localeCompare(b.providerLabel, 'zh-Hans-CN');
      if (pCmp !== 0) return pCmp;
      return a.model.localeCompare(b.model, 'zh-Hans-CN');
    });
    return uniq;
  }, [providerOptions]);

  const selectedProviderModelValue = useMemo(
    () => buildProviderModelValue(defaultProviderId, defaultModel),
    [defaultProviderId, defaultModel]
  );

  const settingsModelOptions = useMemo(() => {
    if (!selectedProviderModelValue) return flatModelOptions;
    if (flatModelOptions.some((x) => x.value === selectedProviderModelValue)) return flatModelOptions;
    if (!String(defaultProviderId ?? '').trim() || !String(defaultModel ?? '').trim()) return flatModelOptions;
    return [
      {
        providerId: String(defaultProviderId ?? '').trim(),
        providerLabel: String(defaultProviderId ?? '').trim(),
        model: String(defaultModel ?? '').trim(),
        value: selectedProviderModelValue,
      },
      ...flatModelOptions,
    ];
  }, [flatModelOptions, selectedProviderModelValue, defaultProviderId, defaultModel]);

  useEffect(() => {
    let mounted = true;
    void (async () => {
      setLoading(true);
      setError(null);
      try {
        const [prefs, opt] = await Promise.all([getMyAssistantPreferences(), getAiChatOptions()]);
        if (!mounted) return;
        setChatOptions(opt);

        const providers = (opt.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
        const storedProvider = normAssistantValue(prefs.defaultProviderId);
        const storedModel = normAssistantValue(prefs.defaultModel);
        const allowManual = opt.assistantManualModelSelectionEnabled !== false;
        const nextProviderId = allowManual ? pickAssistantProviderId(opt, providers, storedProvider) : '';
        const p = providers.find((x) => normAssistantValue(x.id) === nextProviderId) ?? null;
        const nextModel = allowManual ? pickAssistantModel(p, storedModel) : '';

        setDefaultProviderId(nextProviderId);
        setDefaultModel(nextModel);
        setDefaultDeepThink(!!prefs.defaultDeepThink);
        setAutoLoadLastSession(!!prefs.autoLoadLastSession);
        setDefaultUseRag(!!prefs.defaultUseRag);
        const nextRagTopK = Number.isFinite(prefs.ragTopK) ? Math.max(1, Math.min(50, Number(prefs.ragTopK))) : 6;
        setRagTopK(nextRagTopK);
        setStream(typeof prefs.stream === 'boolean' ? prefs.stream : true);
        const nextTemperature = typeof prefs.temperature === 'number' && Number.isFinite(prefs.temperature) ? String(prefs.temperature) : '';
        const nextTopP = typeof prefs.topP === 'number' && Number.isFinite(prefs.topP) ? String(prefs.topP) : '';
        const nextDefaultSystemPrompt = typeof prefs.defaultSystemPrompt === 'string' ? prefs.defaultSystemPrompt : '';
        setTemperature(nextTemperature);
        setTopP(nextTopP);
        setDefaultSystemPrompt(nextDefaultSystemPrompt);

        setBaseline({
          defaultProviderId: nextProviderId,
          defaultModel: nextModel,
          defaultDeepThink: !!prefs.defaultDeepThink,
          autoLoadLastSession: !!prefs.autoLoadLastSession,
          defaultUseRag: !!prefs.defaultUseRag,
          ragTopK: nextRagTopK,
          stream: typeof prefs.stream === 'boolean' ? prefs.stream : true,
          temperature: nextTemperature,
          topP: nextTopP,
          defaultSystemPrompt: nextDefaultSystemPrompt,
        });
        setIsEditing(false);
      } catch (e) {
        if (!mounted) return;
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSavedHint(null);
    try {
      const saved = await updateMyAssistantPreferences({
        defaultProviderId: assistantManualModelSelectionEnabled && defaultProviderId ? defaultProviderId : null,
        defaultModel: assistantManualModelSelectionEnabled && defaultModel ? defaultModel : null,
        defaultDeepThink,
        autoLoadLastSession,
        defaultUseRag,
        ragTopK: Math.max(1, Math.min(50, Number(ragTopK) || 6)),
        stream,
        temperature: temperature.trim() ? Math.max(0, Math.min(2, Number(temperature))) : null,
        topP: topP.trim() ? Math.max(0, Math.min(1, Number(topP))) : null,
        defaultSystemPrompt: defaultSystemPrompt.trim() ? defaultSystemPrompt : null,
      });
      setSavedHint('已保存');
      const nextRagTopK = Number.isFinite(saved.ragTopK) ? Math.max(1, Math.min(50, Number(saved.ragTopK))) : 6;
      const nextStream = typeof saved.stream === 'boolean' ? saved.stream : true;
      const nextTemperature = typeof saved.temperature === 'number' && Number.isFinite(saved.temperature) ? String(saved.temperature) : '';
      const nextTopP = typeof saved.topP === 'number' && Number.isFinite(saved.topP) ? String(saved.topP) : '';
      const nextDefaultSystemPrompt = typeof saved.defaultSystemPrompt === 'string' ? saved.defaultSystemPrompt : '';

      const nextSnapshot = makeSnapshot({
        defaultDeepThink: !!saved.defaultDeepThink,
        autoLoadLastSession: !!saved.autoLoadLastSession,
        defaultUseRag: !!saved.defaultUseRag,
        ragTopK: nextRagTopK,
        stream: nextStream,
        temperature: nextTemperature,
        topP: nextTopP,
        defaultSystemPrompt: nextDefaultSystemPrompt,
      });

      applySnapshot(nextSnapshot);
      setBaseline(nextSnapshot);
      setIsEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const readOnly = !isEditing || loading || saving;

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (!readOnly) void handleSave();
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">设置</h3>
          <p className="text-sm text-gray-600">这些设置会保存在数据库中，并作为聊天页的默认行为。</p>
        </div>
        <div className="flex items-center gap-2">
          {!isEditing ? (
            <button
              type="button"
              className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
              disabled={loading || saving || !baseline}
              onClick={() => {
                setError(null);
                setSavedHint(null);
                setIsEditing(true);
              }}
            >
              编辑
            </button>
          ) : (
            <>
              <button
                type="button"
                className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                disabled={saving || loading || !baseline}
                onClick={() => {
                  if (!baseline) return;
                  applySnapshot(baseline);
                  setError(null);
                  setSavedHint(null);
                  setIsEditing(false);
                }}
              >
                取消
              </button>
              <button
                type="submit"
                className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700 disabled:bg-blue-300"
                disabled={saving || loading}
              >
                {saving ? '保存中...' : '保存'}
              </button>
            </>
          )}
        </div>
      </div>

      {error ? <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}
      {savedHint ? <div className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-800">{savedHint}</div> : null}
      {loading ? <div className="text-sm text-gray-600">加载中...</div> : null}

      <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-4">
        <div className="text-sm font-medium text-gray-900">模型与采样</div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="lg:col-span-2">
            <div className="text-sm text-gray-700 mb-1">默认模型</div>
            {!assistantManualModelSelectionEnabled ? (
              <div className="mb-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                管理员已关闭“智能助手手动选模”，此处固定为自动路由。
              </div>
            ) : null}
            <select
              value={selectedProviderModelValue}
              onChange={(e) => {
                const parsed = parseProviderModelValue(String(e.target.value ?? ''));
                if (!parsed) {
                  setDefaultProviderId('');
                  setDefaultModel('');
                  return;
                }
                setDefaultProviderId(parsed.providerId);
                setDefaultModel(parsed.model);
              }}
              className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
              disabled={readOnly || !assistantManualModelSelectionEnabled}
            >
              <option value="">自动（均衡负载）</option>
              {settingsModelOptions.map((it) => (
                <option key={it.value} value={it.value}>
                  {it.providerLabel}：{it.model}
                </option>
              ))}
            </select>
            <div className="mt-1 text-xs text-gray-500">留空表示自动选择（更适合均衡负载与默认场景）。</div>
          </div>

          <div>
            <label className="flex items-center gap-1 text-sm font-medium text-gray-700 mb-1">
              <span>温度（Temperature）</span>
              <button
                type="button"
                className="p-0.5 rounded text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
                title="控制回复的随机性：越低越稳定，越高越发散。范围 0~2；留空=使用默认值。"
                aria-label="温度（Temperature）说明"
              >
                <CircleHelp size={14} />
              </button>
            </label>
            <input
              type="number"
              value={temperature}
              onChange={(e) => setTemperature(String(e.target.value ?? ''))}
              step={0.1}
              min={0}
              max={2}
              className="w-full max-w-56 rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
              disabled={readOnly}
              placeholder="留空=默认"
            />
          </div>

          <div>
            <label className="flex items-center gap-1 text-sm font-medium text-gray-700 mb-1">
              <span>TOP-P</span>
              <button
                type="button"
                className="p-0.5 rounded text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
                title="核采样阈值：模型只在累计概率 Top-P 的候选中采样。越低越保守，越高越多样。范围 0~1；留空=使用默认值。"
                aria-label="TOP-P 说明"
              >
                <CircleHelp size={14} />
              </button>
            </label>
            <input
              type="number"
              value={topP}
              onChange={(e) => setTopP(String(e.target.value ?? ''))}
              step={0.05}
              min={0}
              max={1}
              className="w-full max-w-56 rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
              disabled={readOnly}
              placeholder="留空=默认"
            />
          </div>
        </div>

        <label className="inline-flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={stream}
            onChange={(e) => setStream(e.target.checked)}
            disabled={readOnly}
            className="h-4 w-4 rounded border-gray-300"
          />
          流式输出
        </label>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-3">
        <div className="text-sm font-medium text-gray-900">默认提示词</div>
        <div>
          <div className="text-sm text-gray-700 mb-1">System Prompt</div>
          <textarea
            value={defaultSystemPrompt}
            onChange={(e) => setDefaultSystemPrompt(String(e.target.value ?? ''))}
            rows={5}
            className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
            disabled={readOnly}
            placeholder="留空则使用系统默认提示词。"
          />
          <div className="mt-1 text-xs text-gray-500">建议写清楚角色、语气与输出格式要求；避免包含敏感信息。</div>
        </div>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-4">
        <div className="text-sm font-medium text-gray-900">RAG 与行为</div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="flex flex-col gap-3">
            <label className="inline-flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={defaultUseRag}
                onChange={(e) => setDefaultUseRag(e.target.checked)}
                disabled={readOnly}
                className="h-4 w-4 rounded border-gray-300"
              />
              默认开启 RAG 功能
            </label>

            <label className="inline-flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={autoLoadLastSession}
                onChange={(e) => setAutoLoadLastSession(e.target.checked)}
                disabled={readOnly}
                className="h-4 w-4 rounded border-gray-300"
              />
              打开聊天页时自动加载上一轮对话
            </label>

            <label className="inline-flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={defaultDeepThink}
                onChange={(e) => setDefaultDeepThink(e.target.checked)}
                disabled={readOnly}
                className="h-4 w-4 rounded border-gray-300"
              />
              默认深度思考
            </label>
          </div>

          <div>
            <label className="flex items-center gap-3">
              <span className="text-sm font-medium text-gray-700 whitespace-nowrap">TopK（检索条数）</span>
              <input
                type="number"
                value={ragTopK}
                onChange={(e) => setRagTopK(Number(e.target.value))}
                min={1}
                max={50}
                className="w-28 rounded border border-gray-300 bg-white px-3 py-2 text-sm text-right focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed"
                disabled={readOnly}
              />
            </label>
            <div className="mt-1 text-xs text-gray-500">数值越大检索越多，但可能带来更高延迟与噪声。</div>
          </div>
        </div>
      </div>
    </form>
  );
}

