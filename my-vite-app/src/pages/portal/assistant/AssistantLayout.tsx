
import { Navigate, Outlet } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';

export default function AssistantLayout() {
  const section = getPortalSection('assistant');
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

export function AssistantIndexRedirect() {
  const section = getPortalSection('assistant');
  return <Navigate to={`${section.basePath}/${section.children[0].path}`} replace />;
}

