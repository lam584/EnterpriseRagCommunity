// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import { useState, useEffect } from 'react';
import LibraryLayout from './pages/NewsSystemLayout';
import Login from './components/login/Login';
import AdminSetup from './components/login/AdminSetup';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { checkInitialSetupStatus } from './services/authService';
import CommunityPortalLayout from './pages/portal/CommunityPortalLayout';
import AdminDashboardLayout from './pages/admin/AdminDashboardLayout';
import { NewsHomePage } from './pages/news/NewsHomePage';
import { NewsDetailPage } from './pages/news/NewsDetailPage';
import Register from './components/login/Register';
import { DiscoverPage, PostsPage, InteractPage, AssistantPage, AccountPage } from './pages/portal/sections';
import { ContentMgmtPage, ReviewCenterPage, SemanticBoostPage, RetrievalRagPage, MetricsMonitorPage, UsersRBACPage } from './pages/admin/sections';

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
                <Route path="discover" element={<DiscoverPage />} />
                <Route path="posts" element={<PostsPage />} />
                <Route path="interact" element={<InteractPage />} />
                <Route path="assistant" element={<AssistantPage />} />
                <Route path="account" element={<AccountPage />} />
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

                {/* 旧的新闻系统布局保留（如需） */}
                <Route path="/library" element={<LibraryLayout />} />
            </Route>

            {/* 新闻相关页面 - 公开访问 */}
            <Route path="/news" element={<NewsHomePage />} />
            <Route path="/news/:newsId" element={<NewsDetailPage />} />
            <Route path="/news/topic/:topicId" element={<NewsHomePage />} />

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
