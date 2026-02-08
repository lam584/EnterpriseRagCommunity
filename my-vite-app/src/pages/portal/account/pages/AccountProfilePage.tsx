import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMyProfile, updateMyProfile } from '../../../../services/accountService';
import { listMyPostsPage, type PostDTO } from '../../../../services/postService';
import type { SpringPage } from '../../../../types/page';
import type { UpdateUserProfileRequest, UserProfile } from '../../../../types/userProfile';
import ProfileAvatarUploader from '../components/ProfileAvatarUploader';
import PostFeed from '../../discover/components/PostFeed';

function isValidWebsite(v: string): boolean {
  if (!v.trim()) return true;
  try {
    const u = new URL(v);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

export default function AccountProfilePage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadErr, setLoadErr] = useState<string | null>(null);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const [saveOk, setSaveOk] = useState<string | null>(null);

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [baseProfile, setBaseProfile] = useState<UserProfile | null>(null);

  const [isEditing, setIsEditing] = useState(false);

  const [username, setUsername] = useState('');
  const [bio, setBio] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [location, setLocation] = useState('');
  const [website, setWebsite] = useState('');

  const [postsLoading, setPostsLoading] = useState(false);
  const [postsErr, setPostsErr] = useState<string | null>(null);
  const [postsPage, setPostsPage] = useState<SpringPage<PostDTO> | null>(null);
  const [postsPageNo, setPostsPageNo] = useState(1);
  const [postsReloadTick, setPostsReloadTick] = useState(0);

  function resetDraftFrom(p: UserProfile) {
    setUsername(p.username ?? '');
    setBio(p.bio ?? '');
    setAvatarUrl(p.avatarUrl ?? '');
    setLocation(p.location ?? '');
    setWebsite(p.website ?? '');
  }

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

  const validationError = useMemo(() => {
    const u = username.trim();
    if (!u) return '昵称不能为空';
    if (u.length > 64) return '昵称不能超过 64 个字符';

    const b = bio.trim();
    if (b.length > 500) return '简介不能超过 500 个字符';

    const loc = location.trim();
    if (loc.length > 64) return '地区不能超过 64 个字符';

    const w = website.trim();
    if (w.length > 191) return '网站不能超过 191 个字符';
    if (!isValidWebsite(w)) return '网站链接格式不正确（需要 http/https）';

    return null;
  }, [username, bio, location, website]);

  const isDirty = useMemo(() => {
    if (!baseProfile) return false;

    const norm = (v: string) => v.trim();
    const emptyToUndef = (v: string) => {
      const t = norm(v);
      return t ? t : undefined;
    };

    const curr = {
      username: norm(username),
      bio: emptyToUndef(bio),
      avatarUrl: emptyToUndef(avatarUrl),
      location: emptyToUndef(location),
      website: emptyToUndef(website),
    };

    const base = {
      username: norm(baseProfile.username ?? ''),
      bio: emptyToUndef(baseProfile.bio ?? ''),
      avatarUrl: emptyToUndef(baseProfile.avatarUrl ?? ''),
      location: emptyToUndef(baseProfile.location ?? ''),
      website: emptyToUndef(baseProfile.website ?? ''),
    };

    return (
      curr.username !== base.username ||
      curr.bio !== base.bio ||
      curr.avatarUrl !== base.avatarUrl ||
      curr.location !== base.location ||
      curr.website !== base.website
    );
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

    const toPatchValue = (v: string): string | null | undefined => {
      // undefined means "don't send / no change"
      // null means "clear"
      // string means "set"
      const t = v.trim();
      if (t === '') return null;
      return t;
    };

    const body: UpdateUserProfileRequest = {
      username: username.trim(),
      bio: toPatchValue(bio),
      avatarUrl: toPatchValue(avatarUrl),
      location: toPatchValue(location),
      website: toPatchValue(website),
    };

    setSaving(true);
    try {
      const updated = await updateMyProfile(body);
      setProfile(updated);
      setBaseProfile(updated);
      resetDraftFrom(updated);
      setIsEditing(false);
      setSaveOk('已保存');
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
