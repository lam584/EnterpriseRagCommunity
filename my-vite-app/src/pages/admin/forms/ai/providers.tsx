import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import {
  adminAddProviderModel,
  adminDeleteProviderModel,
  adminFetchUpstreamModels,
  adminGetAiProvidersConfig,
  adminListProviderModels,
  adminPreviewUpstreamModels,
  adminUpdateAiProvidersConfig,
  type AiProviderDTO,
  type AiProviderModelsDTO,
  type AiUpstreamModelsDTO,
  type AiProvidersConfigDTO,
} from '../../../../services/aiProvidersAdminService';

type HeaderRow = { rowId: string; k: string; v: string };

type ProviderForm = Omit<AiProviderDTO, 'extraHeaders'> & {
  rowId: string;
  extraHeadersRows: HeaderRow[];
};

type ConfigForm = {
  activeProviderId: string;
  providers: ProviderForm[];
};

type ModelPurpose = 'MULTIMODAL_CHAT' | 'EMBEDDING' | 'RERANK';

const SUPPORTED_CAPABILITY_FILTERS = new Set(['推理', '嵌入', '重排', '视觉', '工具']);

function inferModelCapabilityTags(modelName: string): string[] {
  const name = modelName.trim().toLowerCase();
  if (!name) return [];
  const isEmbedding = name.includes('embed') || name.includes('embedding');
  const isRerank = name.includes('rerank') || name.includes('reranker') || /\brerank\b/i.test(name);
  const isVision =
    name.includes('vision') ||
    name.includes('multimodal') ||
    name.includes('qwen-vl') ||
    name.includes('llava') ||
    /(^|[-_])vl($|[-_])/.test(name) ||
    (name.includes('gpt') && (name.includes('4o') || name.includes('4.1')));
  const isTool = name.includes('tool') || name.includes('tools') || name.includes('function') || /\bfc\b/.test(name);

  const tags: string[] = [];
  if (isEmbedding) tags.push('嵌入');
  if (isRerank) tags.push('重排');
  if (!isEmbedding && !isRerank) tags.push('推理');
  if (isVision) tags.push('视觉');
  if (isTool) tags.push('工具');
  return tags;
}

function getModelPurposeWarning(requestedPurpose: ModelPurpose, modelName: string): string | null {
  if (requestedPurpose !== 'MULTIMODAL_CHAT') return null;
  const tags = inferModelCapabilityTags(modelName);
  if (tags.includes('视觉')) return null;
  return '该模型名未识别出显式视觉能力；仍可加入多模态模型列表，但建议优先选择原生多模态模型。';
}

type ProviderModelsState = {
  loading: boolean;
  error: string | null;
  modelsByPurpose: Record<ModelPurpose, string[]>;
};

type AddModelsModalState = {
  open: boolean;
  providerId: string;
  providerIndex: number;
  purpose: ModelPurpose;
  capability?: string;
};

type ProviderPreset = {
  key: string;
  label: string;
  idPrefix: string;
  type: 'OPENAI_COMPAT' | 'LOCAL_OPENAI_COMPAT';
  baseUrl: string;
  description: string;
};

const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    key: 'llm-studio',
    label: 'LLM Studio',
    idPrefix: 'llm-studio',
    type: 'LOCAL_OPENAI_COMPAT',
    baseUrl: 'http://localhost:1234/v1',
    description: '本地 OpenAI 兼容服务，默认端口 1234',
  },
  {
    key: 'ollama',
    label: 'Ollama',
    idPrefix: 'ollama',
    type: 'LOCAL_OPENAI_COMPAT',
    baseUrl: 'http://localhost:11434/v1',
    description: '本地 OpenAI 兼容接口（需要开启/使用 /v1）',
  },
  {
    key: 'nvidia',
    label: '英伟达（NVIDIA）',
    idPrefix: 'nvidia',
    type: 'OPENAI_COMPAT',
    baseUrl: 'https://integrate.api.nvidia.com/v1',
    description: 'NVIDIA OpenAI 兼容网关（通常需要 API Key）',
  },
  {
    key: 'aliyun',
    label: '阿里云（DashScope）',
    idPrefix: 'aliyun',
    type: 'OPENAI_COMPAT',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    description: 'DashScope OpenAI 兼容模式（通常需要 API Key）',
  },
];

function normalizeString(v: unknown): string {
  return typeof v === 'string' ? v : '';
}

function mapHeadersToRows(extraHeaders: Record<string, string> | null | undefined): HeaderRow[] {
  const rows: HeaderRow[] = [];
  const entries = extraHeaders ? Object.entries(extraHeaders) : [];
  for (let i = 0; i < entries.length; i++) {
    const [k, v] = entries[i];
    rows.push({
      rowId: `${Date.now()}-${i}-${Math.random().toString(16).slice(2)}`,
      k: k ?? '',
      v: v ?? '',
    });
  }
  return rows;
}

function newRowId(): string {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function providerRowId(providerId: string): string {
  const pid = providerId.trim();
  return pid ? `provider:${pid}` : newRowId();
}

function rowsToHeaders(rows: HeaderRow[]): Record<string, string> | null {
  const out: Record<string, string> = {};
  for (const r of rows) {
    const k = r.k.trim();
    if (!k) continue;
    out[k] = r.v;
  }
  return Object.keys(out).length ? out : null;
}

function mapDtoToForm(dto?: AiProvidersConfigDTO | null): ConfigForm {
  const providers: ProviderForm[] = (dto?.providers ?? [])
    .filter(Boolean)
    .map((p) => ({
      rowId: providerRowId(p.id ?? ''),
      id: p.id ?? '',
      name: p.name ?? '',
      type: p.type ?? 'OPENAI_COMPAT',
      baseUrl: p.baseUrl ?? '',
      apiKey: p.apiKey ?? '',
      defaultChatModel: p.defaultChatModel ?? '',
      defaultEmbeddingModel: p.defaultEmbeddingModel ?? '',
      defaultRerankModel: p.defaultRerankModel ?? '',
      rerankEndpointPath: p.rerankEndpointPath ?? '',
      supportsVision: p.supportsVision ?? false,
      connectTimeoutMs: p.connectTimeoutMs ?? null,
      readTimeoutMs: p.readTimeoutMs ?? null,
      enabled: p.enabled ?? true,
      extraHeadersRows: mapHeadersToRows(p.extraHeaders),
    }));

  return {
    activeProviderId: normalizeString(dto?.activeProviderId) || (providers[0]?.id ?? ''),
    providers,
  };
}

function buildPayload(form: ConfigForm): AiProvidersConfigDTO {
  return {
    activeProviderId: form.activeProviderId.trim() ? form.activeProviderId.trim() : null,
    providers: form.providers.map((p) => ({
      id: p.id?.trim() ? p.id.trim() : null,
      name: p.name?.trim() ? p.name.trim() : null,
      type: p.type?.trim() ? p.type.trim() : null,
      baseUrl: p.baseUrl?.trim() ? p.baseUrl.trim() : null,
      apiKey: p.apiKey ?? null,
      defaultChatModel: p.defaultChatModel?.trim() ? p.defaultChatModel.trim() : null,
      defaultEmbeddingModel: p.defaultEmbeddingModel?.trim() ? p.defaultEmbeddingModel.trim() : null,
      defaultRerankModel: p.defaultRerankModel?.trim() ? p.defaultRerankModel.trim() : null,
      rerankEndpointPath: p.rerankEndpointPath?.trim() ? p.rerankEndpointPath.trim() : null,
      supportsVision: Boolean(p.supportsVision),
      extraHeaders: rowsToHeaders(p.extraHeadersRows),
      connectTimeoutMs: p.connectTimeoutMs ?? null,
      readTimeoutMs: p.readTimeoutMs ?? null,
      enabled: p.enabled ?? true,
    })),
  };
}

function validate(form: ConfigForm): string[] {
  const errors: string[] = [];
  const ids = new Set<string>();
  for (const p of form.providers) {
    const id = (p.id ?? '').trim();
    if (!id) {
      errors.push('provider.id 不能为空');
      continue;
    }
    if (ids.has(id)) errors.push(`provider.id 重复：${id}`);
    ids.add(id);
    const type = (p.type ?? '').trim();
    if (!type) errors.push(`provider(${id}).type 不能为空`);
  }
  return errors;
}

function ensureUniqueProviderId(existing: ProviderForm[], base: string): string {
  const used = new Set(existing.map((p) => (p.id ?? '').trim()).filter(Boolean));
  const baseId = base.trim() ? base.trim() : 'provider';
  let next = baseId;
  let i = 2;
  while (used.has(next)) {
    next = `${baseId}-${i}`;
    i++;
  }
  return next;
}

export default function AiProvidersForm() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [form, setForm] = useState<ConfigForm>({ activeProviderId: '', providers: [] });
  const [providerModels, setProviderModels] = useState<Record<string, ProviderModelsState>>({});
  const [persistedProviderIds, setPersistedProviderIds] = useState<string[]>([]);
  const [addModelsModal, setAddModelsModal] = useState<AddModelsModalState>({
    open: false,
    providerId: '',
    providerIndex: -1,
    purpose: 'MULTIMODAL_CHAT',
    capability: '',
  });

  const emptyProviderModelsState = useCallback((): ProviderModelsState => {
    return {
      loading: false,
      error: null,
      modelsByPurpose: { MULTIMODAL_CHAT: [], EMBEDDING: [], RERANK: [] },
    };
  }, []);

  const mapModelsDtoToState = useCallback(
    (dto: AiProviderModelsDTO): ProviderModelsState => {
      const next: ProviderModelsState = {
        loading: false,
        error: null,
        modelsByPurpose: { MULTIMODAL_CHAT: [], EMBEDDING: [], RERANK: [] },
      };
      const rows = dto.models ?? [];
      for (const r of rows) {
        if (!r) continue;
        const name = typeof r.modelName === 'string' ? r.modelName.trim() : '';
        if (!name) continue;
        const rawPurpose = typeof r.purpose === 'string' ? r.purpose.trim().toUpperCase() : '';
        const purpose: ModelPurpose | null = (() => {
          if (rawPurpose === 'MULTIMODAL_CHAT' || rawPurpose === 'RERANK') return rawPurpose as ModelPurpose;
          if (rawPurpose === 'EMBEDDING' || rawPurpose === 'POST_EMBEDDING' || rawPurpose === 'SIMILARITY_EMBEDDING') return 'EMBEDDING';
          if (rawPurpose === 'TEXT_CHAT' || rawPurpose === 'IMAGE_CHAT' || rawPurpose === 'CHAT') return 'MULTIMODAL_CHAT';
          return null;
        })();
        if (!purpose) continue;
        if (r.enabled === false) continue;
        next.modelsByPurpose[purpose].push(name);
      }
      for (const k of Object.keys(next.modelsByPurpose) as ModelPurpose[]) {
        next.modelsByPurpose[k] = Array.from(new Set(next.modelsByPurpose[k])).sort((a, b) => a.localeCompare(b));
      }
      return next;
    },
    []
  );

  const providersSorted = useMemo(() => {
    const copy = [...form.providers];
    copy.sort((a, b) => (a.id ?? '').localeCompare(b.id ?? ''));
    return copy;
  }, [form.providers]);

  const refreshProviderModels = useCallback(
    async (providerId: string) => {
      const pid = providerId.trim();
      if (!pid) return;
      setProviderModels((prev) => ({
        ...prev,
        [pid]: { ...(prev[pid] ?? emptyProviderModelsState()), loading: true, error: null },
      }));
      try {
        const dto = await adminListProviderModels(pid);
        setProviderModels((prev) => ({ ...prev, [pid]: { ...mapModelsDtoToState(dto), loading: false } }));
      } catch (e) {
        setProviderModels((prev) => ({
          ...prev,
          [pid]: { ...(prev[pid] ?? emptyProviderModelsState()), loading: false, error: e instanceof Error ? e.message : '加载模型列表失败' },
        }));
      }
    },
    [emptyProviderModelsState, mapModelsDtoToState]
  );

  const applyLoadedConfig = useCallback(
    async (cfg: Awaited<ReturnType<typeof adminGetAiProvidersConfig>>) => {
      const nextForm = mapDtoToForm(cfg);
      setForm(nextForm);
      const ids = Array.from(new Set(nextForm.providers.map((p) => (p.id ?? '').trim()).filter(Boolean)));
      setPersistedProviderIds(ids);
      await Promise.all(ids.map((id) => refreshProviderModels(id)));
    },
    [refreshProviderModels]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const cfg = await adminGetAiProvidersConfig();
      await applyLoadedConfig(cfg);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [applyLoadedConfig]);

  useEffect(() => {
    load();
  }, [load]);

  const onSave = async (): Promise<boolean> => {
    setError(null);
    setSuccess(null);
    const errors = validate(form);
    if (errors.length) {
      setError(errors.join('；'));
      return false;
    }
    setSaving(true);
    try {
      const payload = buildPayload(form);
      const saved = await adminUpdateAiProvidersConfig(payload);
      await applyLoadedConfig(saved);
      setSuccess('保存成功');
      try {
        window.localStorage.removeItem('llm-routing-config.provider-models-map.v2');
      } catch {}
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
      return false;
    } finally {
      setSaving(false);
    }
  };

  const updateProvider = (idx: number, patch: Partial<ProviderForm>) => {
    setForm((prev) => {
      const next = [...prev.providers];
      next[idx] = { ...next[idx], ...patch };
      return { ...prev, providers: next };
    });
  };

  const removeProvider = (idx: number) => {
    setForm((prev) => {
      const next = prev.providers.filter((_, i) => i !== idx);
      const active = prev.activeProviderId;
      const stillActive = next.some((p) => (p.id ?? '').trim() === active.trim());
      return { ...prev, providers: next, activeProviderId: stillActive ? active : (next[0]?.id ?? '') };
    });
  };

  const addProvider = (): string => {
    let createdRowId = '';
    setForm((prev) => {
      const base = `provider-${prev.providers.length + 1}`;
      const id = ensureUniqueProviderId(prev.providers, base);
      createdRowId = providerRowId(id);
      return {
        ...prev,
        providers: [
          ...prev.providers,
          {
            rowId: createdRowId,
            id,
            name: '',
            type: 'OPENAI_COMPAT',
            baseUrl: '',
            apiKey: '',
            defaultChatModel: '',
            defaultEmbeddingModel: '',
            defaultRerankModel: '',
            supportsVision: false,
            connectTimeoutMs: null,
            readTimeoutMs: null,
            enabled: true,
            extraHeadersRows: [],
          },
        ],
      };
    });
    return createdRowId;
  };

  const typeOptions = [
    { value: 'OPENAI_COMPAT', label: 'OpenAI 兼容' },
    { value: 'LOCAL_OPENAI_COMPAT', label: '本地模型（OpenAI 兼容）' },
  ];

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-4 space-y-2">
        <h3 className="text-lg font-semibold">模型提供商</h3>
        <div className="text-sm text-gray-600">加载中…</div>
      </div>
    );
  }

  const getModelsForProvider = (providerId: string, purpose: ModelPurpose): string[] => {
    const pid = providerId.trim();
    const state = providerModels[pid];
    return state?.modelsByPurpose[purpose] ?? [];
  };

  const getProviderModelsState = (providerId: string): ProviderModelsState => {
    const pid = providerId.trim();
    return providerModels[pid] ?? emptyProviderModelsState();
  };

  const openAddModels = (providerId: string, providerIndex: number, purpose: ModelPurpose, capability?: string) => {
    setAddModelsModal({ open: true, providerId, providerIndex, purpose, capability: capability ?? '' });
  };

  return (
    <AiProvidersFormInner
      form={form}
      setForm={setForm}
      saving={saving}
      error={error}
      success={success}
      providersSorted={providersSorted}
      load={load}
      onSave={onSave}
      addProvider={addProvider}
      updateProvider={updateProvider}
      removeProvider={removeProvider}
      typeOptions={typeOptions}
      getModelsForProvider={getModelsForProvider}
      getProviderModelsState={getProviderModelsState}
      openAddModels={openAddModels}
      addModelsModal={addModelsModal}
      setAddModelsModal={setAddModelsModal}
      mapModelsDtoToState={mapModelsDtoToState}
      setProviderModels={setProviderModels}
      persistedProviderIds={persistedProviderIds}
      refreshProviderModels={refreshProviderModels}
    />
  );
}

type InnerProps = {
  form: ConfigForm;
  setForm: Dispatch<SetStateAction<ConfigForm>>;
  saving: boolean;
  error: string | null;
  success: string | null;
  providersSorted: ProviderForm[];
  load: () => Promise<void>;
  onSave: () => Promise<boolean>;
  addProvider: () => string;
  updateProvider: (idx: number, patch: Partial<ProviderForm>) => void;
  removeProvider: (idx: number) => void;
  typeOptions: { value: string; label: string }[];
  getModelsForProvider: (providerId: string, purpose: ModelPurpose) => string[];
  getProviderModelsState: (providerId: string) => ProviderModelsState;
  openAddModels: (providerId: string, providerIndex: number, purpose: ModelPurpose, capability?: string) => void;
  addModelsModal: AddModelsModalState;
  setAddModelsModal: (next: AddModelsModalState) => void;
  mapModelsDtoToState: (dto: AiProviderModelsDTO) => ProviderModelsState;
  setProviderModels: Dispatch<SetStateAction<Record<string, ProviderModelsState>>>;
  persistedProviderIds: string[];
  refreshProviderModels: (providerId: string) => Promise<void>;
};

function AiProvidersFormInner(props: InnerProps) {
  const {
    form,
    setForm,
    saving,
    error,
    success,
    providersSorted,
    load,
    onSave,
    addProvider,
    updateProvider,
    removeProvider,
    typeOptions,
    getModelsForProvider,
    getProviderModelsState,
    openAddModels,
    addModelsModal,
    setAddModelsModal,
    mapModelsDtoToState,
    setProviderModels,
    persistedProviderIds,
  } = props;

  const [isEditing, setIsEditing] = useState(false);
  const baselineRef = useRef<string>('');
  const autoSaveRequestedRef = useRef(false);
  const [expandedProviderRowIds, setExpandedProviderRowIds] = useState<Record<string, boolean>>({});
  const shouldCollapseProviders = form.providers.length > 1;

  useEffect(() => {
    if (!isEditing) baselineRef.current = JSON.stringify(form);
  }, [form, isEditing]);

  useEffect(() => {
    setExpandedProviderRowIds((prev) => {
      const existing = new Set(form.providers.map((p) => p.rowId));
      let changed = false;
      const next: Record<string, boolean> = {};
      for (const [k, v] of Object.entries(prev)) {
        if (!existing.has(k)) {
          changed = true;
          continue;
        }
        next[k] = v;
      }
      return changed ? next : prev;
    });
  }, [form.providers]);

  const isProviderExpanded = useCallback(
    (rowId: string) => {
      if (!shouldCollapseProviders) return true;
      return expandedProviderRowIds[rowId] === true;
    },
    [expandedProviderRowIds, shouldCollapseProviders]
  );

  const hasUnsavedChanges = useMemo(() => {
    if (!isEditing) return false;
    return JSON.stringify(form) !== baselineRef.current;
  }, [form, isEditing]);

  useEffect(() => {
    if (!autoSaveRequestedRef.current) return;
    if (saving) return;
    autoSaveRequestedRef.current = false;
    void onSave();
  }, [form, onSave, saving]);

  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);
  const [modalSuccess, setModalSuccess] = useState<string | null>(null);
  const [modalManualName, setModalManualName] = useState('');
  const [modalFilter, setModalFilter] = useState('');
  const [modalCapability, setModalCapability] = useState('');
  const [upstreamModels, setUpstreamModels] = useState<string[]>([]);
  const [presetModalOpen, setPresetModalOpen] = useState(false);

  const persistedProviderSet = useMemo(() => {
    return new Set(persistedProviderIds.map((x) => x.trim()).filter(Boolean));
  }, [persistedProviderIds]);

  const isProviderPersisted = useCallback(
    (providerId: string) => {
      const pid = providerId.trim();
      if (!pid) return false;
      return persistedProviderSet.has(pid);
    },
    [persistedProviderSet]
  );

  const purposeLabel = (p: ModelPurpose): string => {
    if (p === 'MULTIMODAL_CHAT') return '多模态模型';
    if (p === 'EMBEDDING') return '文本嵌入模型';
    return '重排模型';
  };

  const modelCapabilityTags = useCallback((modelName: string): string[] => {
    return inferModelCapabilityTags(modelName);
  }, []);

  const fetchUpstream = useCallback(
    async (providerId: string, providerIndex: number) => {
      const pid = providerId.trim();
      if (!pid) return;
      const provider =
        form.providers[providerIndex] && (form.providers[providerIndex].id ?? '').trim() === pid
          ? form.providers[providerIndex]
          : form.providers.find((p) => (p.id ?? '').trim() === pid);
      const baseUrl = (provider?.baseUrl ?? '').trim();
      setModalLoading(true);
      setModalError(null);
      try {
        if (!baseUrl) {
          setModalError('请先填写接口地址，再拉取模型列表。');
          setUpstreamModels([]);
          return;
        }

        const baseline = (() => {
          try {
            return JSON.parse(baselineRef.current) as ConfigForm;
          } catch {
            return null;
          }
        })();
        const baselineProvider = baseline?.providers?.find((p) => (p.id ?? '').trim() === pid) ?? null;
        const baselineBaseUrl = (baselineProvider?.baseUrl ?? '').trim();
        const usesMaskedApiKey = (provider?.apiKey ?? '').trim() === '******';
        const extraHeaders = rowsToHeaders(provider?.extraHeadersRows ?? []) ?? null;
        const normalizedExtraHeaders = (() => {
          if (!extraHeaders) return null;
          const out: Record<string, string> = {};
          for (const [k, v] of Object.entries(extraHeaders)) {
            const key = (k ?? '').trim();
            const val = v ?? '';
            if (!key) continue;
            if (typeof val !== 'string') continue;
            if (val.trim() === '******') continue;
            out[key] = val;
          }
          return Object.keys(out).length ? out : null;
        })();

        const canUseSavedConfig =
          isProviderPersisted(pid) && baselineBaseUrl && baselineBaseUrl === baseUrl && usesMaskedApiKey && !normalizedExtraHeaders;

        const dto: AiUpstreamModelsDTO = canUseSavedConfig
          ? await adminFetchUpstreamModels(pid)
          : await adminPreviewUpstreamModels({
              providerId: pid,
              baseUrl,
              apiKey: usesMaskedApiKey ? null : (provider?.apiKey ?? null),
              extraHeaders: normalizedExtraHeaders,
              connectTimeoutMs: provider?.connectTimeoutMs ?? null,
              readTimeoutMs: provider?.readTimeoutMs ?? null,
            });
        const list = (dto.models ?? []).filter((x): x is string => typeof x === 'string').map((x) => x.trim()).filter(Boolean);
        setUpstreamModels(Array.from(new Set(list)).sort((a, b) => a.localeCompare(b)));
      } catch (e) {
        setModalError(e instanceof Error ? e.message : '获取 /v1/models 失败');
        setUpstreamModels([]);
      } finally {
        setModalLoading(false);
      }
    },
    [form.providers, isProviderPersisted]
  );

  useEffect(() => {
    if (!addModelsModal.open) return;
    setModalError(null);
    setModalSuccess(null);
    setModalManualName('');
    setModalFilter('');
    const hinted = (addModelsModal.capability ?? '').trim();
    setModalCapability(
      (SUPPORTED_CAPABILITY_FILTERS.has(hinted) ? hinted : '') ||
        (addModelsModal.purpose === 'EMBEDDING'
          ? '嵌入'
          : addModelsModal.purpose === 'RERANK'
            ? '重排'
            : '')
    );
    setUpstreamModels([]);
    fetchUpstream(addModelsModal.providerId, addModelsModal.providerIndex);
  }, [
    addModelsModal.open,
    addModelsModal.providerId,
    addModelsModal.providerIndex,
    addModelsModal.purpose,
    addModelsModal.capability,
    fetchUpstream,
  ]);

  const closeModal = () => {
    setAddModelsModal({ open: false, providerId: '', providerIndex: -1, purpose: 'MULTIMODAL_CHAT', capability: '' });
  };

  const addModel = async (providerId: string, purpose: ModelPurpose, modelName: string) => {
    if (!isEditing) return;
    const pid = providerId.trim();
    const name = modelName.trim();
    if (!pid || !name) return;
    const actualPurpose = purpose;
    const purposeWarning = getModelPurposeWarning(actualPurpose, name);

    if (purposeWarning) {
      const confirmed = window.confirm(`${purposeWarning}\n\n模型：${name}\n目标列表：${purposeLabel(actualPurpose)}\n\n确认仍然添加？`);
      if (!confirmed) return;
    }

    if (getModelsForProvider(pid, actualPurpose).includes(name)) {
      setModalSuccess(`已存在（${purposeLabel(actualPurpose)}）：${name}`);
      setModalManualName('');
      return;
    }
    setModalLoading(true);
    setModalError(null);
    setModalSuccess(null);
    try {
      if (actualPurpose === 'EMBEDDING') {
        const dto1 = await adminAddProviderModel(pid, 'POST_EMBEDDING', name);
        const dto2 = await adminAddProviderModel(pid, 'SIMILARITY_EMBEDDING', name);
        setProviderModels((prev) => ({ ...prev, [pid]: mapModelsDtoToState(dto2 ?? dto1) }));
        setModalSuccess(`已添加：${name}`);
      } else {
        const dto = await adminAddProviderModel(pid, actualPurpose, name);
        setProviderModels((prev) => ({ ...prev, [pid]: mapModelsDtoToState(dto) }));
        setModalSuccess(`已添加：${name}`);
      }
      try {
        window.localStorage.removeItem('llm-routing-config.provider-models-map.v2');
      } catch {}
      setModalManualName('');
    } catch (e) {
      setModalError(e instanceof Error ? e.message : '添加失败');
    } finally {
      setModalLoading(false);
    }
  };

  const deleteModel = async (providerId: string, purpose: ModelPurpose, modelName: string) => {
    if (!isEditing) return;
    const pid = providerId.trim();
    const name = modelName.trim();
    if (!pid || !name) return;
    const ok = window.confirm(`确认删除 ${purposeLabel(purpose)}：${name}？`);
    if (!ok) return;
    try {
      if (purpose === 'EMBEDDING') {
        const dto1 = await adminDeleteProviderModel(pid, 'POST_EMBEDDING', name);
        const dto2 = await adminDeleteProviderModel(pid, 'SIMILARITY_EMBEDDING', name);
        setProviderModels((prev) => ({ ...prev, [pid]: mapModelsDtoToState(dto2 ?? dto1) }));
      } else {
        const dto = await adminDeleteProviderModel(pid, purpose, name);
        setProviderModels((prev) => ({ ...prev, [pid]: mapModelsDtoToState(dto) }));
      }
      try {
        window.localStorage.removeItem('llm-routing-config.provider-models-map.v2');
      } catch {}
    } catch (e) {
      window.alert(e instanceof Error ? e.message : '删除失败');
    }
  };

  const filteredUpstream = useMemo(() => {
    const q = modalFilter.trim().toLowerCase();
    const cap = modalCapability.trim();
    return upstreamModels.filter((m) => {
      if (q && !m.toLowerCase().includes(q)) return false;
      if (!cap) return true;
      return modelCapabilityTags(m).includes(cap);
    });
  }, [modalCapability, modalFilter, modelCapabilityTags, upstreamModels]);

  const modalErrorHint = useMemo(() => {
    if (!modalError) return null;
    const msg = modalError;
    const lower = msg.toLowerCase();
    const refused =
      lower.includes('connection refused') ||
      lower.includes('econnrefused') ||
      lower.includes('getsockopt') ||
      lower.includes('无法连接');
    const http401 = lower.includes('http 401');
    const http404 = lower.includes('http 404');
    const endpointMatch = msg.match(/endpoint:\s*([^\s)]+)/i);
    const endpoint = endpointMatch?.[1] ?? null;
    const local = endpoint
        ? /\/\/(localhost|127\.0\.0\.1|0\.0\.0\.0)([:/])/i.test(endpoint)
      : /localhost|127\.0\.0\.1|0\.0\.0\.0/i.test(msg);

    const tips: string[] = [];
    if (refused) {
      tips.push('确认上游服务已启动、端口/协议正确，并且后端机器能访问该地址。');
      if (local) {
        tips.push('如果上游跑在你本机而后端跑在服务器/容器里，baseUrl 不能填 localhost/127.0.0.1；请改成后端可达的地址（如宿主机 IP 或 host.docker.internal）。');
      }
      tips.push('OpenAI 兼容接口通常需要以 /v1 结尾，例如 http://127.0.0.1:1234/v1。');
    } else if (http401) {
      tips.push('Upstream 返回 401，通常是 apiKey/Authorization 配置不正确。');
    } else if (http404) {
      tips.push('Upstream 返回 404，通常是 baseUrl 路径不对（是否漏了 /v1），或服务不支持 /models。');
    }

    if (!tips.length) return null;
    return { endpoint, tips };
  }, [modalError]);

  const reload = async () => {
    if (saving) return;
    if (isEditing && hasUnsavedChanges) {
      const ok = window.confirm('当前有未保存的修改，刷新会丢弃这些修改。确认继续？');
      if (!ok) return;
    }
    setPresetModalOpen(false);
    closeModal();
    setIsEditing(false);
    await load();
  };

  const cancelEditing = async () => {
    if (saving) return;
    if (hasUnsavedChanges) {
      const ok = window.confirm('放弃未保存的修改？');
      if (!ok) return;
    }
    setPresetModalOpen(false);
    closeModal();
    setIsEditing(false);
    await load();
  };

  const addProviderFromPreset = (preset: ProviderPreset | null) => {
    setIsEditing(true);
    setPresetModalOpen(false);
    if (!preset) {
      const rowId = addProvider();
      setExpandedProviderRowIds((prev) => ({ ...prev, [rowId]: true }));
      autoSaveRequestedRef.current = true;
      return;
    }
    let rowId = '';
    setForm((prev) => {
      const id = ensureUniqueProviderId(prev.providers, preset.idPrefix);
      rowId = providerRowId(id);
      const nextProvider: ProviderForm = {
        rowId,
        id,
        name: preset.label,
        type: preset.type,
        baseUrl: preset.baseUrl,
        apiKey: '',
        defaultChatModel: '',
        defaultEmbeddingModel: '',
        defaultRerankModel: '',
        supportsVision: false,
        connectTimeoutMs: null,
            readTimeoutMs: null,
            enabled: true,
            extraHeadersRows: [],
      };
      return {
        ...prev,
        providers: [...prev.providers, nextProvider],
        activeProviderId: prev.activeProviderId?.trim() ? prev.activeProviderId : id,
      };
    });
    setExpandedProviderRowIds((prev) => ({ ...prev, [rowId]: true }));
    autoSaveRequestedRef.current = true;
  };

  const openAddModelsSafe = async (providerId: string, providerIndex: number, purpose: ModelPurpose, capability?: string) => {
    if (!isEditing) return;
    if (saving) return;
    const pid = providerId.trim();
    if (!pid) return;
    if (!isProviderPersisted(pid)) {
      const ok = await onSave();
      if (!ok) return;
    }
    openAddModels(pid, providerIndex, purpose, capability);
  };

  const modalProviderId = (addModelsModal.providerId ?? '').trim();
  const canAddModels = isEditing && !!modalProviderId;
  const modalExistingSets = useMemo(() => {
    const pid = modalProviderId;
    return {
      MULTIMODAL_CHAT: new Set(getModelsForProvider(pid, 'MULTIMODAL_CHAT')),
      EMBEDDING: new Set(getModelsForProvider(pid, 'EMBEDDING')),
      RERANK: new Set(getModelsForProvider(pid, 'RERANK')),
    };
  }, [getModelsForProvider, modalProviderId]);
  const modalExistingForPurpose = modalExistingSets[addModelsModal.purpose] ?? new Set<string>();
  const manualAlreadyAdded = modalManualName.trim() ? modalExistingForPurpose.has(modalManualName.trim()) : false;
  const manualModelWarning = useMemo(() => {
    const name = modalManualName.trim();
    if (!name) return null;
    return getModelPurposeWarning(addModelsModal.purpose, name);
  }, [addModelsModal.purpose, modalManualName]);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">模型提供商</h3>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={`px-3 py-1.5 rounded text-sm text-white disabled:opacity-50 ${
              isEditing ? 'bg-blue-600 hover:bg-blue-700' : 'bg-gray-900 hover:bg-gray-800'
            }`}
            onClick={async () => {
              if (saving) return;
              if (!isEditing) {
                setIsEditing(true);
                return;
              }
              const ok = await onSave();
              if (ok) setIsEditing(false);
            }}
            disabled={saving}
          >
            {isEditing ? (saving ? '保存中…' : '保存') : '编辑'}
          </button>
          {isEditing && (
            <button
              type="button"
              className="px-3 py-1.5 rounded bg-gray-100 hover:bg-gray-200 text-sm"
              onClick={cancelEditing}
              disabled={saving}
            >
              取消
            </button>
          )}
          <button
            type="button"
            className="px-3 py-1.5 rounded bg-gray-100 hover:bg-gray-200 text-sm"
            onClick={reload}
            disabled={saving}
          >
            刷新
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">
          {error}
        </div>
      )}
      {success && (
        <div className="rounded border border-green-200 bg-green-50 text-green-700 px-3 py-2 text-sm">
          {success}
        </div>
      )}

      <div className="space-y-2">
        <label className="block text-sm font-medium text-gray-700">全局默认模型提供商</label>
        <select
          className="w-full border rounded px-3 py-2 text-sm"
          value={form.activeProviderId}
          onChange={(e) => setForm((prev) => ({ ...prev, activeProviderId: e.target.value }))}
          disabled={saving || !isEditing}
        >
          {providersSorted.map((p) => (
            <option key={p.rowId} value={p.id ?? ''}>
              {(p.name ?? '').trim() ? `${p.name} (${p.id})` : p.id}
              {p.enabled === false ? ' [禁用]' : ''}
            </option>
          ))}
        </select>
      </div>

      <div className="flex items-center justify-between">
        <div className="text-sm text-gray-700">模型提供商（{form.providers.length}）</div>
        {isEditing && (
          <button
            type="button"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-md bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium shadow-sm disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500/40"
            onClick={() => setPresetModalOpen(true)}
            disabled={saving}
          >
            <span className="text-base leading-none">＋</span>
            新增模型提供商
          </button>
        )}
      </div>

      <div className="space-y-6">
        {form.providers.map((p, idx) => {
          const rerankEndpointRaw = (p.rerankEndpointPath ?? '').trim();
          const rerankEndpointLower = rerankEndpointRaw.toLowerCase();
          const rerankEndpointSelectValue = !rerankEndpointRaw
            ? ''
            : rerankEndpointLower.includes('compatible-api') && rerankEndpointLower.includes('/reranks')
              ? ''
              : rerankEndpointLower.includes('/v1/rerank') && !rerankEndpointLower.includes('/reranks')
                ? '/v1/rerank'
                : '__custom__';

          return (
          <div key={p.rowId} className="border rounded-lg p-4 space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="font-medium text-gray-900 truncate">{(p.name ?? '').trim() ? p.name : p.id}</div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {shouldCollapseProviders && (
                  <button
                    type="button"
                    className="px-3 py-1.5 rounded bg-gray-100 hover:bg-gray-200 text-sm"
                    onClick={() =>
                      setExpandedProviderRowIds((prev) => ({
                        ...prev,
                        [p.rowId]: !isProviderExpanded(p.rowId),
                      }))
                    }
                    disabled={saving}
                  >
                    {isProviderExpanded(p.rowId) ? '收起' : '展开'}
                  </button>
                )}
                <label className="flex items-center gap-2 text-sm text-gray-700">
                  <input
                    type="checkbox"
                    checked={p.enabled !== false}
                    onChange={(e) => updateProvider(idx, { enabled: e.target.checked })}
                    disabled={saving || !isEditing}
                  />
                  启用
                </label>
                {isEditing && (
                  <button
                    type="button"
                    className="px-3 py-1.5 rounded bg-red-50 hover:bg-red-100 text-red-700 text-sm"
                    onClick={() => removeProvider(idx)}
                    disabled={saving}
                  >
                    删除
                  </button>
                )}
              </div>
            </div>

            {isProviderExpanded(p.rowId) && (
              <>
                <div className="space-y-3">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700">标识</label>
                      <input
                        className="w-full border rounded px-3 py-2 text-sm"
                        value={p.id ?? ''}
                        onChange={(e) => updateProvider(idx, { id: e.target.value })}
                        disabled={saving || !isEditing}
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">显示名</label>
                      <input
                        className="w-full border rounded px-3 py-2 text-sm"
                        value={p.name ?? ''}
                        onChange={(e) => updateProvider(idx, { name: e.target.value })}
                        disabled={saving || !isEditing}
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">类型</label>
                      <select
                        className="w-full border rounded px-3 py-2 text-sm"
                        value={p.type ?? 'OPENAI_COMPAT'}
                        onChange={(e) => updateProvider(idx, { type: e.target.value })}
                        disabled={saving || !isEditing}
                      >
                        {typeOptions.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-sm font-medium text-gray-700">接口地址</label>
                      <input
                        className="w-full border rounded px-3 py-2 text-sm"
                        value={p.baseUrl ?? ''}
                        onChange={(e) => updateProvider(idx, { baseUrl: e.target.value })}
                        placeholder="例如 https://api.openai.com/v1 或 http://127.0.0.1:8000/v1"
                        disabled={saving || !isEditing}
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">密钥（保持不变请保留 ******）</label>
                      <input
                        className="w-full border rounded px-3 py-2 text-sm"
                        type="password"
                        value={p.apiKey ?? ''}
                        onChange={(e) => updateProvider(idx, { apiKey: e.target.value })}
                        placeholder="******"
                        disabled={saving || !isEditing}
                      />
                    </div>
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="text-sm font-medium text-gray-700">模型列表</div>
                  </div>

                  {getProviderModelsState(p.id ?? '').error && (
                    <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">
                      {getProviderModelsState(p.id ?? '').error}
                    </div>
                  )}

                  <div className="grid grid-cols-1 lg:grid-cols-4 gap-3">
                    {(['MULTIMODAL_CHAT', 'EMBEDDING', 'RERANK'] as ModelPurpose[]).map((purpose) => {
                      const models = getModelsForProvider(p.id ?? '', purpose);
                      const capHint = purpose === 'EMBEDDING' ? '嵌入' : purpose === 'RERANK' ? '重排' : undefined;
                      return (
                        <div key={purpose} className="border rounded-lg p-3 space-y-2">
                          <div className="flex items-center justify-between">
                            <div className="text-sm font-medium text-gray-900">
                              {purposeLabel(purpose)}（{models.length}）
                            </div>
                            {isEditing && (
                              <button
                                type="button"
                                className="px-3 py-1.5 rounded bg-blue-600 hover:bg-blue-700 text-white text-sm disabled:opacity-50"
                                onClick={() => openAddModelsSafe(p.id ?? '', idx, purpose, capHint)}
                                disabled={saving || !(p.id ?? '').trim()}
                              >
                                添加
                              </button>
                            )}
                          </div>

                          {models.length === 0 ? (
                            <div className="text-sm text-gray-500">暂无模型</div>
                          ) : (
                            <div className="divide-y rounded border">
                              {models.map((m) => (
                                <div key={m} className="flex items-center justify-between px-3 py-2">
                                  <div className="text-sm text-gray-800 truncate">{m}</div>
                                  {isEditing && (
                                    <button
                                      type="button"
                                      className="px-2 py-1 rounded bg-red-50 hover:bg-red-100 text-red-700 text-xs"
                                      onClick={() => deleteModel(p.id ?? '', purpose, m)}
                                      disabled={saving}
                                    >
                                      删除
                                    </button>
                                  )}
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>

                <details className="rounded border p-3">
                  <summary className="cursor-pointer text-sm font-medium text-gray-700">高级设置</summary>
                  <div className="pt-3 space-y-3">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                      <div>
                        <label className="block text-sm font-medium text-gray-700">连接超时（毫秒）</label>
                        <input
                          className="w-full border rounded px-3 py-2 text-sm"
                          value={p.connectTimeoutMs === null || p.connectTimeoutMs === undefined ? '' : String(p.connectTimeoutMs)}
                          onChange={(e) => updateProvider(idx, { connectTimeoutMs: e.target.value.trim() ? Number(e.target.value) : null })}
                          inputMode="numeric"
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700">读取超时（毫秒）</label>
                        <input
                          className="w-full border rounded px-3 py-2 text-sm"
                          value={p.readTimeoutMs === null || p.readTimeoutMs === undefined ? '' : String(p.readTimeoutMs)}
                          onChange={(e) => updateProvider(idx, { readTimeoutMs: e.target.value.trim() ? Number(e.target.value) : null })}
                          inputMode="numeric"
                        />
                      </div>
                    </div>

                    <div>
                      <label className="block text-sm font-medium text-gray-700">重排端点</label>
                      <select
                        className="w-full border rounded px-3 py-2 text-sm bg-white"
                        value={rerankEndpointSelectValue}
                        onChange={(e) => {
                          const v = e.target.value;
                          if (v === '') {
                            updateProvider(idx, { rerankEndpointPath: '' });
                            return;
                          }
                          if (v === '/v1/rerank') {
                            updateProvider(idx, { rerankEndpointPath: '/v1/rerank' });
                            return;
                          }
                          updateProvider(idx, { rerankEndpointPath: rerankEndpointRaw });
                        }}
                        disabled={saving || !isEditing}
                      >
                        <option value="">DashScope 兼容（默认）：POST /compatible-api/v1/reranks</option>
                        <option value="/v1/rerank">OpenAI Responses 风格：POST /v1/rerank</option>
                      </select>
                      {rerankEndpointSelectValue === '__custom__' && (
                        <input
                          className="mt-2 w-full border rounded px-3 py-2 text-sm"
                          value={p.rerankEndpointPath ?? ''}
                          onChange={(e) => updateProvider(idx, { rerankEndpointPath: e.target.value })}
                          placeholder="例如 /v1/responses 或 http://127.0.0.1:20768/v1/responses"
                          disabled={saving || !isEditing}
                        />
                      )}
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="text-sm font-medium text-gray-700">额外请求头（保持不变请保留 ******）</div>
                        {isEditing && (
                          <button
                            type="button"
                            className="px-3 py-1.5 rounded bg-gray-100 hover:bg-gray-200 text-sm"
                            onClick={() =>
                              updateProvider(idx, {
                                extraHeadersRows: [
                                  ...p.extraHeadersRows,
                                  { rowId: `${Date.now()}-${Math.random().toString(16).slice(2)}`, k: '', v: '' },
                                ],
                              })
                            }
                            disabled={saving}
                          >
                            新增请求头
                          </button>
                        )}
                      </div>

                      {p.extraHeadersRows.length === 0 ? (
                        <div className="text-sm text-gray-500">暂无</div>
                      ) : (
                        <div className="space-y-2">
                          {p.extraHeadersRows.map((r, ridx) => (
                            <div key={r.rowId} className="grid grid-cols-1 md:grid-cols-12 gap-2 items-center">
                              <input
                                className="md:col-span-5 border rounded px-3 py-2 text-sm"
                                value={r.k}
                                onChange={(e) => {
                                  const next = [...p.extraHeadersRows];
                                  next[ridx] = { ...next[ridx], k: e.target.value };
                                  updateProvider(idx, { extraHeadersRows: next });
                                }}
                                placeholder="请求头名称，例如 Authorization"
                                disabled={saving || !isEditing}
                              />
                              <input
                                className="md:col-span-6 border rounded px-3 py-2 text-sm"
                                value={r.v}
                                onChange={(e) => {
                                  const next = [...p.extraHeadersRows];
                                  next[ridx] = { ...next[ridx], v: e.target.value };
                                  updateProvider(idx, { extraHeadersRows: next });
                                }}
                                placeholder="请求头值"
                                disabled={saving || !isEditing}
                              />
                              {isEditing && (
                                <button
                                  type="button"
                                  className="md:col-span-1 px-3 py-2 rounded bg-red-50 hover:bg-red-100 text-red-700 text-sm"
                                  onClick={() => {
                                    const next = p.extraHeadersRows.filter((_, j) => j !== ridx);
                                    updateProvider(idx, { extraHeadersRows: next });
                                  }}
                                  disabled={saving}
                                >
                                  删
                                </button>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </details>
              </>
            )}
          </div>
          );
        })}
      </div>

      <div className="pt-4 border-t">
        {/* LLM 路由配置已迁移到独立页面 */}
      </div>

      {presetModalOpen && (
        <div
          className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50"
          onClick={() => setPresetModalOpen(false)}
        >
          <div className="bg-white rounded-lg shadow max-w-2xl w-full p-4 space-y-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-lg font-semibold">新增模型提供商</div>
                <div className="text-sm text-gray-600">选择一个常见 API 预设，或创建空白配置</div>
              </div>
              <button
                type="button"
                className="px-3 py-1.5 rounded bg-gray-100 hover:bg-gray-200 text-sm"
                onClick={() => setPresetModalOpen(false)}
              >
                关闭
              </button>
            </div>

            <div className="space-y-2">
              {PROVIDER_PRESETS.map((p) => (
                <button
                  key={p.key}
                  type="button"
                  className="w-full text-left border rounded-lg px-3 py-2 hover:bg-gray-50"
                  onClick={() => addProviderFromPreset(p)}
                  disabled={saving}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="font-medium text-gray-900">{p.label}</div>
                    <div className="text-xs text-gray-500">{p.type === 'LOCAL_OPENAI_COMPAT' ? '本地 OpenAI 兼容' : 'OpenAI 兼容'}</div>
                  </div>
                  <div className="text-sm text-gray-600 break-all">{p.baseUrl}</div>
                  <div className="text-xs text-gray-500">{p.description}</div>
                </button>
              ))}
            </div>

            <div className="pt-2 border-t">
              <button
                type="button"
                className="w-full px-3 py-2 rounded bg-gray-100 hover:bg-gray-200 text-sm"
                onClick={() => addProviderFromPreset(null)}
                disabled={saving}
              >
                空白配置
              </button>
            </div>
          </div>
        </div>
      )}

      {addModelsModal.open && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50" onClick={closeModal}>
          <div className="bg-white rounded-lg shadow max-w-3xl w-full p-4 space-y-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-lg font-semibold">添加模型</div>
                <div className="text-sm text-gray-600">
                  模型提供商：{addModelsModal.providerId}，类型：{purposeLabel(addModelsModal.purpose)}
                </div>
              </div>
              <button type="button" className="px-3 py-1.5 rounded bg-gray-100 hover:bg-gray-200 text-sm" onClick={closeModal}>
                关闭
              </button>
            </div>

            {modalError && (
              <div className="space-y-2">
                <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{modalError}</div>
                {modalErrorHint && (
                  <div className="rounded border border-amber-200 bg-amber-50 text-amber-800 px-3 py-2 text-sm space-y-1">
                    {modalErrorHint.endpoint && <div className="break-all">endpoint：{modalErrorHint.endpoint}</div>}
                    <ul className="list-disc pl-5">
                      {modalErrorHint.tips.map((t) => (
                        <li key={t}>{t}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
            {modalSuccess && <div className="rounded border border-green-200 bg-green-50 text-green-700 px-3 py-2 text-sm">{modalSuccess}</div>}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700">手动输入模型名</label>
                <input
                  className="w-full border rounded px-3 py-2 text-sm"
                  value={modalManualName}
                  onChange={(e) => setModalManualName(e.target.value)}
                  placeholder="例如 gpt-4o-mini"
                />
                {manualAlreadyAdded && <div className="mt-1 text-xs text-gray-500">该模型已添加，无需重复添加</div>}
                {!manualAlreadyAdded && manualModelWarning && <div className="mt-1 text-xs text-amber-700">{manualModelWarning}</div>}
              </div>
              <div className="flex items-end">
                <button
                  type="button"
                  className="w-full px-3 py-2 rounded bg-blue-600 hover:bg-blue-700 text-white text-sm disabled:opacity-50"
                  disabled={modalLoading || !modalManualName.trim() || manualAlreadyAdded || !canAddModels}
                  onClick={() => addModel(addModelsModal.providerId, addModelsModal.purpose, modalManualName)}
                >
                  添加
                </button>
              </div>
            </div>

            <div className="flex flex-col md:flex-row gap-2 md:items-end">
              <div className="flex-1">
                <label className="block text-sm font-medium text-gray-700">从 /v1/models 查询并筛选</label>
                <input className="w-full border rounded px-3 py-2 text-sm" value={modalFilter} onChange={(e) => setModalFilter(e.target.value)} placeholder="输入关键词筛选" />
              </div>
              <div className="w-full md:w-40">
                <label className="block text-sm font-medium text-gray-700">能力</label>
                <select className="w-full border rounded px-3 py-2 text-sm" value={modalCapability} onChange={(e) => setModalCapability(e.target.value)}>
                  <option value="">全部</option>
                  <option value="推理">推理</option>
                  <option value="嵌入">嵌入</option>
                  <option value="重排">重排</option>
                  <option value="视觉">视觉</option>
                  <option value="工具">工具</option>
                </select>
              </div>
              <button
                type="button"
                className="px-3 py-2 rounded bg-gray-100 hover:bg-gray-200 text-sm disabled:opacity-50"
                disabled={modalLoading}
                onClick={() => fetchUpstream(addModelsModal.providerId, addModelsModal.providerIndex)}
              >
                刷新列表
              </button>
            </div>

            <div className="border rounded overflow-hidden">
              <div className="grid grid-cols-12 bg-gray-50 text-xs text-gray-600 px-3 py-2">
                <div className="col-span-6">模型</div>
                <div className="col-span-5">能力</div>
                <div className="col-span-1 text-right">操作</div>
              </div>
              <div className="max-h-80 overflow-auto">
                {modalLoading && upstreamModels.length === 0 ? (
                  <div className="px-3 py-3 text-sm text-gray-600">加载中…</div>
                ) : filteredUpstream.length === 0 ? (
                  <div className="px-3 py-3 text-sm text-gray-600">暂无数据</div>
                ) : (
                  filteredUpstream.map((m) => {
                    const alreadyAdded = modalExistingForPurpose.has(m);
                    const existingPurposes: string[] = [];
                    if (modalExistingSets.MULTIMODAL_CHAT.has(m)) existingPurposes.push('多模态');
                    if (modalExistingSets.EMBEDDING.has(m)) existingPurposes.push('嵌入');
                    if (modalExistingSets.RERANK.has(m)) existingPurposes.push('重排');
                    const inferred = modelCapabilityTags(m);
                    const tags = Array.from(new Set([...existingPurposes, ...inferred]));
                    const addWarning = getModelPurposeWarning(addModelsModal.purpose, m);
                    return (
                      <div key={m} className="grid grid-cols-12 items-center px-3 py-2 border-t gap-2">
                        <div className="col-span-6 min-w-0">
                          {canAddModels && !alreadyAdded ? (
                            <button
                              type="button"
                              className="w-full text-left text-sm text-gray-800 hover:underline truncate"
                              onClick={() => addModel(addModelsModal.providerId, addModelsModal.purpose, m)}
                            >
                              {m}
                            </button>
                          ) : (
                            <div className={`text-sm truncate ${alreadyAdded ? 'text-gray-500' : 'text-gray-800'}`}>{m}</div>
                          )}
                          {!alreadyAdded && addWarning && <div className="mt-1 text-xs text-amber-700">{addWarning}</div>}
                        </div>
                        <div className="col-span-5 flex flex-wrap gap-1 justify-start">
                          {tags.length ? (
                            tags.map((t) => (
                              <span
                                key={t}
                                className={`px-2 py-0.5 rounded text-xs ${
                                  t === '嵌入'
                                    ? 'bg-indigo-50 text-indigo-700'
                                    : t === '重排'
                                      ? 'bg-purple-50 text-purple-700'
                                      : t === '视觉'
                                        ? 'bg-emerald-50 text-emerald-700'
                                        : t === '工具'
                                          ? 'bg-amber-50 text-amber-700'
                                          : 'bg-gray-100 text-gray-700'
                                }`}
                              >
                                {t}
                              </span>
                            ))
                          ) : (
                            <span className="text-xs text-gray-400">-</span>
                          )}
                        </div>
                        <div className="col-span-1 flex justify-end">
                          <button
                            type="button"
                            className={`px-2 py-1 rounded text-xs disabled:opacity-50 ${
                              alreadyAdded ? 'bg-red-50 hover:bg-red-100 text-red-700' : 'bg-blue-600 hover:bg-blue-700 text-white'
                            }`}
                            disabled={modalLoading || !canAddModels}
                            onClick={() =>
                              alreadyAdded
                                ? deleteModel(addModelsModal.providerId, addModelsModal.purpose, m)
                                : addModel(addModelsModal.providerId, addModelsModal.purpose, m)
                            }
                          >
                            {alreadyAdded ? '删除' : '添加'}
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
