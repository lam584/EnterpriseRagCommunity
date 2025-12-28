import { Navigate, Outlet, useLocation } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';
import PostsCreatePreviewSidebar from './components/PostsCreatePreviewSidebar';

export default function PostsLayout() {
  // 复用“发帖(compose)”分组来承载草稿箱子导航，避免依赖 'posts' section
  const section = getPortalSection('compose');
  const items = section.children.map((c) => ({
    id: c.id,
    label: c.label,
    to: `${section.basePath}/${c.path}`,
  }));

  const location = useLocation();
  const showCreatePreview =
    location.pathname.startsWith('/portal/posts/create') || location.pathname.startsWith('/portal/posts/edit');

  // 详情页：让左侧内容区更宽（避免仅在页面内部 max-width 被父容器限制）
  const isPostDetail = /^\/portal\/posts\/\d+\/?$/.test(location.pathname);

  return (
    <div className="space-y-4">
      <SubTabsNav title={section.label} items={items} />

      <div
        className={
          isPostDetail
            ? 'grid grid-cols-1 gap-4 items-start'
            : 'grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_275px] xl:grid-cols-[minmax(0,1.25fr)_275px] gap-4 items-start'
        }
      >
        {/* Left: main content */}
        <div className="bg-white rounded-lg border border-gray-200 p-4 min-w-0">
          <Outlet />
        </div>

        {/* Right: preview sidebar (only on create/edit) */}
        {!isPostDetail && showCreatePreview ? <PostsCreatePreviewSidebar /> : null}
      </div>
    </div>
  );
}

export function PostsIndexRedirect() {
  const section = getPortalSection('compose');
  // 若未来 compose.children 为空，则退化到草稿箱路由
  const first = section.children[0];
  return <Navigate to={first ? `${section.basePath}/${first.path}` : '/portal/posts/drafts'} replace />;
}
