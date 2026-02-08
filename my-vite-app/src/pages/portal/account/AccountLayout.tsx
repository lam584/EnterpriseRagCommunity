import { Navigate, Outlet } from 'react-router-dom';
import SubTabsNav from '../components/SubTabsNav';
import { getPortalSection } from '../portalMenu';
import { useAuth } from '../../../contexts/AuthContext';
import { getStoredUserId } from '../../../services/portalAuthService';

function toNumId(v: unknown): number | undefined {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

export default function AccountLayout() {
  const { currentUser } = useAuth();
  const section = getPortalSection('account');
  const userId = toNumId((currentUser as unknown as { id?: unknown } | null)?.id) ?? getStoredUserId();
  const profileTo = userId ? `/portal/users/${userId}` : `${section.basePath}/profile`;
  const items = section.children.map((c) => ({
    id: c.id,
    label: c.label,
    to: c.id === 'profile' ? profileTo : (c.to ?? `${section.basePath}/${c.path}`),
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

export function AccountIndexRedirect() {
  const { currentUser } = useAuth();
  const section = getPortalSection('account');
  const userId = toNumId((currentUser as unknown as { id?: unknown } | null)?.id) ?? getStoredUserId();
  const profileTo = userId ? `/portal/users/${userId}` : `${section.basePath}/profile`;
  return <Navigate to={profileTo} replace />;
}

