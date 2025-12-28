import { Navigate } from 'react-router-dom';

export default function SearchIndexRedirect() {
  // 兼容：把一级“搜索”入口统一落到现有实现（原本在 /portal/discover/search）
  return <Navigate to="/portal/search/posts" replace />;
}

