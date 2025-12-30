import { Navigate, Outlet, useLocation } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';
import PostsCreatePreviewSidebar from './components/PostsCreatePreviewSidebar';

export default function PostsLayout() {
  // 使用“发帖(compose)”分组作为二级导航来源（basePath 仍然是 /portal/posts）
  const section = getPortalSection('compose');
  const items = section.children.map((c) => ({
    id: c.id,
    label: c.label,
    to: `${section.basePath}/${c.path}`,
  }));

  const location = useLocation();
  const showCreatePreview =
    location.pathname.startsWith('/portal/posts/create') || location.pathname.startsWith('/portal/posts/edit');

  // 详情页：/portal/posts/detail/:postId
  const isPostDetail = /^\/portal\/posts\/detail\/[^/]+\/?$/.test(location.pathname);

  return (
    <div className="space-y-4">
      <SubTabsNav title={section.label} items={items} />

      <div
        className={
          isPostDetail
            ? 'grid grid-cols-1 gap-4 items-start w-full'
            : 'grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_275px] xl:grid-cols-[minmax(0,1.25fr)_275px] gap-4 items-start'
        }
      >
        {/* Left: main content */}
        {isPostDetail ? (
          <div className="min-w-0 w-full">
            {/*
              Detail pages should not shrink with content.
              Use a stable content container width and keep it single-column.
            */}
            <div className="min-w-0 w-full mx-auto max-w-6xl">
              <Outlet />
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 p-4 min-w-0">
            <Outlet />
          </div>
        )}

        {/* Right: preview sidebar (only on create/edit) */}
        {!isPostDetail && showCreatePreview ? <PostsCreatePreviewSidebar /> : null}
      </div>
    </div>
  );
}

export function PostsIndexRedirect() {
  const section = getPortalSection('compose');
  const first = section.children[0];
  return <Navigate to={first ? `${section.basePath}/${first.path}` : '/portal/posts/drafts'} replace />;
}
