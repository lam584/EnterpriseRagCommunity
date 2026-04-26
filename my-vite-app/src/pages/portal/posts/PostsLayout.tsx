import { useMemo } from 'react';
import { Navigate, Outlet, useLocation, useOutletContext } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import type { PortalOutletContext } from '../CommunityPortalLayout';
import { getPortalSection, type PortalSection } from '../portalMenu';
import PostsCreatePreviewSidebar from './components/PostsCreatePreviewSidebar';

export type PostsOutletContext = PortalOutletContext & {
  isComposeRoute: boolean;
  isPostDetail: boolean;
  previewPaneEnabled: boolean;
};

type LocationState = {
  from?: {
    pathname?: string;
  };
};

function resolvePostsHeaderSection(pathname: string, state: LocationState | null): PortalSection['id'] {
  if (!/^\/portal\/posts\/detail\/[^/]+\/?$/.test(pathname)) return 'compose';

  const fromPathname = state?.from?.pathname;
  if (typeof fromPathname !== 'string') return 'compose';
  if (fromPathname.startsWith('/portal/discover')) return 'discover';
  return 'compose';
}

export default function PostsLayout() {
  const { composePreviewOpen, setComposePreviewOpen } = useOutletContext<PortalOutletContext>();

  const location = useLocation();
  const isComposeRoute =
    location.pathname.startsWith('/portal/posts/create') || location.pathname.startsWith('/portal/posts/edit');

  // 详情页：/portal/posts/detail/:postId
  const isPostDetail = /^\/portal\/posts\/detail\/[^/]+\/?$/.test(location.pathname);
  const sectionId = resolvePostsHeaderSection(location.pathname, (location.state ?? null) as LocationState | null);
  const section = getPortalSection(sectionId);
  const items = section.children.map((c) => ({
    id: c.id,
    label: c.label,
    to: `${section.basePath}/${c.path}`,
  }));

  const previewPaneEnabled = !isPostDetail && isComposeRoute && composePreviewOpen;

  const composeGridClassName = useMemo(() => {
    if (isPostDetail) return 'grid grid-cols-1 gap-4 items-start w-full min-w-0';
    if (!previewPaneEnabled) return 'grid grid-cols-1 gap-4 items-start w-full min-w-0';
    return 'grid grid-cols-1 lg:grid-cols-2 gap-4 items-stretch w-full min-w-0';
  }, [isPostDetail, previewPaneEnabled]);

  const outletContext = useMemo(
    () =>
      ({
        composePreviewOpen,
        setComposePreviewOpen,
        isComposeRoute,
        isPostDetail,
        previewPaneEnabled,
      }) satisfies PostsOutletContext,
    [
      composePreviewOpen,
      isComposeRoute,
      isPostDetail,
      previewPaneEnabled,
      setComposePreviewOpen,
    ],
  );

  return (
    <div className="space-y-4">
      <SubTabsNav
        title={section.label}
        items={items}
      />

      <div className="w-full min-w-0">
        <div className={composeGridClassName}>
          {/* Left: main content */}
          <div className="bg-white rounded-lg border border-gray-200 p-4 min-w-0 min-h-0 h-full">
            <Outlet context={outletContext} />
          </div>

          {/* Right: preview sidebar (only on create/edit) */}
          {previewPaneEnabled ? (
            <div className="hidden lg:flex flex-col bg-white rounded-lg border border-gray-200 p-4 min-w-0 min-h-0 sticky top-4 h-[calc(100vh-2rem)]">
              <PostsCreatePreviewSidebar />
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export function PostsIndexRedirect() {
  const section = getPortalSection('compose');
  const first = section.children[0];
  return <Navigate to={first ? `${section.basePath}/${first.path}` : '/portal/posts/drafts'} replace />;
}
