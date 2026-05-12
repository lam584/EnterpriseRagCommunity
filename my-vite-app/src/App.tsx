// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useState, useEffect, lazy, Suspense, useRef, type ReactElement } from 'react';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { AccessProvider, useAccess } from './contexts/AccessContext';
import { checkInitialSetupStatus } from './services/auth/authService';
import { RequirePermission } from './components/auth/RequirePermission';
import { RequireAccess } from './components/auth/RequireAccess';
import RequireModeratedBoards from './components/auth/RequireModeratedBoards';
import { getStoredUserId } from './services/auth/portalAuthService';
import { getPortalSection } from './pages/portal/portalMenu';

const Login = lazy(() => import('./components/login/Login'));
const AdminSetup = lazy(() => import('./components/login/AdminSetup'));
const Register = lazy(() => import('./components/login/Register'));
const ForgotPassword = lazy(() => import('./components/login/ForgotPassword'));
const ForbiddenPage = lazy(() => import('./pages/ForbiddenPage'));
const CommunityPortalLayout = lazy(() => import('./pages/portal/CommunityPortalLayout'));
const AdminDashboardLayout = lazy(() => import('./pages/admin/AdminDashboardLayout'));
const DiscoverLayout = lazy(() => import('./pages/portal/discover/DiscoverLayout'));
const PostsLayout = lazy(() => import('./pages/portal/posts/PostsLayout'));
const InteractLayout = lazy(() => import('./pages/portal/interact/InteractLayout'));
const AssistantLayout = lazy(() => import('./pages/portal/assistant/AssistantLayout'));
const AccountLayout = lazy(() => import('./pages/portal/account/AccountLayout'));
const ModerationLayout = lazy(() => import('./pages/portal/moderation/ModerationLayout'));
const SearchLayout = lazy(() => import('./pages/portal/search/SearchLayout'));
const DiscoverHomePage = lazy(() => import('./pages/portal/discover/pages/DiscoverHomePage'));
const DiscoverBoardsPage = lazy(() => import('./pages/portal/discover/pages/DiscoverBoardsPage'));
const DiscoverTagsPage = lazy(() => import('./pages/portal/discover/pages/DiscoverTagsPage'));
const DiscoverHotPage = lazy(() => import('./pages/portal/discover/pages/DiscoverHotPage'));
const SearchPostsPage = lazy(() => import('./pages/portal/search/pages/SearchPostsPage'));
const PostsCreatePage = lazy(() => import('./pages/portal/posts/pages/PostsCreatePage'));
const PostsDraftsPage = lazy(() => import('./pages/portal/posts/pages/PostsDraftsPage'));
const PostsMinePage = lazy(() => import('./pages/portal/posts/pages/PostsMinePage'));
const PostsBookmarksPage = lazy(() => import('./pages/portal/posts/pages/PostsBookmarksPage'));
const PostDetailPage = lazy(() => import('./pages/portal/posts/pages/PostDetailPage'));
const InteractAllPage = lazy(() => import('./pages/portal/interact/pages/InteractAllPage'));
const InteractRepliesPage = lazy(() => import('./pages/portal/interact/pages/InteractRepliesPage'));
const InteractLikesPage = lazy(() => import('./pages/portal/interact/pages/InteractLikesPage'));
const InteractMentionsPage = lazy(() => import('./pages/portal/interact/pages/InteractMentionsPage'));
const InteractReportsPage = lazy(() => import('./pages/portal/interact/pages/InteractReportsPage'));
const InteractModerationPage = lazy(() => import('./pages/portal/interact/pages/InteractModerationPage'));
const InteractSecurityPage = lazy(() => import('./pages/portal/interact/pages/InteractSecurityPage'));
const AssistantChatPage = lazy(() => import('./pages/portal/assistant/pages/AssistantChatPage'));
const AssistantHistoryPage = lazy(() => import('./pages/portal/assistant/pages/AssistantHistoryPage'));
const AssistantCollectionsPage = lazy(() => import('./pages/portal/assistant/pages/AssistantCollectionsPage'));
const AssistantSettingsPage = lazy(() => import('./pages/portal/assistant/pages/AssistantSettingsPage'));
const AccountSecurityPage = lazy(() => import('./pages/portal/account/pages/AccountSecurityPage'));
const AccountPreferencesPage = lazy(() => import('./pages/portal/account/pages/AccountPreferencesPage'));
const MyCommentsPage = lazy(() => import('./pages/portal/account/pages/MyCommentsPage'));
const UserProfilePage = lazy(() => import('./pages/portal/users/pages/UserProfilePage'));
const ModerationQueuePage = lazy(() => import('./pages/portal/moderation/pages/ModerationQueuePage'));
const ModerationMyLogsPage = lazy(() => import('./pages/portal/moderation/pages/ModerationMyLogsPage'));

const routeLoadingFallback = <div className="flex justify-center items-center h-screen">加载中...</div>;
const adminRouteLoadingFallback = <div className="p-4">正在加载模块…</div>;

const withRouteSuspense = (element: ReactElement, fallback = routeLoadingFallback) => (
    <Suspense fallback={fallback}>{element}</Suspense>
);

// NOTE: admin sections are lazy-loaded to reduce main-thread EvaluateScript on menu switch.
const ContentMgmtPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.ContentMgmtPage })));
const ReviewCenterPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.ReviewCenterPage })));
const SemanticBoostPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.SemanticBoostPage })));
const RetrievalRagPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.RetrievalRagPage })));
const MetricsMonitorPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.MetricsMonitorPage })));
const UsersRBACPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.UsersRBACPage })));
const LlmConfigPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.LlmConfigPage })));

function SectionIndexRedirect({ sectionId }: { sectionId: Parameters<typeof getPortalSection>[0] }) {
    const section = getPortalSection(sectionId);
    const firstChild = section.children?.find(child => !child.to) ?? section.children?.[0];
    const targetPath = firstChild ? (firstChild.to ?? `${section.basePath}/${firstChild.path}`) : section.basePath;
    return <Navigate to={targetPath} replace />;
}

function PostsSectionIndexRedirect() {
    const section = getPortalSection('compose');
    const firstChild = section.children[0];
    const targetPath = firstChild ? `${section.basePath}/${firstChild.path}` : '/portal/posts/drafts';
    return <Navigate to={targetPath} replace />;
}

function toNumId(v: unknown): number | undefined {
    if (typeof v === 'number' && Number.isFinite(v)) return v;
    if (typeof v === 'string' && v.trim() !== '') {
        const n = Number(v);
        return Number.isFinite(n) ? n : undefined;
    }
    return undefined;
}

function AccountSectionIndexRedirect() {
    const { currentUser } = useAuth();
    const section = getPortalSection('account');
    const userId = toNumId((currentUser as unknown as { id?: unknown } | null)?.id) ?? getStoredUserId();
    const targetPath = userId ? `/portal/users/${userId}` : `${section.basePath}/profile`;
    return <Navigate to={targetPath} replace />;
}

// 受保护的路由组件
const ProtectedRoute = () => {
    const { isAuthenticated, loading } = useAuth();

    if (loading) {
        return <div className="flex justify-center items-center h-screen">加载中...</div>;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    return <Outlet />;
};

// Admin index redirect: if user can't access content, don't use it as default homepage.
function AdminIndexRedirect() {
    const { hasPerm, hasRole, loading } = useAccess();
    if (loading) return <div className="p-4">加载中...</div>;

    // If user isn't allowed into admin UI at all, go to 403.
    const canEnterAdmin = hasPerm('admin_ui', 'access') || hasRole('ADMIN');
    if (!canEnterAdmin) return <Navigate to="/forbidden" replace />;

    // Pick first accessible admin section.
    if (hasPerm('admin_content', 'access')) return <Navigate to="content" replace />;
    if (hasPerm('admin_review', 'access')) return <Navigate to="review" replace />;
    if (hasPerm('admin_semantic', 'access')) return <Navigate to="semantic" replace />;
    if (hasPerm('admin_retrieval', 'access')) return <Navigate to="retrieval" replace />;
    if (hasPerm('admin_metrics', 'access')) return <Navigate to="metrics" replace />;
    if (hasPerm('admin_users', 'access')) return <Navigate to="users" replace />;

    // No section perms at all -> forbidden.
    return <Navigate to="/forbidden" replace />;
}

export function AppRoutes() {
    useAuth();
    const [setupRequired, setSetupRequired] = useState<boolean | null>(null);
    const [loading, setLoading] = useState(true);
    const didCheckSetupRef = useRef(false);

    // 检查系统是否需要初始设置
    useEffect(() => {
        if (didCheckSetupRef.current) return;
        didCheckSetupRef.current = true;

        const checkSetupStatus = async () => {
            try {
                const { setupRequired } = await checkInitialSetupStatus();
                setSetupRequired(setupRequired);
            } catch (error) {
                console.error('检查系统初始设置状态失败:', error);
                // 如果检查失败，默认不需要初始设置
                setSetupRequired(false);
            } finally {
                setLoading(false);
            }
        };

        checkSetupStatus();
    }, []);

    if (loading) {
        return <div className="flex justify-center items-center h-screen">检查系统状态中...</div>;
    }

    return (
        <Routes>
            {/* 根路径重定向逻辑 */}
            <Route path="/" element={
                setupRequired
                    ? <Navigate to="/admin-setup" replace />
                    : <Navigate to="/portal/discover/home" replace />
            } />

            {/* 初始管理员设置页面 */}
            <Route path="/admin-setup" element={
                setupRequired ? withRouteSuspense(<AdminSetup />) : <Navigate to="/" replace />
            } />

            {/* 登录页面 - 如果需要初始设置则重定向到初始设置页面 */}
            <Route path="/login" element={
                withRouteSuspense(<LoginRouteElement setupRequired={setupRequired} onBypassSetup={() => setSetupRequired(false)} />)
            } />

            {/* 前台门户（普通用户/访客） */}
            <Route path="/portal" element={withRouteSuspense(<CommunityPortalLayout />)}>
                <Route path="discover" element={withRouteSuspense(<DiscoverLayout />)}>
                    <Route index element={<SectionIndexRedirect sectionId="discover" />} />

                    {/* 发现：按权限控制（portal_discover_home/boards/tags/hot:view） */}
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_home" action="view" />}>
                        <Route path="home" element={withRouteSuspense(<DiscoverHomePage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_boards" action="view" />}>
                        <Route path="boards" element={withRouteSuspense(<DiscoverBoardsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_tags" action="view" />}>
                        <Route path="tags" element={withRouteSuspense(<DiscoverTagsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_hot" action="view" />}>
                        <Route path="hot" element={withRouteSuspense(<DiscoverHotPage />)} />
                    </Route>

                    {/* discover/search 快捷入口仍然保留，但 search 页面本身也有权限控制 */}
                    <Route path="search" element={<Navigate to="/portal/search/posts" replace />} />
                </Route>

                <Route path="search" element={withRouteSuspense(<SearchLayout />)}>
                    <Route index element={<Navigate to="/portal/search/posts" replace />} />
                    <Route element={<RequireAccess requiresAuth resource="portal_search_posts" action="view" />}>
                        <Route path="posts" element={withRouteSuspense(<SearchPostsPage />)} />
                    </Route>
                </Route>

                <Route path="posts" element={withRouteSuspense(<PostsLayout />)}>
                    <Route index element={<PostsSectionIndexRedirect />} />

                    {/* 写作相关：需要登录 + portal_posts:create */}
                    <Route element={<RequireAccess requiresAuth resource="portal_posts" action="create" /> }>
                        <Route path="create" element={withRouteSuspense(<PostsCreatePage />)} />
                        <Route path="edit/:postId" element={withRouteSuspense(<PostsCreatePage />)} />
                    </Route>

                    {/* 草稿/我的/收藏：需要登录 + 对应 view 权限 */}
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_drafts" action="view" /> }>
                        <Route path="drafts" element={withRouteSuspense(<PostsDraftsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_mine" action="view" /> }>
                        <Route path="mine" element={withRouteSuspense(<PostsMinePage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_bookmarks" action="view" /> }>
                        <Route path="bookmarks" element={withRouteSuspense(<PostsBookmarksPage />)} />
                    </Route>

                    {/* 详情页：公开 */}
                    <Route path="detail/:postId" element={withRouteSuspense(<PostDetailPage />)} />
                </Route>

                {/* 互动中心：需要登录 + 对应 view 权限 */}
                <Route path="interact" element={withRouteSuspense(<InteractLayout />)}>
                    <Route index element={<SectionIndexRedirect sectionId="interact" />} />

                    <Route element={<RequireAccess requiresAuth resource="portal_interact_replies" action="view" /> }>
                        <Route path="replies" element={withRouteSuspense(<InteractRepliesPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_interact_likes" action="view" /> }>
                        <Route path="likes" element={withRouteSuspense(<InteractLikesPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_interact_mentions" action="view" /> }>
                        <Route path="mentions" element={withRouteSuspense(<InteractMentionsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_interact_reports" action="view" /> }>
                        <Route path="reports" element={withRouteSuspense(<InteractReportsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth /> }>
                        <Route path="all" element={withRouteSuspense(<InteractAllPage />)} />
                        <Route path="moderation" element={withRouteSuspense(<InteractModerationPage />)} />
                        <Route path="security" element={withRouteSuspense(<InteractSecurityPage />)} />
                    </Route>
                </Route>

                {/* 智能助手：需要登录 + 对应 view 权限 */}
                <Route path="assistant" element={withRouteSuspense(<AssistantLayout />)}>
                    <Route index element={<SectionIndexRedirect sectionId="assistant" />} />

                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_chat" action="view" /> }>
                        <Route path="chat" element={withRouteSuspense(<AssistantChatPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_history" action="view" /> }>
                        <Route path="history" element={withRouteSuspense(<AssistantHistoryPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_collections" action="view" /> }>
                        <Route path="collections" element={withRouteSuspense(<AssistantCollectionsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_settings" action="view" /> }>
                        <Route path="settings" element={withRouteSuspense(<AssistantSettingsPage />)} />
                    </Route>
                </Route>

                <Route element={<RequireAccess requiresAuth />}>
                    <Route path="moderation" element={withRouteSuspense(<ModerationLayout />)}>
                        <Route index element={<SectionIndexRedirect sectionId="moderation" />} />
                        <Route element={<RequireModeratedBoards />}>
                            <Route path="queue" element={withRouteSuspense(<ModerationQueuePage />)} />
                        </Route>
                        <Route path="logs" element={withRouteSuspense(<ModerationMyLogsPage />)} />
                    </Route>
                </Route>

                {/* 账号中心：需要登录 + 对应 view 权限 */}
                <Route path="account" element={withRouteSuspense(<AccountLayout />)}>
                    <Route index element={<AccountSectionIndexRedirect />} />

                    <Route element={<RequireAccess requiresAuth resource="portal_account_profile" action="view" /> }>
                        <Route path="profile" element={withRouteSuspense(<UserProfilePage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_account_security" action="view" /> }>
                        <Route path="security" element={withRouteSuspense(<AccountSecurityPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_account_preferences" action="view" /> }>
                        <Route path="preferences" element={withRouteSuspense(<AccountPreferencesPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_account_connections" action="view" /> }>
                        <Route path="connections" element={<Navigate to="/portal/account/security#email" replace />} />
                    </Route>

                    <Route element={<RequireAccess requiresAuth resource="portal_posts_mine" action="view" /> }>
                        <Route path="mine" element={withRouteSuspense(<PostsMinePage />)} />
                        <Route path="comments" element={withRouteSuspense(<MyCommentsPage />)} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_bookmarks" action="view" /> }>
                        <Route path="bookmarks" element={withRouteSuspense(<PostsBookmarksPage />)} />
                    </Route>
                </Route>

                <Route element={<RequireAccess requiresAuth />}>
                    <Route path="users/:userId" element={withRouteSuspense(<UserProfilePage />)} />
                </Route>

                <Route index element={<Navigate to="discover" replace />} />
            </Route>

            {/* 403 无权限 */}
            <Route path="/forbidden" element={withRouteSuspense(<ForbiddenPage />)} />

            {/* 受保护的路由组 */}
            <Route element={<ProtectedRoute />}>
                {/**
                 * 管理员后台入口：
                 * - 生产建议使用细粒度权限 admin_ui:access
                 * - 开发阶段允许 ROLE_ADMIN 直接进入，避免权限矩阵未初始化导致误判 403
                 */}
                <Route element={<RequirePermission resource="admin_ui" action="access" allowRoles={["ADMIN"]} />}>
                    {/* 后台管理（审核员/管理员） */}
                    <Route path="/admin" element={withRouteSuspense(<AdminDashboardLayout />, adminRouteLoadingFallback)}>
                        <Route path="content" element={withRouteSuspense(<ContentMgmtPage />, adminRouteLoadingFallback)} />
                        <Route path="review" element={withRouteSuspense(<ReviewCenterPage />, adminRouteLoadingFallback)} />
                        <Route path="semantic" element={withRouteSuspense(<SemanticBoostPage />, adminRouteLoadingFallback)} />
                        <Route path="retrieval" element={withRouteSuspense(<RetrievalRagPage />, adminRouteLoadingFallback)} />
                        <Route path="llm-config" element={withRouteSuspense(<LlmConfigPage />, adminRouteLoadingFallback)} />
                        <Route path="metrics" element={withRouteSuspense(<MetricsMonitorPage />, adminRouteLoadingFallback)} />
                        <Route path="users" element={withRouteSuspense(<UsersRBACPage />, adminRouteLoadingFallback)} />
                        <Route index element={<AdminIndexRedirect />} />
                    </Route>
                </Route>

            </Route>

            {/* 新闻相关页面 - 公开访问 */}

            <Route path="/register" element={withRouteSuspense(<Register />)} />
            <Route path="/forgot-password" element={withRouteSuspense(<ForgotPassword />)} />

            {/* 兜底路由 */}
            <Route path="*" element={<Navigate to="/portal/discover" replace />} />
        </Routes>
    );
}

function LoginRouteElement({ setupRequired, onBypassSetup }: { setupRequired: boolean | null; onBypassSetup: () => void }) {
    const location = useLocation();
    const state = location.state as { setupJustCompleted?: boolean } | null;
    const bypass = Boolean(state?.setupJustCompleted);

    useEffect(() => {
        if (bypass) onBypassSetup();
    }, [bypass, onBypassSetup]);

    if (setupRequired && !bypass) return <Navigate to="/admin-setup" replace />;
    return <Login />;
}

function App() {
    return (
        <AuthProvider>
            <AccessProvider>
                <BrowserRouter>
                    <AppRoutes />
                    <Toaster position="top-right" />
                </BrowserRouter>
            </AccessProvider>
        </AuthProvider>
    )
}

export default App
