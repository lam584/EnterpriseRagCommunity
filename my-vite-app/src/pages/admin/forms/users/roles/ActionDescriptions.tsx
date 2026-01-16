import React, {useMemo} from 'react';

import {type PermissionVM, type StandardAction, safeStr, stdActionOf, stdActionLabel} from './permissionUtils';

// 把某个 resource 的权限说明（description）按标准动作分组展示
const ActionDescriptions: React.FC<{ perms: PermissionVM[] }> = ({perms}) => {
    const groups = useMemo(() => {
        const map = new Map<StandardAction, PermissionVM[]>();
        for (const p of perms) {
            const key = stdActionOf(p.action);
            map.set(key, [...(map.get(key) ?? []), p]);
        }

        // 固定顺序，避免渲染抖动
        const order: StandardAction[] = ['READ', 'WRITE', 'UPDATE', 'DELETE', 'EXEC', 'OTHER'];
        return order
            .map(k => ({
                key: k,
                label: stdActionLabel(k),
                items: (map.get(k) ?? []).filter(x => safeStr(x.description)),
            }))
            .filter(g => g.items.length > 0);
    }, [perms]);

    if (!groups.length) return null;

    return (
        <div className="mt-1 text-[14px] text-gray-500 whitespace-normal max-w-[520px]">
            {groups.map(g => {
                const text = g.items
                    .map(p => {
                        const d = safeStr(p.description);
                        // 同一标准动作下可能有多个 raw action（list/query/get...），这里用 raw action 前缀降低歧义
                        return g.key === 'OTHER' ? `${p.action}: ${d}` : `${d}`;
                    })
                    .join('；');

                return (
                    <div key={g.key} className="leading-5">
                        <span className="text-gray-400">{g.label}：</span>
                        <span>{text}</span>
                    </div>
                );
            })}
        </div>
    );
};

export default ActionDescriptions;
