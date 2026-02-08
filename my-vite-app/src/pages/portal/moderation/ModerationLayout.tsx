import { Navigate, Outlet } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';

export default function ModerationLayout() {
  const section = getPortalSection('moderation');
  const items = section.children?.map((child) => ({ id: child.id, label: child.label, to: `${section.basePath}/${child.path}` })) ?? [];

  return (
    <div className="space-y-4">
      <SubTabsNav title={section.label} items={items} />
      <div>
        <Outlet />
      </div>
    </div>
  );
}

export function ModerationIndexRedirect() {
  const section = getPortalSection('moderation');
  const firstChild = section.children && section.children.length > 0 ? section.children[0] : null;
  const targetPath = firstChild ? `${section.basePath}/${firstChild.path}` : section.basePath;
  return <Navigate to={targetPath} replace />;
}

