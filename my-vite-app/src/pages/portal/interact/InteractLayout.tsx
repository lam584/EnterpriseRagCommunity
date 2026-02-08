
import { Navigate, Outlet } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';

export default function InteractLayout() {
  const section = getPortalSection('interact');
  const items = section.children.map((c) => ({
    id: c.id,
    label: c.label,
    to: c.to ?? `${section.basePath}/${c.path}`,
  }));

  return (
    <div className="space-y-4">
      <SubTabsNav title={section.label} items={items} />
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <Outlet />
      </div>
    </div>
  );
}

export function InteractIndexRedirect() {
  const section = getPortalSection('interact');
  const first = section.children.find((c) => !c.to) ?? section.children[0];
  return <Navigate to={first.to ?? `${section.basePath}/${first.path}`} replace />;
}

