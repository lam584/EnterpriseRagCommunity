// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import { useState, useEffect } from 'react';
import Login from './components/login/Login';
import AdminSetup from './components/login/AdminSetup';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { checkInitialSetupStatus } from './services/authService';
import CommunityPortalLayout from './pages/portal/CommunityPortalLayout';
import AdminDashboardLayout from './pages/admin/AdminDashboardLayout';
import Register from './components/login/Register';
import DiscoverLayout, { DiscoverIndexRedirect } from './pages/portal/discover/DiscoverLayout';
import PostsLayout, { PostsIndexRedirect } from './pages/portal/posts/PostsLayout';
import InteractLayout, { InteractIndexRedirect } from './pages/portal/interact/InteractLayout';
import AssistantLayout, { AssistantIndexRedirect } from './pages/portal/assistant/AssistantLayout';
import AccountLayout, { AccountIndexRedirect } from './pages/portal/account/AccountLayout';

import DiscoverHomePage from './pages/portal/discover/pages/DiscoverHomePage';
import DiscoverBoardsPage from './pages/portal/discover/pages/DiscoverBoardsPage';
import DiscoverTagsPage from './pages/portal/discover/pages/DiscoverTagsPage';
import DiscoverHotPage from './pages/portal/discover/pages/DiscoverHotPage';

import PostsFeedPage from './pages/portal/posts/pages/PostsFeedPage';
import PostsCreatePage from './pages/portal/posts/pages/PostsCreatePage';
import PostsDraftsPage from './pages/portal/posts/pages/PostsDraftsPage';
import PostsMinePage from './pages/portal/posts/pages/PostsMinePage';
import PostsBookmarksPage from './pages/portal/posts/pages/PostsBookmarksPage';
import PostDetailPage from './pages/portal/posts/pages/PostDetailPage';

// 删除 notifications 页路由后，该 import 不再需要
import InteractRepliesPage from './pages/portal/interact/pages/InteractRepliesPage';
import InteractLikesPage from './pages/portal/interact/pages/InteractLikesPage';
import InteractMentionsPage from './pages/portal/interact/pages/InteractMentionsPage';
import InteractReportsPage from './pages/portal/interact/pages/InteractReportsPage';

import AssistantChatPage from './pages/portal/assistant/pages/AssistantChatPage';
import AssistantHistoryPage from './pages/portal/assistant/pages/AssistantHistoryPage';
import AssistantCollectionsPage from './pages/portal/assistant/pages/AssistantCollectionsPage';
import AssistantSettingsPage from './pages/portal/assistant/pages/AssistantSettingsPage';

import AccountProfilePage from './pages/portal/account/pages/AccountProfilePage';
import AccountSecurityPage from './pages/portal/account/pages/AccountSecurityPage';
import AccountPreferencesPage from './pages/portal/account/pages/AccountPreferencesPage';
import AccountConnectionsPage from './pages/portal/account/pages/AccountConnectionsPage';
import SearchLayout from './pages/portal/search/SearchLayout';
import SearchIndexRedirect from './pages/portal/search/SearchIndexRedirect';
import SearchPostsPage from './pages/portal/search/pages/SearchPostsPage';
import { ContentMgmtPage, ReviewCenterPage, SemanticBoostPage, RetrievalRagPage, MetricsMonitorPage, UsersRBACPage } from './pages/admin/sections.tsx';

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

function AppRoutes() {
    const { isAuthenticated } = useAuth();
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
                    : <Navigate to="/portal/discover" replace />
            } />

            {/* 初始管理员设置页面 */}
            <Route path="/admin-setup" element={
                setupRequired ? <AdminSetup /> : <Navigate to="/" replace />
            } />

            {/* 登录页面 - 如果需要初始设置则重定向到初始设置页面 */}
            <Route path="/login" element={
                setupRequired
                    ? <Navigate to="/admin-setup" replace />
                    : (isAuthenticated ? <Navigate to="/portal/discover" replace /> : <Login />)
            } />

            {/* 前台门户（普通用户/访客） - 公开访问 */}
            <Route path="/portal" element={<CommunityPortalLayout />}>
                <Route path="discover" element={<DiscoverLayout />}>
                    <Route index element={<DiscoverIndexRedirect />} />
                    <Route path="home" element={<DiscoverHomePage />} />
                    <Route path="boards" element={<DiscoverBoardsPage />} />
                    <Route path="tags" element={<DiscoverTagsPage />} />
                    <Route path="search" element={<Navigate to="/portal/search/posts" replace />} />
                    <Route path="hot" element={<DiscoverHotPage />} />
                </Route>

                <Route path="search" element={<SearchLayout />}>
                    <Route index element={<SearchIndexRedirect />} />
                    <Route path="posts" element={<SearchPostsPage />} />
                </Route>

                <Route path="posts" element={<PostsLayout />}>
                    <Route index element={<PostsIndexRedirect />} />
                    <Route path="feed" element={<PostsFeedPage />} />
                    <Route path="create" element={<PostsCreatePage />} />
                    <Route path="edit/:postId" element={<PostsCreatePage />} />
                    <Route path="detail/:postId" element={<PostDetailPage />} />
                    <Route path="drafts" element={<PostsDraftsPage />} />
                    <Route path="mine" element={<PostsMinePage />} />
                    <Route path="bookmarks" element={<PostsBookmarksPage />} />
                </Route>

                <Route path="interact" element={<InteractLayout />}>
                    <Route index element={<InteractIndexRedirect />} />
                    {/* 二级菜单“通知”删除：原 /portal/interact/notifications 不再暴露为独立页 */}
                    <Route path="replies" element={<InteractRepliesPage />} />
                    <Route path="likes" element={<InteractLikesPage />} />
                    <Route path="mentions" element={<InteractMentionsPage />} />
                    <Route path="reports" element={<InteractReportsPage />} />
                </Route>

                <Route path="assistant" element={<AssistantLayout />}>
                    <Route index element={<AssistantIndexRedirect />} />
                    <Route path="chat" element={<AssistantChatPage />} />
                    <Route path="history" element={<AssistantHistoryPage />} />
                    <Route path="collections" element={<AssistantCollectionsPage />} />
                    <Route path="settings" element={<AssistantSettingsPage />} />
                </Route>

                <Route path="account" element={<AccountLayout />}>
                    <Route index element={<AccountIndexRedirect />} />
                    <Route path="profile" element={<AccountProfilePage />} />
                    <Route path="security" element={<AccountSecurityPage />} />
                    <Route path="preferences" element={<AccountPreferencesPage />} />
                    <Route path="connections" element={<AccountConnectionsPage />} />
                    <Route path="mine" element={<PostsMinePage />} />
                    <Route path="bookmarks" element={<PostsBookmarksPage />} />
                </Route>

                <Route index element={<Navigate to="discover" replace />} />
            </Route>

            {/* 受保护的路由组 */}
            <Route element={<ProtectedRoute />}>
                {/* 后台管理（审核员/管理员） */}
                <Route path="/admin" element={<AdminDashboardLayout />}>
                    <Route path="content" element={<ContentMgmtPage />} />
                    <Route path="review" element={<ReviewCenterPage />} />
                    <Route path="semantic" element={<SemanticBoostPage />} />
                    <Route path="retrieval" element={<RetrievalRagPage />} />
                    <Route path="metrics" element={<MetricsMonitorPage />} />
                    <Route path="users" element={<UsersRBACPage />} />
                    <Route index element={<Navigate to="content" replace />} />
                </Route>

            </Route>

            {/* 新闻相关页面 - 公开访问 */}

            <Route path="/register" element={<Register />} />

            {/* 兜底路由 */}
            <Route path="*" element={<Navigate to="/portal/discover" replace />} />
        </Routes>
    );
}

function App() {
    return (
        <AuthProvider>
            <BrowserRouter>
                <AppRoutes />
            </BrowserRouter>
        </AuthProvider>
    )
}

export default App
