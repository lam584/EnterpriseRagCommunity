import {useCallback, useMemo, useState} from 'react';

import type {ModerationQueueItem} from '../../../../services/moderationQueueService';

export function useQueueSelection(items: ModerationQueueItem[]) {
    const [selectedMap, setSelectedMap] = useState<Record<number, boolean>>({});

    const selectedIds = useMemo(() => {
        const out: number[] = [];
        for (const [k, v] of Object.entries(selectedMap)) {
            if (!v) continue;
            const n = Number(k);
            if (Number.isFinite(n) && n > 0) out.push(n);
        }
        out.sort((a, b) => a - b);
        return out;
    }, [selectedMap]);

    const allOnPageSelected = useMemo(() => {
        if (!items.length) return false;
        return items.every((it) => !!selectedMap[it.id]);
    }, [items, selectedMap]);

    const toggleAllOnPage = useCallback(
        (checked: boolean) => {
            setSelectedMap((prev) => {
                const next = {...prev};
                for (const it of items) next[it.id] = checked;
                return next;
            });
        },
        [items]
    );

    const toggleOne = useCallback((id: number, checked: boolean) => {
        setSelectedMap((prev) => ({...prev, [id]: checked}));
    }, []);

    const clearSelection = useCallback(() => {
        setSelectedMap({});
    }, []);

    return {
        selectedMap,
        setSelectedMap,
        selectedIds,
        allOnPageSelected,
        toggleAllOnPage,
        toggleOne,
        clearSelection,
    };
}
