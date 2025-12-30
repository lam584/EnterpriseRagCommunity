import { Outlet } from 'react-router-dom';

export default function SearchLayout() {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">搜索</h2>
        <p className="text-sm text-gray-600">支持从首页快速搜索，并在这里查看结果。</p>
      </div>
      <Outlet />
    </div>
  );
}
