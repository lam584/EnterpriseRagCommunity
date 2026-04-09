import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getMyProfile, updateMyProfile } from '../../../../services/accountService';
import { getPublicUserProfile } from '../../../../services/publicUserProfileService';
import { listMyPostsPage, listPostsPage } from '../../../../services/postService';
import { reportProfile } from '../../../../services/reportService';
import type { UserProfile } from '../../../../types/userProfile';
import { resolveAssetUrl } from '../../../../utils/urlUtils';
import ProfileAvatarUploader from '../../account/components/ProfileAvatarUploader';
import SubTabsNav from '../../components/SubTabsNav';
import { getPortalSection } from '../../portalMenu';
import PostFeed from '../../discover/components/PostFeed';
import { formatProfileModerationStatus } from '../../profileModerationText';
import { hasProfileDraftChanges, validateProfileFields } from '../../account/pages/profileValidationUtils';
import { toProfileUpdateRequest, useProfilePageState } from '../../account/pages/profilePageShared';

function sleepMs(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toUserProfileFromPublic(dto: { id: number; username: string; avatarUrl?: string | null; bio?: string | null; location?: string | null; website?: string | null }): UserProfile {
  return {
    id: dto.id,
    email: '',
    username: dto.username ?? '',
    avatarUrl: dto.avatarUrl == null ? undefined : String(dto.avatarUrl),
    bio: dto.bio == null ? undefined : String(dto.bio),
    location: dto.location == null ? undefined : String(dto.location),
    website: dto.website == null ? undefined : String(dto.website),
  };
}

export default function UserProfilePage() {
  const navigate = useNavigate();
  const routeLocation = useLocation();
  const { userId } = useParams();
  const mountedRef = useRef(true);

  const parsedParamUserId = useMemo(() => {
    if (typeof userId !== 'string') return null;
    const n = Number(userId);
    return Number.isFinite(n) ? n : null;
  }, [userId]);

  const paramInvalid = typeof userId === 'string' && parsedParamUserId == null;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isSelf, setIsSelf] = useState(false);
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

  const [profileReportOpen, setProfileReportOpen] = useState(false);
  const [profileReportPending, setProfileReportPending] = useState(false);
  const [profileReportDone, setProfileReportDone] = useState(false);
  const [profileReportError, setProfileReportError] = useState<string | null>(null);
  const [profileReportReasonCode, setProfileReportReasonCode] = useState('SPAM');
  const [profileReportReasonText, setProfileReportReasonText] = useState('');

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      if (paramInvalid) {
        setProfile(null);
        setBaseProfile(null);
        setIsEditing(false);
        setIsSelf(false);
        setLoadErr('用户ID不正确');
        setLoading(false);
        return;
      }

      setLoading(true);
      setLoadErr(null);
      setSaveErr(null);
      setSaveOk(null);
      try {
        const me = await getMyProfile();
        if (cancelled) return;

        const targetUserId = parsedParamUserId ?? me.id;
        const self = targetUserId === me.id;
        setIsSelf(self);

        let p: UserProfile;
        if (self) {
          p = me;
          setBaseProfile(me);
        } else {
          const pub = await getPublicUserProfile(targetUserId);
          if (cancelled) return;
          p = toUserProfileFromPublic(pub);
          setBaseProfile(null);
        }

        setProfile(p);
        resetDraftFrom(p);
        setIsEditing(false);
        setPostsPageNo(1);
        setPostsPage(null);
        setPostsErr(null);
        setPostsReloadTick((n) => n + 1);
      } catch (e: unknown) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : undefined;
        setLoadErr(msg || '加载个人资料失败');
        setProfile(null);
        setBaseProfile(null);
        setIsSelf(false);
        setIsEditing(false);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [paramInvalid, parsedParamUserId, resetDraftFrom, setBaseProfile, setIsEditing, setPostsErr, setPostsPage, setPostsPageNo, setPostsReloadTick, setProfile, setSaveOk]);

  async function refreshMyProfileModerationSoon() {
    if (!mountedRef.current) return;
    if (!isSelf) return;
    const delays = [0, 200, 400, 800, 1200, 1600];
    for (const d of delays) {
      if (!mountedRef.current) return;
      if (d > 0) await sleepMs(d);
      if (!mountedRef.current) return;
      try {
        const me = await getMyProfile();
        if (!mountedRef.current) return;
        setProfile(me);
        setBaseProfile(me);
        if (me.profileModeration?.status && me.profileModeration.status !== 'PENDING') return;
      } catch {
        return;
      }
    }
  }

  useEffect(() => {
    let cancelled = false;

    async function run() {
      if (!profile) return;

      setPostsLoading(true);
      setPostsErr(null);
      try {
        const page = isSelf
          ? await listMyPostsPage({
              status: 'PUBLISHED',
              page: postsPageNo,
              pageSize: 10,
              sortBy: 'publishedAt',
              sortOrderDirection: 'DESC',
            })
          : await listPostsPage({
              authorId: profile.id,
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
  }, [isSelf, postsPageNo, postsReloadTick, profile, setPostsErr, setPostsLoading, setPostsPage]);

  const validationError = useMemo(
    () => validateProfileFields({ username, bio, location, website }),
    [bio, location, username, website]
  );

  const isDirty = useMemo(() => {
    return hasProfileDraftChanges(baseProfile, { username, bio, avatarUrl, location, website });
  }, [avatarUrl, baseProfile, bio, location, username, website]);

  function handleStartEditing() {
    if (!isSelf) return;
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

    if (!isSelf) return;
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
      void refreshMyProfileModerationSoon();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : undefined;
      setSaveErr(msg || '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function onSubmitProfileReport() {
    if (!profile) return;
    if (isSelf) return;
    const reasonCode = profileReportReasonCode.trim();
    if (!reasonCode) {
      setProfileReportError('请选择举报原因');
      return;
    }

    setProfileReportPending(true);
    setProfileReportError(null);
    try {
      await reportProfile(profile.id, { reasonCode, reasonText: profileReportReasonText.trim() || undefined });
      setProfileReportDone(true);
      setProfileReportOpen(false);
      setProfileReportReasonText('');
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : undefined;
      setProfileReportError(msg || '举报失败');
    } finally {
      setProfileReportPending(false);
    }
  }

  const avatarDisplayUrl = resolveAssetUrl(avatarUrl);
  const canEdit = isSelf;
  const inputsDisabled = !canEdit || !isEditing || saving;

  if (loading) {
    return <div className="text-gray-600">加载中…</div>;
  }

  if (!profile) {
    return (
      <div className="space-y-3">
        <div>
          <h3 className="text-lg font-semibold">个人资料</h3>
          <p className="text-gray-600">无法加载该用户资料。</p>
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

  const accountSection = getPortalSection('account');
  const subTabsItems = accountSection.children.map((c) => ({
    id: c.id,
    label: c.label,
    to: c.id === 'profile' ? `/portal/users/${profile.id}` : (c.to ?? `${accountSection.basePath}/${c.path}`),
  }));

  const showSubTabs = isSelf && routeLocation.pathname.startsWith('/portal/users/');

  const content = (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">个人资料</h3>
          {isSelf && profile.profileModeration?.status ? (
            <div className="text-sm text-gray-600">
              审核状态：
              {formatProfileModerationStatus(profile.profileModeration.status)}
              {profile.profileModeration.reason ? `（${profile.profileModeration.reason}）` : null}
            </div>
          ) : null}
        </div>

        {!isSelf ? (
          <button
            type="button"
            className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 text-sm disabled:opacity-50"
            onClick={() => {
              setProfileReportOpen(true);
              setProfileReportError(null);
              setProfileReportDone(false);
            }}
            disabled={profileReportPending || profileReportDone}
            title={profileReportDone ? '已举报' : '举报该用户资料'}
          >
            {profileReportDone ? '已举报' : '举报资料'}
          </button>
        ) : null}
      </div>

      {loadErr ? <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{loadErr}</div> : null}

      <form className="space-y-4" onSubmit={handleSubmit}>
        {isSelf ? (
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
        ) : (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">用户ID</label>
            <input value={String(profile.id)} disabled className="w-full bg-gray-50 border border-gray-200 rounded-md px-3 py-2 text-gray-600" />
          </div>
        )}

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">头像</label>
          <ProfileAvatarUploader
            value={avatarDisplayUrl || undefined}
            onChange={(v) => setAvatarUrl(v)}
            disabled={!canEdit || !isEditing || saving}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">昵称</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={inputsDisabled}
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
            disabled={inputsDisabled}
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
              disabled={inputsDisabled}
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
              disabled={inputsDisabled}
              className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:text-gray-600"
              placeholder="https://example.com"
              maxLength={191}
            />
            {isEditing ? <p className="mt-1 text-xs text-gray-500">仅支持 http/https。</p> : null}
          </div>
        </div>

        {saveErr ? <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{saveErr}</div> : null}
        {saveOk ? <div className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{saveOk}</div> : null}

        {isSelf ? (
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
        ) : null}
      </form>

      <div className="space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-base font-semibold">{isSelf ? '我发布的帖子' : '已发布的帖子'}</h4>
          {isSelf ? (
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
          ) : (
            <button
              type="button"
              className="px-3 py-2 rounded-md border border-gray-300 hover:bg-gray-50 text-sm"
              onClick={() => setPostsReloadTick((n) => n + 1)}
              disabled={postsLoading}
            >
              刷新
            </button>
          )}
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

      {profileReportOpen ? (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg shadow-lg w-full max-w-lg">
            <div className="flex items-center justify-between px-4 py-3 border-b">
              <div className="font-semibold">举报资料</div>
              <button
                type="button"
                className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-50"
                onClick={() => setProfileReportOpen(false)}
                disabled={profileReportPending}
              >
                关闭
              </button>
            </div>

            <div className="p-4 space-y-3">
              {profileReportError ? (
                <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{profileReportError}</div>
              ) : null}

              <div className="space-y-1">
                <div className="text-sm text-gray-700">举报原因</div>
                <select
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                  value={profileReportReasonCode}
                  onChange={(e) => setProfileReportReasonCode(e.target.value)}
                  disabled={profileReportPending}
                >
                  <option value="SPAM">垃圾/广告</option>
                  <option value="ABUSE">辱骂/骚扰</option>
                  <option value="HATE">仇恨/歧视</option>
                  <option value="PORN">色情/低俗</option>
                  <option value="ILLEGAL">违法违规</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>

              <div className="space-y-1">
                <div className="text-sm text-gray-700">补充说明（可选）</div>
                <textarea
                  className="w-full min-h-[90px] rounded border border-gray-300 px-3 py-2 text-sm"
                  value={profileReportReasonText}
                  onChange={(e) => setProfileReportReasonText(e.target.value)}
                  disabled={profileReportPending}
                  placeholder="请描述举报原因，便于审核"
                />
              </div>

              <div className="flex justify-end gap-2 pt-1">
                <button
                  type="button"
                  className="rounded border px-4 py-2 hover:bg-gray-50 disabled:opacity-50"
                  onClick={() => setProfileReportOpen(false)}
                  disabled={profileReportPending}
                >
                  取消
                </button>
                <button
                  type="button"
                  className="rounded bg-red-600 text-white px-4 py-2 hover:bg-red-700 disabled:opacity-50"
                  onClick={onSubmitProfileReport}
                  disabled={profileReportPending}
                >
                  {profileReportPending ? '提交中...' : '提交举报'}
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
  if (showSubTabs) {
    return (
      <div className="space-y-4">
        <SubTabsNav title={accountSection.label} items={subTabsItems} />
        <div className="bg-white rounded-lg border border-gray-200 p-4">{content}</div>
      </div>
    );
  }
  return content;
}
