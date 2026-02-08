// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useState, useEffect, lazy, Suspense } from 'react';
import { Toaster } from 'react-hot-toast';
import Login from './components/login/Login';
import AdminSetup from './components/login/AdminSetup';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { AccessProvider, useAccess } from './contexts/AccessContext';
import { checkInitialSetupStatus } from './services/authService';
import CommunityPortalLayout from './pages/portal/CommunityPortalLayout';
import AdminDashboardLayout from './pages/admin/AdminDashboardLayout';
import Register from './components/login/Register';
import ForgotPassword from './components/login/ForgotPassword';
import DiscoverLayout, { DiscoverIndexRedirect } from './pages/portal/discover/DiscoverLayout';
import PostsLayout, { PostsIndexRedirect } from './pages/portal/posts/PostsLayout';
import InteractLayout, { InteractIndexRedirect } from './pages/portal/interact/InteractLayout';
import AssistantLayout, { AssistantIndexRedirect } from './pages/portal/assistant/AssistantLayout';
import AccountLayout, { AccountIndexRedirect } from './pages/portal/account/AccountLayout';
import ModerationLayout, { ModerationIndexRedirect } from './pages/portal/moderation/ModerationLayout';

import DiscoverHomePage from './pages/portal/discover/pages/DiscoverHomePage';
import DiscoverBoardsPage from './pages/portal/discover/pages/DiscoverBoardsPage';
import DiscoverTagsPage from './pages/portal/discover/pages/DiscoverTagsPage';
import DiscoverHotPage from './pages/portal/discover/pages/DiscoverHotPage';

import PostsCreatePage from './pages/portal/posts/pages/PostsCreatePage';
import PostsDraftsPage from './pages/portal/posts/pages/PostsDraftsPage';
import PostsMinePage from './pages/portal/posts/pages/PostsMinePage';
import PostsBookmarksPage from './pages/portal/posts/pages/PostsBookmarksPage';
import PostDetailPage from './pages/portal/posts/pages/PostDetailPage';

// 删除 notifications 页路由后，该 import 不再需要
import InteractAllPage from './pages/portal/interact/pages/InteractAllPage';
import InteractRepliesPage from './pages/portal/interact/pages/InteractRepliesPage';
import InteractLikesPage from './pages/portal/interact/pages/InteractLikesPage';
import InteractMentionsPage from './pages/portal/interact/pages/InteractMentionsPage';
import InteractReportsPage from './pages/portal/interact/pages/InteractReportsPage';
import InteractSecurityPage from './pages/portal/interact/pages/InteractSecurityPage';

import AssistantChatPage from './pages/portal/assistant/pages/AssistantChatPage';
import AssistantHistoryPage from './pages/portal/assistant/pages/AssistantHistoryPage';
import AssistantCollectionsPage from './pages/portal/assistant/pages/AssistantCollectionsPage';
import AssistantSettingsPage from './pages/portal/assistant/pages/AssistantSettingsPage';

import AccountSecurityPage from './pages/portal/account/pages/AccountSecurityPage';
import AccountPreferencesPage from './pages/portal/account/pages/AccountPreferencesPage';
import AccountConnectionsPage from './pages/portal/account/pages/AccountConnectionsPage';
import UserProfilePage from './pages/portal/users/pages/UserProfilePage';
import SearchLayout from './pages/portal/search/SearchLayout';
import SearchIndexRedirect from './pages/portal/search/SearchIndexRedirect';
import SearchPostsPage from './pages/portal/search/pages/SearchPostsPage';
import ModerationQueuePage from './pages/portal/moderation/pages/ModerationQueuePage';
import ModerationMyLogsPage from './pages/portal/moderation/pages/ModerationMyLogsPage';
import { RequirePermission } from './components/auth/RequirePermission';
import { RequireAccess } from './components/auth/RequireAccess';
import RequireModeratedBoards from './components/auth/RequireModeratedBoards';
import ForbiddenPage from './pages/ForbiddenPage';

// NOTE: admin sections are lazy-loaded to reduce main-thread EvaluateScript on menu switch.
const ContentMgmtPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.ContentMgmtPage })));
const ReviewCenterPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.ReviewCenterPage })));
const SemanticBoostPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.SemanticBoostPage })));
const RetrievalRagPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.RetrievalRagPage })));
const MetricsMonitorPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.MetricsMonitorPage })));
const UsersRBACPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.UsersRBACPage })));
const LlmConfigPage = lazy(() => import('./pages/admin/sections').then(m => ({ default: m.LlmConfigPage })));

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
    if (hasPerm('admin_semantic', 'access')) return <Navigate to="llm-config" replace />;
    if (hasPerm('admin_retrieval', 'access')) return <Navigate to="retrieval" replace />;
    if (hasPerm('admin_metrics', 'access')) return <Navigate to="metrics" replace />;
    if (hasPerm('admin_users', 'access')) return <Navigate to="users" replace />;

    // No section perms at all -> forbidden.
    return <Navigate to="/forbidden" replace />;
}

function AppRoutes() {
    useAuth();
    const [setupRequired, setSetupRequired] = useState<boolean | null>(null);
    const [loading, setLoading] = useState(true);

    // 检查系统是否需要初始设置
    useEffect(() => {
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
                setupRequired ? <AdminSetup /> : <Navigate to="/" replace />
            } />

            {/* 登录页面 - 如果需要初始设置则重定向到初始设置页面 */}
            <Route path="/login" element={
                <LoginRouteElement setupRequired={setupRequired} onBypassSetup={() => setSetupRequired(false)} />
            } />

            {/* 前台门户（普通用户/访客） */}
            <Route path="/portal" element={<CommunityPortalLayout />}>
                <Route path="discover" element={<DiscoverLayout />}>
                    <Route index element={<DiscoverIndexRedirect />} />

                    {/* 发现：按权限控制（portal_discover_home/boards/tags/hot:view） */}
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_home" action="view" />}>
                        <Route path="home" element={<DiscoverHomePage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_boards" action="view" />}>
                        <Route path="boards" element={<DiscoverBoardsPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_tags" action="view" />}>
                        <Route path="tags" element={<DiscoverTagsPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_discover_hot" action="view" />}>
                        <Route path="hot" element={<DiscoverHotPage />} />
                    </Route>

                    {/* discover/search 快捷入口仍然保留，但 search 页面本身也有权限控制 */}
                    <Route path="search" element={<Navigate to="/portal/search/posts" replace />} />
                </Route>

                <Route path="search" element={<SearchLayout />}>
                    <Route index element={<SearchIndexRedirect />} />
                    <Route element={<RequireAccess requiresAuth resource="portal_search_posts" action="view" />}>
                        <Route path="posts" element={<SearchPostsPage />} />
                    </Route>
                </Route>

                <Route path="posts" element={<PostsLayout />}>
                    <Route index element={<PostsIndexRedirect />} />

                    {/* 写作相关：需要登录 + portal_posts:create */}
                    <Route element={<RequireAccess requiresAuth resource="portal_posts" action="create" /> }>
                        <Route path="create" element={<PostsCreatePage />} />
                        <Route path="edit/:postId" element={<PostsCreatePage />} />
                    </Route>

                    {/* 草稿/我的/收藏：需要登录 + 对应 view 权限 */}
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_drafts" action="view" /> }>
                        <Route path="drafts" element={<PostsDraftsPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_mine" action="view" /> }>
                        <Route path="mine" element={<PostsMinePage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_bookmarks" action="view" /> }>
                        <Route path="bookmarks" element={<PostsBookmarksPage />} />
                    </Route>

                    {/* 详情页：公开 */}
                    <Route path="detail/:postId" element={<PostDetailPage />} />
                </Route>

                {/* 互动中心：需要登录 + 对应 view 权限 */}
                <Route path="interact" element={<InteractLayout />}>
                    <Route index element={<InteractIndexRedirect />} />

                    <Route element={<RequireAccess requiresAuth resource="portal_interact_replies" action="view" /> }>
                        <Route path="replies" element={<InteractRepliesPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_interact_likes" action="view" /> }>
                        <Route path="likes" element={<InteractLikesPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_interact_mentions" action="view" /> }>
                        <Route path="mentions" element={<InteractMentionsPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_interact_reports" action="view" /> }>
                        <Route path="reports" element={<InteractReportsPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth /> }>
                        <Route path="all" element={<InteractAllPage />} />
                        <Route path="security" element={<InteractSecurityPage />} />
                    </Route>
                </Route>

                {/* 智能助手：需要登录 + 对应 view 权限 */}
                <Route path="assistant" element={<AssistantLayout />}>
                    <Route index element={<AssistantIndexRedirect />} />

                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_chat" action="view" /> }>
                        <Route path="chat" element={<AssistantChatPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_history" action="view" /> }>
                        <Route path="history" element={<AssistantHistoryPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_collections" action="view" /> }>
                        <Route path="collections" element={<AssistantCollectionsPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_assistant_settings" action="view" /> }>
                        <Route path="settings" element={<AssistantSettingsPage />} />
                    </Route>
                </Route>

                <Route element={<RequireAccess requiresAuth />}>
                    <Route path="moderation" element={<ModerationLayout />}>
                        <Route index element={<ModerationIndexRedirect />} />
                        <Route element={<RequireModeratedBoards />}>
                            <Route path="queue" element={<ModerationQueuePage />} />
                        </Route>
                        <Route path="logs" element={<ModerationMyLogsPage />} />
                    </Route>
                </Route>

                {/* 账号中心：需要登录 + 对应 view 权限 */}
                <Route path="account" element={<AccountLayout />}>
                    <Route index element={<AccountIndexRedirect />} />

                    <Route element={<RequireAccess requiresAuth resource="portal_account_profile" action="view" /> }>
                        <Route path="profile" element={<UserProfilePage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_account_security" action="view" /> }>
                        <Route path="security" element={<AccountSecurityPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_account_preferences" action="view" /> }>
                        <Route path="preferences" element={<AccountPreferencesPage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_account_connections" action="view" /> }>
                        <Route path="connections" element={<AccountConnectionsPage />} />
                    </Route>

                    {/* 账号中心里的“我的/收藏”复用 posts 权限 */}
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_mine" action="view" /> }>
                        <Route path="mine" element={<PostsMinePage />} />
                    </Route>
                    <Route element={<RequireAccess requiresAuth resource="portal_posts_bookmarks" action="view" /> }>
                        <Route path="bookmarks" element={<PostsBookmarksPage />} />
                    </Route>
                </Route>

                <Route element={<RequireAccess requiresAuth />}>
                    <Route path="users/:userId" element={<UserProfilePage />} />
                </Route>

                <Route index element={<Navigate to="discover" replace />} />
            </Route>

            {/* 403 无权限 */}
            <Route path="/forbidden" element={<ForbiddenPage />} />

            {/* 受保护的路由组 */}
            <Route element={<ProtectedRoute />}>
                {/**
                 * 管理员后台入口：
                 * - 生产建议使用细粒度权限 admin_ui:access
                 * - 开发阶段允许 ROLE_ADMIN 直接进入，避免权限矩阵未初始化导致误判 403
                 */}
                <Route element={<RequirePermission resource="admin_ui" action="access" allowRoles={["ADMIN"]} />}>
                    {/* 后台管理（审核员/管理员） */}
                    <Route path="/admin" element={<AdminDashboardLayout />}>
                        <Route path="content" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><ContentMgmtPage /></Suspense>} />
                        <Route path="review" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><ReviewCenterPage /></Suspense>} />
                        <Route path="semantic" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><SemanticBoostPage /></Suspense>} />
                        <Route path="retrieval" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><RetrievalRagPage /></Suspense>} />
                        <Route path="llm-config" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><LlmConfigPage /></Suspense>} />
                        <Route path="metrics" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><MetricsMonitorPage /></Suspense>} />
                        <Route path="users" element={<Suspense fallback={<div className="p-4">正在加载模块…</div>}><UsersRBACPage /></Suspense>} />
                        <Route index element={<AdminIndexRedirect />} />
                    </Route>
                </Route>

            </Route>

            {/* 新闻相关页面 - 公开访问 */}

            <Route path="/register" element={<Register />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />

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
