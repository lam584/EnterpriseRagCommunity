import React, { useMemo } from 'react';

type CategorizedModelRow = {
  providerId: string;
  providerName: string;
  modelName: string;
  enabledScenarios: Set<string>;
};

type CategorizedModelRows = {
  TEXT_GEN: CategorizedModelRow[];
  EMBEDDING: CategorizedModelRow[];
  RERANK: CategorizedModelRow[];
};

type CategorizedScenarios = {
  TEXT_GEN: string[];
  EMBEDDING: string[];
  RERANK: string[];
};

type LlmHealthSummary = {
  severity?: string;
  failureRate: number;
  consecutiveFailures: number;
  lastFailure?: { tsMs: number | null; errorCode: string; errorMessage: string } | null;
  runningCount?: number;
  lastCallTsMs?: number | null;
  lastOkTsMs?: number | null;
  lastThrottledTsMs?: number | null;
  recentRecords?: Array<{
    tsMs: number | null;
    ok: boolean;
    errorCode: string;
    errorMessage: string;
    taskId: string;
    taskType: string;
    status: string;
    durationMs: number | null;
    tokensIn: number | null;
    tokensOut: number | null;
    totalTokens: number | null;
  }>;
};

type ModelAvailabilityCheck = {
  ok: boolean;
  reason: string;
  checkedAtMs: number;
  latencyMs: number | null;
};

type ProbeJob = {
  providerId: string;
  providerName: string;
  modelName: string;
  kind: string;
};

export const AvailableModelsCard: React.FC<{
  availabilitySummary: { total: number; ok: number; fail: number; checkedAtMs: number } | null;
  isEditingAny: boolean;
  loading: boolean;
  saving: boolean;
  disabled?: boolean;
  setIsEditingModelList: React.Dispatch<React.SetStateAction<boolean>>;
  cancelEdits: () => void;
  save: () => void;
  draft: unknown | null;
  canEdit: boolean;
  availabilityChecking: boolean;
  availabilityProgress: { done: number; total: number } | null;
  probeJobs: ProbeJob[];
  checkAllModelsAvailability: () => void;
  setAvailabilityModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
  categorizedModelRows: CategorizedModelRows;
  categorizedScenarios: CategorizedScenarios;
  scenarioMetadata: Map<string, { label: string; category: string }>;
  healthByKey: Record<string, LlmHealthSummary>;
  ignoredHealthKeys: Record<string, true>;
  setHealthModalKey: React.Dispatch<React.SetStateAction<string | null>>;
  toggleModelForScenario: (providerId: string, modelName: string, tt: string, enabled: boolean) => void;
  severityToUi: (severity: string) => { dot: string; text: string };
  formatMmddHms: (ms: number | null | undefined) => string;
}> = ({
  availabilitySummary,
  isEditingAny,
  loading,
  saving,
  disabled,
  setIsEditingModelList,
  cancelEdits,
  save,
  draft,
  canEdit,
  availabilityChecking,
  availabilityProgress,
  probeJobs,
  checkAllModelsAvailability,
  setAvailabilityModalOpen,
  categorizedModelRows,
  categorizedScenarios,
  scenarioMetadata,
  healthByKey,
  ignoredHealthKeys,
  setHealthModalKey,
  toggleModelForScenario,
  severityToUi,
  formatMmddHms,
}) => {
  return (
    <div className="rounded border p-3 space-y-3 bg-white">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold">可用模型列表</div>
          {availabilitySummary ? (
            <div className="text-xs text-gray-500">
              上次检测：{formatMmddHms(availabilitySummary.checkedAtMs)}，可用 {availabilitySummary.ok}/{availabilitySummary.total}
              {availabilitySummary.fail ? `（不可用 ${availabilitySummary.fail}）` : ''}
            </div>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          {!isEditingAny ? (
            <button
              type="button"
              className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50"
              onClick={() => setIsEditingModelList(true)}
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
            onClick={() => {
              setAvailabilityModalOpen(true);
            }}
            disabled={!availabilitySummary}
            title="查看最近一次检测结果"
          >
            查看检测结果
          </button>
          <button
            type="button"
            className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50"
            onClick={checkAllModelsAvailability}
            disabled={availabilityChecking || !probeJobs.length}
            title="向模型发送最小请求探活：文本生成/重排/嵌入分别走不同调用"
          >
            {availabilityChecking
              ? `检测中…${availabilityProgress ? `（${availabilityProgress.done}/${availabilityProgress.total}）` : ''}`
              : '检测全部模型状态'}
          </button>
        </div>
      </div>

      {[
        { title: '文本生成类模型', rows: categorizedModelRows.TEXT_GEN, scenarios: categorizedScenarios.TEXT_GEN },
        { title: '嵌入向量化模型', rows: categorizedModelRows.EMBEDDING, scenarios: categorizedScenarios.EMBEDDING },
        { title: '重排模型', rows: categorizedModelRows.RERANK, scenarios: categorizedScenarios.RERANK },
      ].map((cat) => {
        const totalToggles = cat.rows.length * cat.scenarios.length;
        const enabledToggles = cat.rows.reduce((acc, m) => acc + cat.scenarios.filter((s) => m.enabledScenarios.has(s)).length, 0);
        const allEnabledInCat = totalToggles > 0 && enabledToggles === totalToggles;
        const bulkButtonText = allEnabledInCat ? '全不选' : '全选';
        const bulkButtonTitle = totalToggles ? `本类已启用 ${enabledToggles}/${totalToggles}` : '暂无模型数据';

        return (
          <div key={cat.title} className="space-y-2">
            <div className="flex items-center justify-between gap-2 text-xs font-semibold text-gray-600 bg-gray-50 p-2 rounded">
              <span>{cat.title}</span>
              <button
                type="button"
                className="rounded border bg-white px-2 py-1 text-xs disabled:opacity-50"
                onClick={() => {
                  const nextEnabled = !allEnabledInCat;
                  cat.rows.forEach((m) => {
                    cat.scenarios.forEach((tt) => {
                      if (m.enabledScenarios.has(tt) === nextEnabled) return;
                      toggleModelForScenario(m.providerId, m.modelName, tt, nextEnabled);
                    });
                  });
                }}
                disabled={!canEdit || totalToggles === 0}
                title={bulkButtonTitle}
              >
                {bulkButtonText}
              </button>
            </div>
            <div className="overflow-x-auto border rounded">
              <table className="w-full text-xs border-collapse">
                <thead>
                  <tr className="bg-gray-50 text-gray-600 border-b">
                    <th className="px-2 py-1.5 border-r text-left font-semibold min-w-[70px]">
                      <span>状态</span>
                    </th>
                    <th className="px-3 py-1.5 border-r text-left font-semibold min-w-[320px]">模型名</th>
                    {cat.scenarios.map((tt) => (
                      <th key={tt} className="px-2 py-1.5 border-r text-center font-semibold min-w-[65px]" title={tt}>
                        {scenarioMetadata.get(tt)?.label || tt}
                      </th>
                    ))}
                    <th className="px-2 py-1.5 text-center font-semibold min-w-[64px]">启用总数</th>
                  </tr>
                </thead>
                <tbody className="divide-y bg-white">
                  {cat.rows.length === 0 ? (
                    <tr>
                      <td colSpan={cat.scenarios.length + 3} className="p-4 text-center text-gray-400 italic">
                        暂无模型数据
                      </td>
                    </tr>
                  ) : (
                    cat.rows.map((m) => {
                      const enabledCountInCat = Array.from(m.enabledScenarios).filter((s) => cat.scenarios.includes(s)).length;
                      const modelKey = `${m.providerId}|${m.modelName}`;
                      const summary = healthByKey[modelKey];
                      const ignored = !!ignoredHealthKeys[modelKey];
                      const severity = ignored ? 'IGNORED' : (summary?.severity ?? 'NEVER_CALLED');
                      const ui = severityToUi(severity);
                      const lastFailTs = summary?.lastFailure?.tsMs ?? null;
                      const lastCallTs = summary?.lastCallTsMs ?? null;
                      const runningCount = summary?.runningCount ?? 0;
                      const clickToShow = severity !== 'NORMAL' && severity !== 'NEVER_CALLED' && severity !== 'IGNORED';
                      const statusHint = (() => {
                        if (severity === 'THROTTLED')
                          return summary?.lastThrottledTsMs ? formatMmddHms(summary.lastThrottledTsMs) : lastCallTs ? formatMmddHms(lastCallTs) : '';
                        if (severity === 'NORMAL') return lastCallTs ? formatMmddHms(lastCallTs) : '';
                        if (runningCount > 0) return `运行中${runningCount}`;
                        if (lastFailTs) return formatMmddHms(lastFailTs);
                        return lastCallTs ? formatMmddHms(lastCallTs) : '';
                      })();
                      const title = (() => {
                        if (!summary) return '暂无调用记录';
                        const parts: string[] = [];
                        if (summary.lastCallTsMs) parts.push(`最近调用：${formatMmddHms(summary.lastCallTsMs)}`);
                        if (summary.lastOkTsMs) parts.push(`最近成功：${formatMmddHms(summary.lastOkTsMs)}`);
                        if (summary.lastThrottledTsMs) parts.push(`最近限流：${formatMmddHms(summary.lastThrottledTsMs)}`);
                        if (summary.runningCount) parts.push(`运行中：${summary.runningCount}`);
                        parts.push(`失败率：${Math.round(summary.failureRate * 100)}%`);
                        parts.push(`连续失败：${summary.consecutiveFailures}`);
                        return parts.join('\n');
                      })();
                      return (
                        <tr
                          key={`${m.providerId}-${m.modelName}`}
                          className={['transition-colors', clickToShow ? 'hover:bg-gray-50 cursor-pointer' : 'hover:bg-gray-50'].join(' ')}
                          onClick={() => {
                            if (!clickToShow) return;
                            setHealthModalKey(modelKey);
                          }}
                        >
                          <td className="px-2 py-1.5 border-r">
                            <div className="flex items-center gap-1.5" title={title}>
                              <span className={`inline-block h-2.5 w-2.5 rounded-full ${ui.dot}`} />
                              <span className="text-gray-700">{ui.text}</span>
                              {statusHint ? <span className="text-gray-500">{statusHint}</span> : null}
                            </div>
                          </td>
                          <td className="px-3 py-1.5 border-r">
                            <div className="font-medium text-gray-900">
                              {m.providerName}：{m.modelName}
                            </div>
                          </td>
                          {cat.scenarios.map((tt) => {
                            const isEnabled = m.enabledScenarios.has(tt);
                            return (
                              <td key={tt} className="px-2 py-1 border-r text-center">
                                <input
                                  type="checkbox"
                                  className="h-5 w-5 rounded border-gray-300 text-blue-600 focus:ring-blue-500 cursor-pointer"
                                  checked={isEnabled}
                                  onChange={(e) => toggleModelForScenario(m.providerId, m.modelName, tt, e.target.checked)}
                                  onClick={(e) => e.stopPropagation()}
                                  disabled={!canEdit}
                                />
                              </td>
                            );
                          })}
                          <td className="px-2 py-1.5 text-center font-medium text-gray-700">{enabledCountInCat}</td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export const AvailableModelsModals: React.FC<{
  healthModalKey: string | null;
  setHealthModalKey: React.Dispatch<React.SetStateAction<string | null>>;
  healthByKey: Record<string, LlmHealthSummary>;
  setIgnoredHealthKeys: React.Dispatch<React.SetStateAction<Record<string, true>>>;
  copyToClipboard: (text: string) => void;
  formatMmddHms: (ms: number | null | undefined) => string;
  availabilityModalOpen: boolean;
  setAvailabilityModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
  availabilitySummary: { total: number; ok: number; fail: number; checkedAtMs: number } | null;
  availabilityOnlyFailed: boolean;
  setAvailabilityOnlyFailed: React.Dispatch<React.SetStateAction<boolean>>;
  checkAllModelsAvailability: () => void;
  availabilityChecking: boolean;
  availabilityProgress: { done: number; total: number } | null;
  probeJobs: ProbeJob[];
  availabilityByKey: Record<string, ModelAvailabilityCheck>;
}> = ({
  healthModalKey,
  setHealthModalKey,
  healthByKey,
  setIgnoredHealthKeys,
  copyToClipboard,
  formatMmddHms,
  availabilityModalOpen,
  setAvailabilityModalOpen,
  availabilitySummary,
  availabilityOnlyFailed,
  setAvailabilityOnlyFailed,
  checkAllModelsAvailability,
  availabilityChecking,
  availabilityProgress,
  probeJobs,
  availabilityByKey,
}) => {
  const healthModalSummary = healthModalKey ? healthByKey[healthModalKey] : null;
  const healthModalRecent = useMemo(() => (healthModalSummary?.recentRecords ?? []).slice(0, 10), [healthModalSummary]);

  return (
    <>
      {healthModalKey ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="模型状态详情"
          onMouseDown={() => setHealthModalKey(null)}
        >
          <div className="w-full max-w-2xl rounded bg-white shadow-lg" onMouseDown={(e) => e.stopPropagation()}>
            <div className="flex items-start justify-between gap-3 border-b px-4 py-3">
              <div>
                <div className="text-sm font-semibold">模型状态详情</div>
                <div className="text-xs text-gray-500">{healthModalKey}</div>
              </div>
              <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => setHealthModalKey(null)}>
                关闭
              </button>
            </div>
            <div className="px-4 py-3 space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-sm">
                <div className="rounded border bg-gray-50 px-3 py-2">
                  <div className="text-[11px] text-gray-500">最近调用</div>
                  <div className="text-gray-800">{formatMmddHms(healthModalSummary?.lastCallTsMs) || '-'}</div>
                </div>
                <div className="rounded border bg-gray-50 px-3 py-2">
                  <div className="text-[11px] text-gray-500">最近成功</div>
                  <div className="text-gray-800">{formatMmddHms(healthModalSummary?.lastOkTsMs) || '-'}</div>
                </div>
                <div className="rounded border bg-gray-50 px-3 py-2 sm:col-span-1">
                  <div className="text-[11px] text-gray-500">失败率 / 连续失败 / 运行中</div>
                  <div className="text-gray-800">
                    {healthModalSummary
                      ? `${Math.round(healthModalSummary.failureRate * 100)}% / ${healthModalSummary.consecutiveFailures} / ${healthModalSummary.runningCount ?? 0}`
                      : '-'}
                  </div>
                </div>
              </div>

              <div className="rounded border px-3 py-2">
                <div className="text-[11px] text-gray-500 mb-1">最近一次失败</div>
                <div className="text-sm text-gray-800 whitespace-pre-wrap break-words">
                  {healthModalSummary?.lastFailure
                    ? `${formatMmddHms(healthModalSummary.lastFailure.tsMs) || '-'}  ${healthModalSummary.lastFailure.errorCode || ''}\n${
                        healthModalSummary.lastFailure.errorMessage || ''
                      }`.trim()
                    : '-'}
                </div>
              </div>

              <div className="rounded border">
                <div className="flex items-center justify-between gap-2 border-b bg-gray-50 px-3 py-2">
                  <div className="text-sm font-medium text-gray-700">最近 10 次调用</div>
                  <button
                    type="button"
                    className="rounded border bg-white px-2 py-1 text-xs"
                    onClick={() => copyToClipboard(healthModalRecent.map((x) => x.taskId).filter(Boolean).join('\n'))}
                    disabled={!healthModalRecent.some((x) => x.taskId)}
                  >
                    复制全部
                  </button>
                </div>
                <div className="divide-y">
                  {healthModalRecent.length ? (
                    healthModalRecent.map((r, idx) => (
                      <div key={`${r.taskId}-${idx}`} className="px-3 py-2 space-y-0.5">
                        <div className="flex items-center justify-between gap-2">
                          <div className="min-w-0">
                            <div className="text-xs text-gray-500">
                              {formatMmddHms(r.tsMs) || '-'} {r.taskType ? `· ${r.taskType}` : ''}
                              {r.durationMs != null ? ` · ${r.durationMs}ms` : ''}
                            </div>
                            <div className="text-sm text-gray-800 break-all">{r.taskId || '-'}</div>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <span className={`text-xs ${r.ok ? 'text-green-700' : 'text-red-700'}`}>{r.ok ? '成功' : '失败'}</span>
                            <button
                              type="button"
                              className="rounded border px-2 py-1 text-xs"
                              onClick={() => copyToClipboard(r.taskId)}
                              disabled={!r.taskId}
                            >
                              复制
                            </button>
                          </div>
                        </div>
                        {!r.ok ? (
                          <div className="text-xs text-gray-700 whitespace-pre-wrap break-words">
                            {(r.errorCode ? `[${r.errorCode}] ` : '') + (r.errorMessage || '')}
                          </div>
                        ) : null}
                        {r.totalTokens != null || r.tokensOut != null ? (
                          <div className="text-[11px] text-gray-500">
                            {r.tokensIn != null ? `in ${r.tokensIn}` : 'in -'} / {r.tokensOut != null ? `out ${r.tokensOut}` : 'out -'} /{' '}
                            {r.totalTokens != null ? `total ${r.totalTokens}` : 'total -'}
                          </div>
                        ) : null}
                      </div>
                    ))
                  ) : (
                    <div className="px-3 py-3 text-sm text-gray-500">暂无调用记录</div>
                  )}
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 border-t px-4 py-3">
              <button
                type="button"
                className="rounded border px-3 py-2 text-sm"
                onClick={() => {
                  if (!healthModalKey) return;
                  setIgnoredHealthKeys((prev) => ({ ...prev, [healthModalKey]: true }));
                  setHealthModalKey(null);
                }}
              >
                忽略本次异常
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {availabilityModalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="模型可用性检测结果"
          onMouseDown={() => setAvailabilityModalOpen(false)}
        >
          <div
            className="w-full max-w-5xl rounded bg-white shadow-lg max-h-[85vh] overflow-hidden flex flex-col"
            onMouseDown={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-3 border-b px-4 py-3">
              <div>
                <div className="text-sm font-semibold">模型可用性检测结果</div>
                <div className="text-xs text-gray-500">
                  {availabilitySummary
                    ? `检测时间：${formatMmddHms(availabilitySummary.checkedAtMs)}，可用 ${availabilitySummary.ok}/${availabilitySummary.total}`
                    : '暂无检测结果'}
                </div>
              </div>
              <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => setAvailabilityModalOpen(false)}>
                关闭
              </button>
            </div>

            <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
              <div className="flex items-center justify-between gap-2">
                <label className="inline-flex items-center gap-2 text-sm text-gray-700">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    checked={availabilityOnlyFailed}
                    onChange={(e) => setAvailabilityOnlyFailed(e.target.checked)}
                  />
                  仅看不可用
                </label>
                <button
                  type="button"
                  className="rounded border px-3 py-2 text-sm disabled:opacity-50"
                  onClick={checkAllModelsAvailability}
                  disabled={availabilityChecking || !probeJobs.length}
                >
                  {availabilityChecking ? `重新检测中…${availabilityProgress ? `（${availabilityProgress.done}/${availabilityProgress.total}）` : ''}` : '重新检测'}
                </button>
              </div>

              <div className="overflow-x-auto border rounded">
                <table className="w-full text-xs border-collapse">
                  <thead>
                    <tr className="bg-gray-50 text-gray-600 border-b">
                      <th className="p-2 border-r text-left font-semibold min-w-[240px]">模型</th>
                      <th className="p-2 border-r text-left font-semibold min-w-[90px]">类型</th>
                      <th className="p-2 border-r text-left font-semibold min-w-[90px]">结果</th>
                      <th className="p-2 border-r text-left font-semibold min-w-[300px]">原因</th>
                      <th className="p-2 border-r text-left font-semibold min-w-[120px]">时间</th>
                      <th className="p-2 text-left font-semibold min-w-[90px]">耗时</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y bg-white">
                    {(() => {
                      const rows = probeJobs
                        .map((job) => {
                          const key = `${job.providerId}|${job.modelName}|${job.kind}`;
                          const st = availabilityByKey[key] ?? null;
                          return { ...job, key, st };
                        })
                        .filter((r) => (availabilityOnlyFailed ? r.st && !r.st.ok : true));
                      if (!rows.length) {
                        return (
                          <tr>
                            <td colSpan={6} className="p-4 text-center text-gray-400 italic">
                              {availabilitySummary ? '暂无不可用模型' : '暂无数据'}
                            </td>
                          </tr>
                        );
                      }
                      return rows.map((r) => {
                        const ok = r.st ? r.st.ok : null;
                        const statusDot = ok === true ? 'bg-green-500' : ok === false ? 'bg-red-500' : 'bg-gray-400';
                        const statusText = ok === true ? '可用' : ok === false ? '不可用' : '未检测';
                        const kindLabel = r.kind === 'CHAT' ? '文本生成' : r.kind === 'EMBEDDING' ? '嵌入' : '重排';
                        const reason = r.st ? r.st.reason : '未检测';
                        const when = r.st ? formatMmddHms(r.st.checkedAtMs) : '-';
                        const latency = r.st?.latencyMs != null ? `${r.st.latencyMs}ms` : '-';
                        return (
                          <tr key={r.key} className="hover:bg-gray-50">
                            <td className="p-2 border-r">
                              <div className="font-medium text-gray-900">
                                {r.providerName}：{r.modelName}
                              </div>
                              <div className="text-[11px] text-gray-500">{r.key}</div>
                            </td>
                            <td className="p-2 border-r text-gray-700">{kindLabel}</td>
                            <td className="p-2 border-r">
                              <div className="flex items-center gap-2">
                                <span className={`inline-block h-2.5 w-2.5 rounded-full ${statusDot}`} />
                                <span className="text-gray-700">{statusText}</span>
                              </div>
                            </td>
                            <td className="p-2 border-r text-gray-700 whitespace-pre-wrap break-words">{reason || '-'}</td>
                            <td className="p-2 border-r text-gray-700">{when || '-'}</td>
                            <td className="p-2 text-gray-700">{latency}</td>
                          </tr>
                        );
                      });
                    })()}
                  </tbody>
                </table>
              </div>

              <div className="text-xs text-gray-500">
                检测方式：向模型发送最小请求并判定是否能正常返回（文本生成/重排走一次 chat completion，嵌入走一次 /embeddings）。
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
};
