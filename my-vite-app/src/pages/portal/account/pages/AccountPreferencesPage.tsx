import { useEffect, useMemo, useState } from 'react';
import { getMyTranslatePreferences, updateMyTranslatePreferences } from '../../../../services/accountPreferencesService';
import { getTranslateConfig } from '../../../../services/translateService';
import { listSupportedLanguages, type SupportedLanguageDTO } from '../../../../services/supportedLanguagesService';
import { getPostTitleGenPublicConfig, type PostTitleGenPublicConfigDTO } from '../../../../services/titleGenPublicService';
import { getPostTagGenPublicConfig, type PostTagGenPublicConfigDTO } from '../../../../services/tagGenPublicService';

function clampCount(n: number, maxCount?: number | null): number {
  const max = Math.max(1, Math.min(maxCount ?? 50, 50));
  const nn = Number.isFinite(n) ? Math.trunc(n) : 1;
  return Math.max(1, Math.min(max, nn));
}

function normalizeCount(raw: unknown, fallback: number): number {
  const n = typeof raw === 'number' ? raw : raw == null ? NaN : Number(raw);
  if (!Number.isFinite(n)) return fallback;
  const nn = Math.trunc(n);
  if (nn < 1 || nn > 50) return fallback;
  return nn;
}

export default function AccountPreferencesPage() {
  const [emailNoti, setEmailNoti] = useState(false);

  const [targetLanguage, setTargetLanguage] = useState('zh-CN');
  const [supportedLanguages, setSupportedLanguages] = useState<SupportedLanguageDTO[]>([]);
  const [allowedTargetLanguageCodes, setAllowedTargetLanguageCodes] = useState<string[]>([]);
  const [autoTranslatePosts, setAutoTranslatePosts] = useState(false);
  const [autoTranslateComments, setAutoTranslateComments] = useState(false);
  const [titleGenCount, setTitleGenCount] = useState(5);
  const [tagGenCount, setTagGenCount] = useState(5);
  const [titleGenConfig, setTitleGenConfig] = useState<PostTitleGenPublicConfigDTO | null>(null);
  const [tagGenConfig, setTagGenConfig] = useState<PostTagGenPublicConfigDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [committed, setCommitted] = useState(() => ({
    emailNoti: false,
    targetLanguage: 'zh-CN',
    autoTranslatePosts: false,
    autoTranslateComments: false,
    titleGenCount: 5,
    tagGenCount: 5,
  }));

  const controlsDisabled = !editing || saving || loading;

  const availableTargetLanguages = useMemo(() => {
    if (!supportedLanguages.length) return [];
    if (!allowedTargetLanguageCodes.length) return supportedLanguages;
    const allowed = new Set(allowedTargetLanguageCodes);
    const filtered = supportedLanguages.filter((x) => allowed.has(x.languageCode));
    return filtered.length ? filtered : supportedLanguages;
  }, [supportedLanguages, allowedTargetLanguageCodes]);

  const isDirty = useMemo(() => {
    return (
      emailNoti !== committed.emailNoti ||
      targetLanguage !== committed.targetLanguage ||
      autoTranslatePosts !== committed.autoTranslatePosts ||
      autoTranslateComments !== committed.autoTranslateComments ||
      titleGenCount !== committed.titleGenCount ||
      tagGenCount !== committed.tagGenCount
    );
  }, [autoTranslateComments, autoTranslatePosts, committed, emailNoti, tagGenCount, targetLanguage, titleGenCount]);

  useEffect(() => {
    let mounted = true;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const p = await getMyTranslatePreferences();
        if (!mounted) return;
        const next = {
          emailNoti: false,
          targetLanguage: p.targetLanguage || 'zh-CN',
          autoTranslatePosts: !!p.autoTranslatePosts,
          autoTranslateComments: !!p.autoTranslateComments,
          titleGenCount: normalizeCount(p.titleGenCount, 5),
          tagGenCount: normalizeCount(p.tagGenCount, 5),
        };
        setEmailNoti(next.emailNoti);
        setTargetLanguage(next.targetLanguage);
        setAutoTranslatePosts(next.autoTranslatePosts);
        setAutoTranslateComments(next.autoTranslateComments);
        setTitleGenCount(next.titleGenCount);
        setTagGenCount(next.tagGenCount);
        setCommitted(next);
        setEditing(false);
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

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const cfg = await getPostTitleGenPublicConfig();
        if (!mounted) return;
        setTitleGenConfig(cfg);
      } catch {
        if (!mounted) return;
        setTitleGenConfig(null);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const cfg = await getPostTagGenPublicConfig();
        if (!mounted) return;
        setTagGenConfig(cfg);
      } catch {
        if (!mounted) return;
        setTagGenConfig(null);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    setTitleGenCount((prev) => clampCount(prev, titleGenConfig?.maxCount));
  }, [titleGenConfig?.maxCount]);

  useEffect(() => {
    setTagGenCount((prev) => clampCount(prev, tagGenConfig?.maxCount));
  }, [tagGenConfig?.maxCount]);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const langs = await listSupportedLanguages();
        if (!mounted) return;
        setSupportedLanguages((langs ?? []).filter((x) => x && typeof x.languageCode === 'string' && typeof x.displayName === 'string'));
      } catch {
        if (!mounted) return;
        setSupportedLanguages([]);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const cfg = await getTranslateConfig();
        if (!mounted) return;
        const codes = (cfg.allowedTargetLanguages ?? []).filter((x): x is string => typeof x === 'string' && x.trim().length > 0);
        setAllowedTargetLanguageCodes(codes);
      } catch {
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-semibold">偏好</h3>
            {editing ? (
              <span className="inline-flex items-center rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                编辑中
              </span>
            ) : null}
          </div>
          <p className="text-sm text-gray-600">管理展示、通知与翻译偏好设置。</p>
        </div>

        <div className="flex items-center gap-2">
          {!editing ? (
            <button
              type="button"
              className="px-3 py-1.5 rounded-md border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
              disabled={loading || saving}
              onClick={() => {
                setEditing(true);
                setError(null);
                setSavedHint(null);
              }}
            >
              编辑
            </button>
          ) : (
            <>
              <button
                type="button"
                className="px-3 py-1.5 rounded-md border border-gray-300 bg-white hover:bg-gray-50 disabled:opacity-60 disabled:cursor-not-allowed"
                disabled={saving || loading}
                onClick={() => {
                  setEmailNoti(committed.emailNoti);
                  setTargetLanguage(committed.targetLanguage);
                  setAutoTranslatePosts(committed.autoTranslatePosts);
                  setAutoTranslateComments(committed.autoTranslateComments);
                  setTitleGenCount(committed.titleGenCount);
                  setTagGenCount(committed.tagGenCount);
                  setEditing(false);
                  setError(null);
                  setSavedHint(null);
                }}
              >
                取消
              </button>
              <button
                type="button"
                className="px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                disabled={saving || loading || !isDirty}
                onClick={async () => {
                  setSaving(true);
                  setError(null);
                  setSavedHint(null);
                  try {
                    const saved = await updateMyTranslatePreferences({
                      targetLanguage: targetLanguage.trim() || 'zh-CN',
                      autoTranslatePosts,
                      autoTranslateComments,
                      titleGenCount: clampCount(titleGenCount, titleGenConfig?.maxCount),
                      tagGenCount: clampCount(tagGenCount, tagGenConfig?.maxCount),
                    });
                    const next = {
                      emailNoti,
                      targetLanguage: saved.targetLanguage || 'zh-CN',
                      autoTranslatePosts: !!saved.autoTranslatePosts,
                      autoTranslateComments: !!saved.autoTranslateComments,
                      titleGenCount: clampCount(normalizeCount(saved.titleGenCount, titleGenCount), titleGenConfig?.maxCount),
                      tagGenCount: clampCount(normalizeCount(saved.tagGenCount, tagGenCount), tagGenConfig?.maxCount),
                    };
                    setTargetLanguage(next.targetLanguage);
                    setAutoTranslatePosts(next.autoTranslatePosts);
                    setAutoTranslateComments(next.autoTranslateComments);
                    setTitleGenCount(next.titleGenCount);
                    setTagGenCount(next.tagGenCount);
                    setCommitted(next);
                    setEditing(false);
                    setSavedHint('保存成功');
                  } catch (e2) {
                    setError(e2 instanceof Error ? e2.message : String(e2));
                  } finally {
                    setSaving(false);
                  }
                }}
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </>
          )}
        </div>
      </div>

      {error ? <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div> : null}
      {savedHint ? <div className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{savedHint}</div> : null}
      {editing && !saving && !loading && !isDirty ? <div className="text-sm text-gray-500">未修改</div> : null}
      {loading ? <div className="text-sm text-gray-600">加载中…</div> : null}

      <form className="space-y-4" onSubmit={(e) => e.preventDefault()}>
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-base font-semibold text-gray-900">翻译偏好</div>
              <div className="mt-0.5 text-sm text-gray-600">设置目标语言与自动翻译策略。</div>
            </div>
            {!editing ? <div className="text-xs text-gray-500">点击右上角“编辑”以修改</div> : null}
          </div>

          <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">默认翻译目标语言</label>
              <select
                className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600 disabled:cursor-not-allowed"
                value={targetLanguage}
                onChange={(e) => setTargetLanguage(e.target.value)}
                disabled={controlsDisabled}
              >
                {!availableTargetLanguages.some((x) => x.languageCode === targetLanguage) ? (
                  <option value={targetLanguage}>{targetLanguage}</option>
                ) : null}
                {availableTargetLanguages.map((lang) => (
                  <option key={lang.languageCode} value={lang.languageCode}>
                    {lang.displayName}
                  </option>
                ))}
              </select>
              <div className="mt-1 text-xs text-gray-500">从支持列表中选择目标语言。</div>
            </div>

            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3">
              <div className="space-y-2">
                <label className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-gray-900">帖子自动翻译</div>
                    <div className="mt-0.5 text-xs text-gray-600">浏览帖子时自动触发翻译。</div>
                  </div>
                  <input
                    type="checkbox"
                    className="mt-1 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed"
                    checked={autoTranslatePosts}
                    onChange={(e) => setAutoTranslatePosts(e.target.checked)}
                    disabled={controlsDisabled}
                  />
                </label>
                <label className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-gray-900">评论区自动翻译</div>
                    <div className="mt-0.5 text-xs text-gray-600">浏览评论区时自动触发翻译。</div>
                  </div>
                  <input
                    type="checkbox"
                    className="mt-1 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed"
                    checked={autoTranslateComments}
                    onChange={(e) => setAutoTranslateComments(e.target.checked)}
                    disabled={controlsDisabled}
                  />
                </label>
              </div>

              <div className="mt-2 text-xs text-gray-500">
                自动翻译仅在语言标签为单一语言且与目标语言不同的情况下触发；多语言内容默认不自动翻译。
              </div>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-base font-semibold text-gray-900">发帖 AI 生成</div>
              <div className="mt-0.5 text-sm text-gray-600">设置标题/标签生成时的默认生成数量。</div>
            </div>
          </div>

          <div className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-2">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">标题生成数量</label>
              <select
                value={titleGenCount}
                onChange={(e) => setTitleGenCount(clampCount(Number(e.target.value), titleGenConfig?.maxCount))}
                className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600 disabled:cursor-not-allowed"
                disabled={controlsDisabled || titleGenConfig?.enabled === false}
              >
                {Array.from(
                  { length: Math.max(1, Math.min(titleGenConfig?.maxCount ?? 10, 50)) },
                  (_, i) => i + 1
                ).map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
              <div className="mt-1 text-xs text-gray-500">用于“生成标题”的候选数量。</div>
              {titleGenConfig?.enabled === false ? (
                <div className="mt-2 text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                  标题生成已被管理员关闭。
                </div>
              ) : null}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">标签生成数量</label>
              <select
                value={tagGenCount}
                onChange={(e) => setTagGenCount(clampCount(Number(e.target.value), tagGenConfig?.maxCount))}
                className="w-full border border-gray-300 rounded-md px-3 py-2 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600 disabled:cursor-not-allowed"
                disabled={controlsDisabled || tagGenConfig?.enabled === false}
              >
                {Array.from(
                  { length: Math.max(1, Math.min(tagGenConfig?.maxCount ?? 10, 50)) },
                  (_, i) => i + 1
                ).map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
              <div className="mt-1 text-xs text-gray-500">用于“生成标签”的候选数量。</div>
              {tagGenConfig?.enabled === false ? (
                <div className="mt-2 text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                  主题标签生成已被管理员关闭。
                </div>
              ) : null}
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-base font-semibold text-gray-900">通知</div>
              <div className="mt-0.5 text-sm text-gray-600">控制通知方式。</div>
            </div>
          </div>

          <div className="mt-3 grid grid-cols-1 gap-3">
            <label className="flex items-start justify-between gap-4 rounded-lg border border-gray-200 p-3 hover:bg-gray-50">
              <div className="min-w-0">
                <div className="text-sm font-medium text-gray-900">邮件通知</div>
                <div className="mt-0.5 text-xs text-gray-600">接收账号相关的邮件提醒。</div>
              </div>
              <input
                type="checkbox"
                className="mt-1 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:opacity-60 disabled:cursor-not-allowed"
                checked={emailNoti}
                disabled={controlsDisabled}
                onChange={(e) => setEmailNoti(e.target.checked)}
              />
            </label>
          </div>
        </div>
      </form>
    </div>
  );
}

