import { useEffect, useState } from 'react';
import { getMyTranslatePreferences, updateMyTranslatePreferences } from '../../../../services/accountPreferencesService';
import { getTranslateConfig } from '../../../../services/translateService';
import { LLM_SUPPORTED_LANGUAGES } from '../../../../constants/llmSupportedLanguages';

export default function AccountPreferencesPage() {
  const [compact, setCompact] = useState(true);
  const [emailNoti, setEmailNoti] = useState(false);

  const [targetLanguage, setTargetLanguage] = useState('zh');
  const [availableTargetLanguages, setAvailableTargetLanguages] = useState<string[]>(LLM_SUPPORTED_LANGUAGES);
  const [autoTranslatePosts, setAutoTranslatePosts] = useState(false);
  const [autoTranslateComments, setAutoTranslateComments] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const p = await getMyTranslatePreferences();
        if (!mounted) return;
        setTargetLanguage(p.targetLanguage || 'zh');
        setAutoTranslatePosts(!!p.autoTranslatePosts);
        setAutoTranslateComments(!!p.autoTranslateComments);
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
        const cfg = await getTranslateConfig();
        const langs = (cfg.allowedTargetLanguages ?? []).filter((x): x is string => typeof x === 'string' && x.trim().length > 0);
        if (!mounted) return;
        if (langs.length > 0) setAvailableTargetLanguages(langs);
      } catch {
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold">偏好</h3>
        <p className="text-gray-600">这里管理展示、通知与翻译偏好设置。</p>
      </div>

      {error ? <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}
      {savedHint ? <div className="text-sm text-green-700">{savedHint}</div> : null}
      {loading ? <div className="text-sm text-gray-600">加载中...</div> : null}

      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaving(true);
          setError(null);
          setSavedHint(null);
          try {
            await updateMyTranslatePreferences({
              targetLanguage: targetLanguage.trim() || 'zh',
              autoTranslatePosts,
              autoTranslateComments,
            });
            setSavedHint('保存成功');
          } catch (e2) {
            setError(e2 instanceof Error ? e2.message : String(e2));
          } finally {
            setSaving(false);
          }
        }}
      >
        <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-3">
          <div className="text-sm font-medium text-gray-900">翻译偏好</div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <div className="text-sm text-gray-700 mb-1">默认翻译目标语言</div>
              <select
                className="w-full rounded border border-gray-300 px-3 py-2 text-sm bg-white"
                value={targetLanguage}
                onChange={(e) => setTargetLanguage(e.target.value)}
              >
                {!availableTargetLanguages.includes(targetLanguage) ? (
                  <option value={targetLanguage}>{targetLanguage}</option>
                ) : null}
                {availableTargetLanguages.map((lang) => (
                  <option key={lang} value={lang}>
                    {lang}
                  </option>
                ))}
              </select>
              <div className="mt-1 text-xs text-gray-500">从支持列表中选择目标语言。</div>
            </div>
            <div className="space-y-2">
              <label className="inline-flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={autoTranslatePosts}
                  onChange={(e) => setAutoTranslatePosts(e.target.checked)}
                />
                帖子自动翻译
              </label>
              <label className="inline-flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={autoTranslateComments}
                  onChange={(e) => setAutoTranslateComments(e.target.checked)}
                />
                评论区自动翻译
              </label>
              <div className="text-xs text-gray-500">
                自动翻译仅在语言标签为单一语言且与目标语言不同的情况下触发；多语言内容默认不自动翻译。
              </div>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4 space-y-3">
          <div className="text-sm font-medium text-gray-900">展示与通知</div>
        <label className="inline-flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={compact} onChange={(e) => setCompact(e.target.checked)} />
          紧凑模式
        </label>
        <label className="inline-flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={emailNoti} onChange={(e) => setEmailNoti(e.target.checked)} />
          邮件通知
        </label>
        </div>

        <button
          type="submit"
          disabled={saving}
          className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? '保存中...' : '保存'}
        </button>
      </form>
    </div>
  );
}

