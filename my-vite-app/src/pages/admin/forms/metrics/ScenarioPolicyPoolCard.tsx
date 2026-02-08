import React from 'react';
import { GripVertical } from 'lucide-react';
import { FaQuestionCircle } from 'react-icons/fa';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';

export const ScenarioPolicyPoolCard: React.FC<{
  isEditingAny: boolean;
  setIsEditingRouting: React.Dispatch<React.SetStateAction<boolean>>;
  loading: boolean;
  saving: boolean;
  disabled?: boolean;
  cancelEdits: () => void;
  save: () => void;
  draft: unknown | null;
  canEdit: boolean;
  loadProviderModels: (force?: boolean) => void | Promise<void>;
  providerModelsLoading: boolean;
  providerModelsLoadedAtMs: number | null;
  providerModelsFailedProviderIds: string[];
  error: string | null;
  ok: boolean;
  routingDraftCacheBanner: { tsMs: number; draft: any } | null;
  setDraft: React.Dispatch<React.SetStateAction<any | null>>;
  copyConfig: (cfg: any | null) => any;
  setOk: React.Dispatch<React.SetStateAction<boolean>>;
  setIsEditingModelList: React.Dispatch<React.SetStateAction<boolean>>;
  clearRoutingDraftCache: () => void;
  setRoutingDraftCacheBanner: React.Dispatch<React.SetStateAction<{ tsMs: number; draft: any } | null>>;
  formatMmddHms: (ms: number | null | undefined) => string;
  taskTypes: string[];
  selectedTaskType: string;
  setSelectedTaskType: React.Dispatch<React.SetStateAction<string>>;
  formatTaskTypeLabel: (tt: string) => string;
  currentPolicy: any;
  updatePolicy: (patch: any) => void;
  currentTargetRows: Array<{ target: any; targetIndex: number }>;
  providerOptions: any[];
  activeProviderIdProp?: string;
  chatOptionsActiveProviderId: string;
  chatProviders: any[];
  normNonBlank: (s: string | null | undefined) => string | null;
  updateTarget: (targetIndex: number, patch: any) => void;
  moveTarget: (fromIdx: number, toIdx: number) => void;
  dragFromIndex: number | null;
  dragOverIndex: number | null;
  setDragFromIndex: React.Dispatch<React.SetStateAction<number | null>>;
  setDragOverIndex: React.Dispatch<React.SetStateAction<number | null>>;
  routingStateByKey: Record<string, any>;
}> = ({
  isEditingAny,
  setIsEditingRouting,
  loading,
  saving,
  disabled,
  cancelEdits,
  save,
  draft,
  canEdit,
  loadProviderModels,
  providerModelsLoading,
  providerModelsLoadedAtMs,
  providerModelsFailedProviderIds,
  error,
  ok,
  routingDraftCacheBanner,
  setDraft,
  copyConfig,
  setOk,
  setIsEditingModelList,
  clearRoutingDraftCache,
  setRoutingDraftCacheBanner,
  formatMmddHms,
  taskTypes,
  selectedTaskType,
  setSelectedTaskType,
  formatTaskTypeLabel,
  currentPolicy,
  updatePolicy,
  currentTargetRows,
  providerOptions,
  activeProviderIdProp,
  chatOptionsActiveProviderId,
  chatProviders,
  normNonBlank,
  updateTarget,
  moveTarget,
  dragFromIndex,
  dragOverIndex,
  setDragFromIndex,
  setDragOverIndex,
  routingStateByKey,
}) => {
  return (
    <div className="rounded border p-3 space-y-3 bg-white">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold">场景与策略与候选模型池</div>
          <div className="text-xs text-gray-600">按场景配置权重轮询/优先级回退与故障切换（熔断冷却）</div>
        </div>
        <div className="flex items-center gap-2">
          {!isEditingAny ? (
            <button
              type="button"
              className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50"
              onClick={() => setIsEditingRouting(true)}
              disabled={loading || saving || disabled}
            >
              编辑
            </button>
          ) : (
            <>
              <button type="button" className="rounded border px-3 py-2 text-sm" onClick={cancelEdits} disabled={saving}>
                取消
              </button>
              <button
                type="button"
                className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50"
                onClick={save}
                disabled={!draft || saving || !canEdit}
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </>
          )}
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm disabled:opacity-50"
            onClick={() => void loadProviderModels(true)}
            disabled={providerModelsLoading || loading || saving || disabled}
            title={providerModelsLoadedAtMs ? `上次加载：${formatMmddHms(providerModelsLoadedAtMs)}` : ''}
          >
            {providerModelsLoading ? '模型列表加载中…' : '重载模型列表'}
          </button>
        </div>
      </div>

      {error && <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>}
      {ok && <div className="rounded border border-green-300 bg-green-50 text-green-700 px-3 py-2 text-sm">已保存</div>}
      {providerModelsFailedProviderIds.length ? (
        <div className="rounded border border-yellow-300 bg-yellow-50 text-yellow-800 px-3 py-2 text-sm flex items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="font-medium">部分模型列表加载失败</div>
            <div className="text-xs opacity-80 break-words">{providerModelsFailedProviderIds.join('，')}</div>
          </div>
          <button
            type="button"
            className="shrink-0 rounded border border-yellow-400 bg-white px-3 py-1.5 text-sm disabled:opacity-50"
            onClick={() => void loadProviderModels(true)}
            disabled={providerModelsLoading || loading || saving || disabled}
          >
            重试
          </button>
        </div>
      ) : null}
      {routingDraftCacheBanner ? (
        <div className="rounded border border-blue-300 bg-blue-50 text-blue-800 px-3 py-2 text-sm flex items-center justify-between gap-3">
          <div className="min-w-0">
            <div className="font-medium">检测到本地草稿</div>
            <div className="text-xs opacity-80 break-words">{formatMmddHms(routingDraftCacheBanner.tsMs) || '-'}</div>
          </div>
          <div className="shrink-0 flex items-center gap-2">
            <button
              type="button"
              className="rounded border border-blue-300 bg-white px-3 py-1.5 text-sm disabled:opacity-50"
              onClick={() => {
                setDraft(copyConfig(routingDraftCacheBanner.draft));
                setIsEditingRouting(true);
                setIsEditingModelList(false);
                setOk(false);
                setRoutingDraftCacheBanner(null);
              }}
              disabled={loading || saving || disabled}
            >
              恢复草稿
            </button>
            <button
              type="button"
              className="rounded border border-blue-300 bg-white px-3 py-1.5 text-sm disabled:opacity-50"
              onClick={() => {
                clearRoutingDraftCache();
                setRoutingDraftCacheBanner(null);
              }}
              disabled={loading || saving || disabled}
            >
              丢弃
            </button>
          </div>
        </div>
      ) : null}

      <div className="grid grid-cols-1 lg:grid-cols-[240px_minmax(0,1fr)] gap-3">
        <div className="rounded border p-3 space-y-2">
          <div className="text-sm font-semibold">场景与策略</div>
          <div className="space-y-2">
            <div className="text-xs text-gray-700">场景</div>
            <div className="flex flex-col border rounded overflow-hidden">
              {taskTypes.map((t) => (
                <button
                  key={t}
                  type="button"
                  className={`w-full text-left px-3 py-2 text-sm border-b last:border-b-0 transition-colors ${
                    selectedTaskType === t ? 'bg-blue-600 text-white font-medium' : 'bg-white hover:bg-gray-50 text-gray-700'
                  }`}
                  onClick={() => setSelectedTaskType(t)}
                  disabled={!canEdit && !taskTypes.length}
                >
                  {formatTaskTypeLabel(t)}
                </button>
              ))}
            </div>
            {selectedTaskType === 'IMAGE_CHAT' ? (
              <div className="text-xs text-gray-500">图片聊天：用于包含图片的请求；请仅配置视觉模型，避免被路由到纯文本模型。</div>
            ) : selectedTaskType === 'TEXT_CHAT' ? (
              <div className="text-xs text-gray-500">文本聊天：用于纯文本请求；不包含图片的对话将路由到此场景。</div>
            ) : null}

            <label className="block text-xs text-gray-700">
              路由策略
              <select
                className="mt-1 w-full rounded border px-2 py-2 text-sm"
                value={currentPolicy?.strategy || 'WEIGHTED_RR'}
                onChange={(e) => updatePolicy({ strategy: e.target.value })}
                disabled={!canEdit}
              >
                <option value="WEIGHTED_RR">权重轮询</option>
                <option value="PRIORITY_FALLBACK">优先级回退</option>
              </select>
            </label>
          </div>

          <div className="space-y-2">
            <label className="block text-xs text-gray-700">
              最大尝试候选数
              <input
                type="number"
                className="mt-1 w-full rounded border px-2 py-2 text-sm"
                value={currentPolicy?.maxAttempts ?? 2}
                min={1}
                max={10}
                onChange={(e) => updatePolicy({ maxAttempts: Number(e.target.value) })}
                disabled={!canEdit}
              />
            </label>
            <label className="block text-xs text-gray-700">
              冷却时间（毫秒）
              <input
                type="number"
                className="mt-1 w-full rounded border px-2 py-2 text-sm"
                value={currentPolicy?.cooldownMs ?? 30000}
                min={0}
                max={3600000}
                onChange={(e) => updatePolicy({ cooldownMs: Number(e.target.value) })}
                disabled={!canEdit}
              />
            </label>
            <label className="block text-xs text-gray-700">
              熔断阈值（连续失败）
              <input
                type="number"
                className="mt-1 w-full rounded border px-2 py-2 text-sm"
                value={currentPolicy?.failureThreshold ?? 2}
                min={1}
                max={20}
                onChange={(e) => updatePolicy({ failureThreshold: Number(e.target.value) })}
                disabled={!canEdit}
              />
            </label>
          </div>
        </div>

        <div className="rounded border p-3 space-y-2">
          <div className="flex items-center justify-between gap-2">
            <div className="text-sm font-semibold">候选模型池</div>
          </div>

          {!currentTargetRows.length ? (
            <div className="text-sm text-gray-500">暂无候选模型</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full table-fixed text-sm border-separate border-spacing-0">
                <thead>
                  <tr>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[56px] min-w-[56px]">序号</th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b">模型</th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[85px] min-w-[85px] relative pr-5">
                      轮询权重
                      <span
                        className="absolute right-1 top-1 text-gray-400 hover:text-gray-600 cursor-help"
                        title="用于权重轮询：权重越大，被分配到的请求越多。数值范围：0–10000"
                      >
                        <FaQuestionCircle className="h-3 w-3" />
                      </span>
                    </th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[95px] min-w-[95px] relative pr-5">
                      回退优先级
                      <span
                        className="absolute right-1 top-1 text-gray-400 hover:text-gray-600 cursor-help"
                        title="用于优先级回退：优先级越大越先尝试；失败会回退到下一个候选。数值范围：-10000–10000"
                      >
                        <FaQuestionCircle className="h-3 w-3" />
                      </span>
                    </th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[92px] min-w-[92px]">最大并发</th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[110px] min-w-[110px]">最小间隔(ms)</th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[90px] min-w-[90px]">QPS</th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[90px] min-w-[90px]">冷却</th>
                    <th className="text-left text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[80px] min-w-[80px]">运行中</th>
                    <th className="text-center text-xs font-semibold text-gray-700 py-2 px-2 border-b w-[44px] min-w-[44px]">排序</th>
                  </tr>
                </thead>
                <tbody>
                  {currentTargetRows.map(({ target: t, targetIndex }, idx) => (
                    <tr
                      key={`${targetIndex}-${t.providerId}-${t.modelName}`}
                      className={['align-top', dragFromIndex != null && dragOverIndex === idx && dragFromIndex !== idx ? 'bg-blue-50' : ''].join(' ')}
                      onDragEnter={() => {
                        if (!canEdit) return;
                        if (dragFromIndex == null) return;
                        setDragOverIndex(idx);
                      }}
                      onDragOver={(e) => {
                        if (!canEdit) return;
                        if (dragFromIndex == null) return;
                        e.preventDefault();
                      }}
                      onDrop={(e) => {
                        if (!canEdit) return;
                        if (dragFromIndex == null) return;
                        e.preventDefault();
                        moveTarget(dragFromIndex, idx);
                        setDragFromIndex(null);
                        setDragOverIndex(null);
                      }}
                    >
                      <td className="py-2 px-2 border-b whitespace-nowrap text-gray-700">{idx + 1}</td>
                      <td className="py-2 px-2 border-b min-w-0">
                        <ProviderModelSelect
                          providers={providerOptions}
                          activeProviderId={
                            normNonBlank(activeProviderIdProp) ?? (chatOptionsActiveProviderId || String(providerOptions[0]?.id ?? '').trim())
                          }
                          chatProviders={chatProviders}
                          mode="chat"
                          providerId={String(t.providerId ?? '')}
                          model={String(t.modelName ?? '')}
                          disabled={!canEdit}
                          label=""
                          autoOptionLabel="请选择模型"
                          disableAutoOption
                          selectClassName="w-full rounded border px-2 py-2 text-sm bg-white disabled:bg-gray-50"
                          onChange={(next) => updateTarget(targetIndex, { providerId: next.providerId, modelName: next.model })}
                        />
                      </td>
                      <td className="py-2 px-2 border-b w-[85px] min-w-[85px]">
                        <input
                          type="number"
                          className="w-full rounded border px-2 py-2 text-sm"
                          value={t.weight ?? 0}
                          onChange={(e) => updateTarget(targetIndex, { weight: Number(e.target.value) })}
                          disabled={!canEdit}
                          min={0}
                          max={10000}
                        />
                      </td>
                      <td className="py-2 px-2 border-b w-[85px] min-w-[85px]">
                        <input
                          type="number"
                          className="w-full rounded border px-2 py-2 text-sm"
                          value={t.priority ?? 0}
                          onChange={(e) => updateTarget(targetIndex, { priority: Number(e.target.value) })}
                          disabled={!canEdit}
                          min={-10000}
                          max={10000}
                        />
                      </td>
                      {(() => {
                        const key = `${String(t.providerId ?? '').trim()}|${String(t.modelName ?? '').trim()}`;
                        const rt = routingStateByKey[key] ?? null;
                        const cd = rt?.cooldownRemainingMs ?? 0;
                        const cdText = cd > 0 ? `${Math.ceil(cd / 1000)}s` : '-';
                        const tokenText = rt && Number.isFinite(rt.rateTokens) ? `令牌${rt.rateTokens.toFixed(2)}` : '';
                        const lastDispatchText = rt?.lastDispatchAtMs ? formatMmddHms(rt.lastDispatchAtMs) : '';
                        const tip = [tokenText, lastDispatchText ? `最近分发：${lastDispatchText}` : ''].filter(Boolean).join('\n');
                        return (
                          <>
                            <td className="py-2 px-2 border-b w-[92px] min-w-[92px]">
                              <input
                                type="number"
                                className="w-full rounded border px-2 py-2 text-sm"
                                value={t.maxConcurrent ?? 0}
                                onChange={(e) => updateTarget(targetIndex, { maxConcurrent: Number(e.target.value) })}
                                disabled={!canEdit}
                                min={0}
                                max={10000}
                              />
                            </td>
                            <td className="py-2 px-2 border-b w-[110px] min-w-[110px]">
                              <input
                                type="number"
                                className="w-full rounded border px-2 py-2 text-sm"
                                value={t.minDelayMs ?? 0}
                                onChange={(e) => updateTarget(targetIndex, { minDelayMs: Number(e.target.value) })}
                                disabled={!canEdit}
                                min={0}
                                max={60000}
                              />
                            </td>
                            <td className="py-2 px-2 border-b w-[90px] min-w-[90px]">
                              <input
                                type="number"
                                className="w-full rounded border px-2 py-2 text-sm"
                                value={t.qps ?? 0}
                                onChange={(e) => updateTarget(targetIndex, { qps: Number(e.target.value) })}
                                disabled={!canEdit}
                                min={0}
                                max={100000}
                                step={0.1}
                              />
                            </td>
                            <td className="py-2 px-2 border-b w-[90px] min-w-[90px] text-gray-700" title={tip}>
                              {cdText}
                            </td>
                            <td className="py-2 px-2 border-b w-[80px] min-w-[80px] text-gray-700">{rt ? rt.runningCount : '-'}</td>
                          </>
                        );
                      })()}
                      <td className="py-2 px-2 border-b w-[44px] min-w-[44px] text-center">
                        <button
                          type="button"
                          className="inline-flex items-center justify-center rounded border px-2 py-2 text-sm text-gray-700 disabled:opacity-50 cursor-grab active:cursor-grabbing"
                          aria-label="拖拽排序"
                          disabled={!canEdit}
                          draggable={canEdit}
                          onDragStart={(e) => {
                            if (!canEdit) return;
                            setDragFromIndex(idx);
                            setDragOverIndex(idx);
                            e.dataTransfer.effectAllowed = 'move';
                            try {
                              e.dataTransfer.setData('text/plain', String(idx));
                            } catch {
                            }
                          }}
                          onDragEnd={() => {
                            setDragFromIndex(null);
                            setDragOverIndex(null);
                          }}
                        >
                          <GripVertical className="h-4 w-4 pointer-events-none" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
