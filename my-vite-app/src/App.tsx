// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import LibraryLayout from './pages/LibraryLayout';
import Login from './components/login/Login';
import { AuthProvider, useAuth } from './contexts/AuthContext';

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

    return (
        <Routes>
            {/* 根路径重定向 - 如果已登录则导航到图书馆，否则导航到登录页 */}
            <Route path="/" element={isAuthenticated ? <Navigate to="/library" replace /> : <Navigate to="/login" replace />} />
            {/* 登录页面 - 如果已登录则重定向到图书馆 */}
            <Route path="/login" element={isAuthenticated ? <Navigate to="/library" replace /> : <Login />} />
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

