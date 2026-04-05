import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMyProfile, updateMyProfile } from '../../../../services/accountService';
import { listMyPostsPage } from '../../../../services/postService';
import ProfileAvatarUploader from '../components/ProfileAvatarUploader';
import PostFeed from '../../discover/components/PostFeed';
import { formatProfileModerationStatus } from '../../profileModerationText';
import { hasProfileDraftChanges, validateProfileFields } from './profileValidationUtils';
import { toProfileUpdateRequest, useProfilePageState } from './profilePageShared';

export default function AccountProfilePage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadErr, setLoadErr] = useState<string | null>(null);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const {
    saveOk,
    setSaveOk,
    profile,
    setProfile,
    baseProfile,
    setBaseProfile,
    isEditing,
    setIsEditing,
    username,
    setUsername,
    bio,
    setBio,
    avatarUrl,
    setAvatarUrl,
    location,
    setLocation,
    website,
    setWebsite,
    postsLoading,
    setPostsLoading,
    postsErr,
    setPostsErr,
    postsPage,
    setPostsPage,
    postsPageNo,
    setPostsPageNo,
    postsReloadTick,
    setPostsReloadTick,
    resetDraftFrom,
  } = useProfilePageState();

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setLoading(true);
      setLoadErr(null);
      try {
        const p = await getMyProfile();
        if (cancelled) return;
        setProfile(p);
        setBaseProfile(p);
        resetDraftFrom(p);
        setIsEditing(false);
      } catch (e: unknown) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : undefined;
        setLoadErr(msg || '加载个人资料失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      if (!profile) return;

      setPostsLoading(true);
      setPostsErr(null);
      try {
        const page = await listMyPostsPage({
          status: 'PUBLISHED',
          page: postsPageNo,
          pageSize: 10,
          sortBy: 'publishedAt',
          sortOrderDirection: 'DESC',
        });
        if (cancelled) return;
        setPostsPage(page);
      } catch (e: unknown) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : undefined;
        setPostsErr(msg || '加载帖子失败');
        setPostsPage(null);
      } finally {
        if (!cancelled) setPostsLoading(false);
      }
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [profile, postsPageNo, postsReloadTick]);

  const validationError = useMemo(
    () => validateProfileFields({ username, bio, location, website }),
    [username, bio, location, website]
  );

  const isDirty = useMemo(() => {
    return hasProfileDraftChanges(baseProfile, { username, bio, avatarUrl, location, website });
  }, [avatarUrl, baseProfile, bio, location, username, website]);

  function handleStartEditing() {
    if (saving) return;
    if (!profile) return;
    setSaveErr(null);
    setSaveOk(null);
    setIsEditing(true);
  }

  function handleCancelEditing() {
    if (saving) return;
    if (baseProfile) resetDraftFrom(baseProfile);
    setSaveErr(null);
    setSaveOk(null);
    setIsEditing(false);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaveErr(null);
    setSaveOk(null);

    if (!isEditing) return;

    if (validationError) {
      setSaveErr(validationError);
      return;
    }

    if (!isDirty) {
      setIsEditing(false);
      return;
    }

    const body = toProfileUpdateRequest({ username, bio, avatarUrl, location, website });

    setSaving(true);
    try {
      const updated = await updateMyProfile(body);
      setProfile(updated);
      setBaseProfile(updated);
      resetDraftFrom(updated);
      setIsEditing(false);
      setSaveOk('已提交审核');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : undefined;
      setSaveErr(msg || '保存失败');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <div className="text-gray-600">加载中…</div>;
  }

  if (!profile) {
    return (
      <div className="space-y-3">
        <div>
          <h3 className="text-lg font-semibold">个人资料</h3>
          <p className="text-gray-600">无法加载个人资料。</p>
        </div>
        {loadErr ? <div className="text-red-600 text-sm">{loadErr}</div> : null}
        <button
          type="button"
          className="px-4 py-2 rounded-md bg-gray-900 text-white hover:bg-gray-800"
          onClick={() => window.location.reload()}
        >
          刷新重试
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-lg font-semibold">个人资料</h3>
        {profile.profileModeration?.status ? (
          <div className="text-sm text-gray-600">
            审核状态：
            {formatProfileModerationStatus(profile.profileModeration.status)}
            {profile.profileModeration.reason ? `（${profile.profileModeration.reason}）` : null}
          </div>
        ) : null}
      </div>

      {loadErr ? <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{loadErr}</div> : null}

      <form className="space-y-4" onSubmit={handleSubmit}>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">用户ID</label>
            <input value={String(profile.id)} disabled className="w-full bg-gray-50 border border-gray-200 rounded-md px-3 py-2 text-gray-600" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">邮箱（登录名）</label>
            <input value={profile.email} disabled className="w-full bg-gray-50 border border-gray-200 rounded-md px-3 py-2 text-gray-600" />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">头像</label>
          <ProfileAvatarUploader value={avatarUrl || undefined} onChange={(v) => setAvatarUrl(v)} disabled={!isEditing || saving} />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">昵称</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={!isEditing || saving}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600"
            placeholder="输入昵称..."
            maxLength={64}
            autoComplete="nickname"
          />
          {isEditing ? <p className="mt-1 text-xs text-gray-500">最多 64 字。</p> : null}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">简介</label>
          <textarea
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            rows={4}
            disabled={!isEditing || saving}
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600"
            placeholder="一句话介绍自己..."
          />
          {isEditing ? <div className="mt-1 text-xs text-gray-500">{bio.trim().length}/500</div> : null}
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">地区</label>
            <input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              disabled={!isEditing || saving}
              className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600"
              placeholder="例如：上海 / 北京"
              maxLength={64}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">个人网站</label>
            <input
              value={website}
              onChange={(e) => setWebsite(e.target.value)}
              disabled={!isEditing || saving}
              className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600"
              placeholder="https://example.com"
              maxLength={191}
            />
            {isEditing ? <p className="mt-1 text-xs text-gray-500">仅支持 http/https。</p> : null}
          </div>
        </div>

        {saveErr ? <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{saveErr}</div> : null}
        {saveOk ? <div className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{saveOk}</div> : null}

        <div className="flex items-center gap-3">
          {isEditing ? (
            <>
              <button
                type="submit"
                disabled={saving || !!validationError || !isDirty}
                className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {saving ? '保存中…' : '保存'}
              </button>

              <button
                type="button"
                disabled={saving}
                className="px-4 py-2 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                onClick={handleCancelEditing}
              >
                放弃修改
              </button>

              {validationError ? <span className="text-sm text-gray-500">{validationError}</span> : null}
              {!validationError && !isDirty ? <span className="text-sm text-gray-500">未修改</span> : null}
            </>
          ) : (
            <button
              type="button"
              disabled={saving}
              className="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
              onClick={handleStartEditing}
            >
              编辑
            </button>
          )}
        </div>
      </form>

      <div className="space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">我发布的帖子</h4>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 text-sm"
              onClick={() => {
                setPostsPageNo(1);
                setPostsReloadTick((n) => n + 1);
              }}
              disabled={postsLoading}
            >
              刷新
            </button>
            <button
              type="button"
              className="px-3 py-2 rounded-md bg-gray-900 text-white hover:bg-gray-800 text-sm"
              onClick={() => navigate('/portal/account/mine')}
            >
              管理
            </button>
          </div>
        </div>

        <PostFeed
          page={postsPage}
          loading={postsLoading}
          error={postsErr}
          onRetry={() => setPostsReloadTick((n) => n + 1)}
          onPrev={() => setPostsPageNo((n) => Math.max(1, n - 1))}
          onNext={() => setPostsPageNo((n) => n + 1)}
        />
      </div>
    </div>
  );
}
