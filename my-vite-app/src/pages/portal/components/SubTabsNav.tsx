import { Fragment, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

export type SubTabsNavItem = {
  id: string;
  label: string;
  to: string;
};

export type SubTabsNavProps = {
  title?: string;
  items: SubTabsNavItem[];
  className?: string;
  afterItemId?: string;
  afterItem?: ReactNode;
};

export default function SubTabsNav({ title, items, className, afterItemId, afterItem }: SubTabsNavProps) {
  if (!items || items.length === 0) return null;

  return (
    <div className={className}>
      {title ? <h2 className="text-lg font-semibold text-gray-900">{title}</h2> : null}
      <div className="mt-2 flex flex-wrap gap-2">
        {items.map((it) => (
          <Fragment key={it.id}>
            <NavLink
              to={it.to}
              className={({ isActive }) =>
                [
                  'inline-flex items-center rounded-full border px-3 py-1 text-sm transition-colors',
                  isActive
                    ? 'border-blue-600 bg-blue-50 text-blue-700'
                    : 'border-gray-200 bg-white text-gray-700 hover:bg-gray-50',
                ].join(' ')
              }
            >
              {it.label}
            </NavLink>
            {afterItemId === it.id ? afterItem : null}
          </Fragment>
        ))}
      </div>
    </div>
  );
}

