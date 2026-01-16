export type PermissionVM = {
    id: number;
    resource: string;
    action: string;
    description?: string;
};

export type StandardAction = 'READ' | 'WRITE' | 'UPDATE' | 'DELETE' | 'EXEC' | 'OTHER';

export function safeStr(v: unknown): string {
    return String(v ?? '').trim();
}

export function stdActionOf(raw: string): StandardAction {
    const a = safeStr(raw).toLowerCase();
    if (['read', 'view', 'list', 'get', 'query'].includes(a)) return 'READ';
    if (['write', 'create', 'add', 'post'].includes(a)) return 'WRITE';
    if (['update', 'edit', 'put', 'patch'].includes(a)) return 'UPDATE';
    if (['delete', 'remove'].includes(a)) return 'DELETE';
    if (['access', 'action', 'exec', 'run'].includes(a)) return 'EXEC';
    return 'OTHER';
}

export function stdActionLabel(a: StandardAction): string {
    switch (a) {
        case 'READ':
            return '读';
        case 'WRITE':
            return '写';
        case 'UPDATE':
            return '更';
        case 'DELETE':
            return '删';
        case 'EXEC':
            return '执行';
        case 'OTHER':
        default:
            return '更多';
    }
}

