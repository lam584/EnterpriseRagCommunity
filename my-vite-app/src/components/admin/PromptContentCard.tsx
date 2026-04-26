import React from 'react';

export type PromptContentDraft = {
  name: string;
  systemPrompt: string;
  userPromptTemplate: string;
  providerId?: string | null;
  modelName?: string | null;
  visionProviderId?: string | null;
  visionModel?: string | null;
  temperature?: number | null;
  topP?: number | null;
  maxTokens?: number | null;
  enableDeepThinking?: boolean | null;
};

export type PromptContentCardProps = {
  title: string;
  draft: PromptContentDraft | null;
  editing: boolean;
  onChange?: (next: PromptContentDraft) => void;
  hint?: string;
  showName?: boolean;
  showSystemPrompt?: boolean;
  showUserPromptTemplate?: boolean;
  showRuntimeParams?: boolean;
  compact?: boolean;
};

const PromptContentCard: React.FC<PromptContentCardProps> = ({
  title,
  draft,
  editing,
  onChange,
  hint,
  showName = false,
  showSystemPrompt = true,
  showUserPromptTemplate = true,
  showRuntimeParams = false,
  compact = false,
}) => {
  const readOnly = !editing || !onChange;
  const cardClassName = compact
    ? 'rounded-2xl border border-slate-200 bg-white/95 p-3 space-y-3 shadow-sm'
    : 'rounded-lg border border-gray-200 bg-white p-3 space-y-3';
  const inputClassName = compact
    ? 'w-full rounded-xl border border-slate-200 px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed'
    : 'w-full rounded border px-3 py-2 text-sm disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed';
  const systemPromptClassName = compact
    ? 'w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[120px] max-h-[240px] resize-y font-mono disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed'
    : 'w-full rounded border px-3 py-2 text-sm min-h-[120px] font-mono disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed';
  const userPromptClassName = compact
    ? 'w-full rounded-xl border border-slate-200 px-3 py-2 text-sm min-h-[140px] max-h-[260px] resize-y font-mono disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed'
    : 'w-full rounded border px-3 py-2 text-sm min-h-[140px] font-mono disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed';

  return (
    <div className={cardClassName}>
      <div className="flex items-center justify-between gap-2">
        <div className="text-sm font-semibold text-gray-900">{title}</div>
        {!editing ? <div className="text-xs text-gray-500">只读</div> : null}
      </div>

      {hint ? <div className="text-xs text-gray-500">{hint}</div> : null}

      {draft == null ? (
        <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">未找到对应提示词内容</div>
      ) : (
        <div className="space-y-3">
          {showName ? (
            <div>
              <div className="text-xs text-gray-600 mb-1">名称</div>
              <input
                className={inputClassName}
                value={draft.name}
                disabled={readOnly}
                onChange={(e) => onChange?.({ ...draft, name: e.target.value })}
              />
            </div>
          ) : null}

          {showSystemPrompt ? (
            <div>
              <div className="text-xs text-gray-600 mb-1">系统提示词</div>
              <textarea
                className={systemPromptClassName}
                value={draft.systemPrompt}
                disabled={readOnly}
                onChange={(e) => onChange?.({ ...draft, systemPrompt: e.target.value })}
              />
            </div>
          ) : null}

          {showUserPromptTemplate ? (
            <div>
              <div className="text-xs text-gray-600 mb-1">用户提示词模板</div>
              <textarea
                className={userPromptClassName}
                value={draft.userPromptTemplate}
                disabled={readOnly}
                onChange={(e) => onChange?.({ ...draft, userPromptTemplate: e.target.value })}
              />
            </div>
          ) : null}

          {showRuntimeParams ? (
            <div className={`${compact ? 'rounded-2xl' : 'rounded'} border border-gray-200 bg-gray-50 p-3 space-y-3`}>
              <div className="text-xs font-semibold text-gray-700">LLM 参数</div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <label className="text-xs text-gray-600">
                  <div className="mb-1">上下文长度 (max_tokens)</div>
                  <input
                    type="number"
                    min={1}
                    step={1}
                    className={inputClassName}
                    value={draft.maxTokens ?? ''}
                    disabled={readOnly}
                    onChange={(e) => onChange?.({
                      ...draft,
                      maxTokens: e.target.value === ''
                        ? null
                        : (() => {
                          const parsed = Number(e.target.value);
                          if (!Number.isFinite(parsed)) return draft.maxTokens ?? null;
                          return Math.max(1, Math.trunc(parsed));
                        })(),
                    })}
                  />
                </label>

                <label className="text-xs text-gray-600">
                  <div className="mb-1">启用深度思考</div>
                  <select
                    className={inputClassName}
                    value={draft.enableDeepThinking == null ? '' : String(draft.enableDeepThinking)}
                    disabled={readOnly}
                    onChange={(e) => onChange?.({
                      ...draft,
                      enableDeepThinking: e.target.value === '' ? null : e.target.value === 'true',
                    })}
                  >
                    <option value="">默认</option>
                    <option value="true">启用</option>
                    <option value="false">禁用</option>
                  </select>
                </label>

                <label className="text-xs text-gray-600">
                  <div className="mb-1">温度 (temperature)</div>
                  <input
                    type="number"
                    min={0}
                    max={2}
                    step={0.1}
                    className={inputClassName}
                    value={draft.temperature ?? ''}
                    disabled={readOnly}
                    onChange={(e) => onChange?.({
                      ...draft,
                      temperature: e.target.value === ''
                        ? null
                        : (() => {
                          const parsed = Number(e.target.value);
                          return Number.isFinite(parsed) ? parsed : draft.temperature ?? null;
                        })(),
                    })}
                  />
                </label>

                <label className="text-xs text-gray-600">
                  <div className="mb-1">Top P (top_p)</div>
                  <input
                    type="number"
                    min={0}
                    max={1}
                    step={0.01}
                    className={inputClassName}
                    value={draft.topP ?? ''}
                    disabled={readOnly}
                    onChange={(e) => onChange?.({
                      ...draft,
                      topP: e.target.value === ''
                        ? null
                        : (() => {
                          const parsed = Number(e.target.value);
                          return Number.isFinite(parsed) ? parsed : draft.topP ?? null;
                        })(),
                    })}
                  />
                </label>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
};

export default PromptContentCard;
