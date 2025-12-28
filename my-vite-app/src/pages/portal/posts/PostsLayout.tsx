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

  return (
    <div className="space-y-4">
      <SubTabsNav title={section.label} items={items} />

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_525px] xl:grid-cols-[minmax(0,1.25fr)_525px] gap-4 items-start">
        {/* Left: original card container */}
        <div className="bg-white rounded-lg border border-gray-200 p-4 min-w-0">
          <Outlet />
        </div>

        {/* Right: preview sidebar (outside of the left card) */}
        {showCreatePreview ? <PostsCreatePreviewSidebar /> : null}
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
