import { Navigate, Outlet } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';

export default function AccountLayout() {
  const section = getPortalSection('account');
  const items = section.children.map((c) => ({ id: c.id, label: c.label, to: `${section.basePath}/${c.path}` }));

  return (
    <div className="space-y-4">
      <SubTabsNav title={section.label} items={items} />
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <Outlet />
      </div>
    </div>
  );
}

export function AccountIndexRedirect() {
  const section = getPortalSection('account');
  return <Navigate to={`${section.basePath}/${section.children[0].path}`} replace />;
}

