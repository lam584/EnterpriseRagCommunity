// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import { useState, useEffect } from 'react';
import LibraryLayout from './pages/NewsSystemLayout';
import Login from './components/login/Login';
import AdminSetup from './components/login/AdminSetup';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { checkInitialSetupStatus } from './services/authService';

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
                : (isAuthenticated ? <Navigate to="/library" replace /> : <Navigate to="/login" replace />)
            } />

            {/* 初始管理员设置页面 */}
            <Route path="/admin-setup" element={
                setupRequired ? <AdminSetup /> : <Navigate to="/" replace />
            } />

            {/* 登录页面 - 如果需要初始设置则重定向到初始设置页面 */}
            <Route path="/login" element={
                setupRequired
                ? <Navigate to="/admin-setup" replace />
                : (isAuthenticated ? <Navigate to="/library" replace /> : <Login />)
            } />

            {/* 受保护的路由组 */}
            <Route element={<ProtectedRoute />}>
                <Route path="/library" element={<LibraryLayout />} />
                {/* 这里可以添加其他需要保护的路由 */}
            </Route>
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
