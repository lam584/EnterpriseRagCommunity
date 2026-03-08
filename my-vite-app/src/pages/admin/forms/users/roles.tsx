import React, {useEffect, useMemo, useState} from 'react';
import {Button} from '../../../../components/ui/button';
import {Input} from '../../../../components/ui/input';
import {Checkbox} from '../../../../components/ui/checkbox';
import {Label} from '../../../../components/ui/label';
import {PermissionsUpdateDTO, queryPermissions} from '../../../../services/permissionsService';
import {
    createRoleWithMatrix,
    listRoleIds,
    listRolePermissionsByRole,
    listRoleSummaries,
    replaceRolePermissions,
    type RolePermissionUpsertDTO,
    type RolePermissionViewDTO,
    type RoleSummaryDTO,
} from '../../../../services/rolePermissionsService';
import {FaEdit, FaPlus, FaSearch, FaSync, FaTrash} from 'react-icons/fa';
import {useAccess} from '../../../../contexts/AccessContext';
import Modal from './roles/Modal';
import RolePermissionEditor from './roles/RolePermissionEditor';
import ActionDescriptions from './roles/ActionDescriptions';
import {type PermissionVM, safeStr, type StandardAction, stdActionLabel, stdActionOf} from './roles/permissionUtils';
import { useAdminStepUp } from '../../../../components/admin/useAdminStepUp';
import { isAdminStepUpRequired } from '../../../../services/apiError';
import {
    getRegistrationSettings,
    updateRegistrationSettings,
} from '../../../../services/adminSettingsService';

type RoleRow = { roleId: number; roleName?: string };
type ResourceGroupVM = {
    resourceKey: string;
    perms: PermissionVM[];
};

type SubGroupVM = {
    key: string;
    label: string;
    resources: ResourceGroupVM[];
};

type L1GroupVM = {
    key: string;
    label: string;
    subGroups: SubGroupVM[];
};

function splitResource(resource: string): { l1: string; l2: string; resourceKey: string } {
    const key = safeStr(resource) || 'unknown';
    const parts = key.split('_').filter(Boolean);
    const l1 = parts[0] || 'misc';
    const l2 = parts[1] || 'misc';
    return {l1, l2, resourceKey: key};
}

function l1Label(key: string): string {
    switch (key) {
        case 'admin':
            return '后台管理';
        case 'portal':
            return '前台功能';
        default:
            return key;
    }
}

function actionBadgeHint(perms: PermissionVM[]): string {
    return perms
        .map(p => `${p.action}${p.description ? ` - ${p.description}` : ''}`)
        .slice(0, 6)
        .join('\n');
}

type TriState = 'ALLOW' | 'DENY' | 'UNSET';

function triStateClass(state: TriState): string {
    if (state === 'ALLOW') return 'bg-green-100 text-green-700 border-green-200';
    if (state === 'DENY') return 'bg-red-100 text-red-700 border-red-200';
    return 'bg-gray-100 text-gray-700 border-gray-200';
}

function triStateLabel(state: TriState): string {
    switch (state) {
        case 'ALLOW':
            return '允许';
        case 'DENY':
            return '拒绝';
        case 'UNSET':
        default:
            return '未设置';
    }
}

function normalizeRoleId(value: unknown): number | undefined {
    const n = typeof value === 'number' ? value : Number(String(value ?? ''));
    if (!Number.isFinite(n)) return undefined;
    if (n <= 0) return undefined;
    return Math.trunc(n);
}

function toTriState(existing: RolePermissionViewDTO | undefined): TriState {
    if (!existing) return 'UNSET';
    return existing.allow ? 'ALLOW' : 'DENY';
}

function toAllow(payload: TriState): boolean | undefined {
    if (payload === 'ALLOW') return true;
    if (payload === 'DENY') return false;
    return undefined;
}

function initDraftFromPermissions(perms: PermissionsUpdateDTO[]): Record<number, TriState> {
    const nextDraft: Record<number, TriState> = {};
    for (const p of perms ?? []) nextDraft[p.id] = 'UNSET';
    return nextDraft;
}

function initDraftFromRolePerms(perms: PermissionsUpdateDTO[], rolePerms: RolePermissionViewDTO[]): Record<number, TriState> {
    const rpMap = new Map<number, RolePermissionViewDTO>();
    for (const rp of rolePerms ?? []) rpMap.set(rp.permissionId, rp);
    const nextDraft: Record<number, TriState> = {};
    for (const p of perms ?? []) nextDraft[p.id] = toTriState(rpMap.get(p.id));
    return nextDraft;
}

function buildUpsertPayload(args: {
    mode: 'create';
    roleNameInput: string;
    permissions: PermissionsUpdateDTO[];
    draft: Record<number, TriState>;
    rolePermMap: Map<number, RolePermissionViewDTO>;
}): { payload: Omit<RolePermissionUpsertDTO, 'roleId'>[]; trimmedName: string };
function buildUpsertPayload(args: {
    mode: 'edit';
    roleId: number;
    roleNameInput: string;
    permissions: PermissionsUpdateDTO[];
    draft: Record<number, TriState>;
    rolePermMap: Map<number, RolePermissionViewDTO>;
}): { payload: RolePermissionUpsertDTO[]; trimmedName: string };
function buildUpsertPayload(args: {
    mode: 'create' | 'edit';
    roleId?: number;
    roleNameInput: string;
    permissions: PermissionsUpdateDTO[];
    draft: Record<number, TriState>;
    rolePermMap: Map<number, RolePermissionViewDTO>;
}): { payload: RolePermissionUpsertDTO[] | Omit<RolePermissionUpsertDTO, 'roleId'>[]; trimmedName: string } {
    const trimmedName = args.roleNameInput.trim();
    const payload: (RolePermissionUpsertDTO | Omit<RolePermissionUpsertDTO, 'roleId'>)[] = [];
    for (const p of args.permissions ?? []) {
        const state = args.mode === 'edit'
            ? (args.draft[p.id] ?? toTriState(args.rolePermMap.get(p.id)))
            : (args.draft[p.id] ?? 'UNSET');
        const allow = toAllow(state);
        if (allow === undefined) continue;

        if (args.mode === 'edit') {
            payload.push({
                roleId: args.roleId!,
                roleName: trimmedName ? trimmedName : undefined,
                permissionId: p.id,
                allow,
            } satisfies RolePermissionUpsertDTO);
        } else {
            payload.push({
                roleName: trimmedName,
                permissionId: p.id,
                allow,
            } satisfies Omit<RolePermissionUpsertDTO, 'roleId'>);
        }
    }
    if (args.mode === 'edit') {
        return {
            payload: payload as RolePermissionUpsertDTO[],
            trimmedName,
        };
    }

    return {
        payload: payload as Omit<RolePermissionUpsertDTO, 'roleId'>[],
        trimmedName,
    };
}

const RolesManagement: React.FC = () => {
  const [loading, setLoading] = useState(false);
    const [matrixLoading, setMatrixLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const access = useAccess();
    const { ensureAdminStepUp, adminStepUpModal } = useAdminStepUp();
    const [feedback, setFeedback] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);
    const [nameKeyword, setNameKeyword] = useState('');
    const [roles, setRoles] = useState<RoleRow[]>([]);
    const [isRoleModalOpen, setRoleModalOpen] = useState(false);
    const [editingRoleId, setEditingRoleId] = useState<number | null>(null);
    const [roleNameInput, setRoleNameInput] = useState('');
    const [creating, setCreating] = useState(false);
    const [permissions, setPermissions] = useState<PermissionsUpdateDTO[]>([]);
    const [rolePermissions, setRolePermissions] = useState<RolePermissionViewDTO[]>([]);
    const [draft, setDraft] = useState<Record<number, TriState>>({});
    const [permKeyword, setPermKeyword] = useState('');
    const [collapsedL1, setCollapsedL1] = useState<Record<string, boolean>>({});

    const [registrationLoading, setRegistrationLoading] = useState(false);
    const [registrationSaving, setRegistrationSaving] = useState(false);
    const [defaultRegisterRoleId, setDefaultRegisterRoleId] = useState<number>(1);
    const [registrationEnabled, setRegistrationEnabled] = useState<boolean>(true);
    const [roleSummaries, setRoleSummaries] = useState<RoleSummaryDTO[]>([]);
    const rolePermMap = useMemo(() => {
        const map = new Map<number, RolePermissionViewDTO>();
    for (const rp of rolePermissions) {
        map.set(rp.permissionId, rp);
    }
        return map;
  }, [rolePermissions]);

    const permissionVMs = useMemo((): PermissionVM[] => {
        return (permissions ?? [])
            .map(p => ({
                id: p.id,
                resource: safeStr(p.resource) || 'unknown',
                action: safeStr(p.action) || 'unknown',
                description: p.description,
            }))
            .filter(p => p.id && p.resource);
    }, [permissions]);

    const filteredRoles = useMemo(() => {
        const keyword = nameKeyword.trim().toLowerCase();
        if (!keyword) return roles;
        return roles.filter(r => (safeStr(r.roleName).toLowerCase().includes(keyword)));
    }, [roles, nameKeyword]);

    const groupedTree = useMemo((): L1GroupVM[] => {
        const byL1 = new Map<string, Map<string, Map<string, PermissionVM[]>>>();
        for (const p of permissionVMs) {
            const {l1, l2, resourceKey} = splitResource(p.resource);
            if (!byL1.has(l1)) byL1.set(l1, new Map());
            const byL2 = byL1.get(l1)!;
            if (!byL2.has(l2)) byL2.set(l2, new Map());
            const byRes = byL2.get(l2)!;
            if (!byRes.has(resourceKey)) byRes.set(resourceKey, []);
            byRes.get(resourceKey)!.push(p);
        }

        const l1Keys = Array.from(byL1.keys()).sort((a: string, b: string) => a.localeCompare(b));
        return l1Keys.map(l1 => {
            const byL2 = byL1.get(l1)!;
            const l2Keys = Array.from(byL2.keys()).sort((a: string, b: string) => a.localeCompare(b));
            const subGroups: SubGroupVM[] = l2Keys.map(l2 => {
                const byRes = byL2.get(l2)!;
                const resKeys = Array.from(byRes.keys()).sort((a: string, b: string) => a.localeCompare(b));
                const resources: ResourceGroupVM[] = resKeys.map(resourceKey => {
                    const perms = (byRes.get(resourceKey) ?? []).slice().sort((a: PermissionVM, b: PermissionVM) => a.action.localeCompare(b.action));
                    return {resourceKey, perms};
                });
                return {key: l2, label: l2, resources};
            });
            return {key: l1, label: l1Label(l1), subGroups};
        });
    }, [permissionVMs]);

    const filteredTree = useMemo((): L1GroupVM[] => {
        const kw = permKeyword.trim().toLowerCase();
        if (!kw) return groupedTree;

        const matchesPerm = (p: PermissionVM) => {
            const r = p.resource.toLowerCase();
            const a = p.action.toLowerCase();
            const d = (p.description ?? '').toLowerCase();
            return r.includes(kw) || a.includes(kw) || d.includes(kw);
        };

        const next: L1GroupVM[] = [];
        for (const g1 of groupedTree) {
            const nextSubs: SubGroupVM[] = [];
            for (const g2 of g1.subGroups) {
                const nextRes: ResourceGroupVM[] = [];
                for (const r of g2.resources) {
                    if (r.perms.some(matchesPerm) || r.resourceKey.toLowerCase().includes(kw)) {
                        nextRes.push(r);
                    }
                }
                if (nextRes.length) nextSubs.push({...g2, resources: nextRes});
            }
            if (nextSubs.length) next.push({...g1, subGroups: nextSubs});
        }
        return next;
    }, [groupedTree, permKeyword]);
    useEffect(() => {
        const kw = permKeyword.trim();
        if (!kw) return;
        setCollapsedL1(prev => {
            const copy = {...prev};
            for (const g1 of filteredTree) copy[g1.key] = false;
            return copy;
        });
    }, [permKeyword, filteredTree]);

    const visiblePermissionIds = useMemo(() => {
        const ids: number[] = [];
        for (const g1 of filteredTree) {
            for (const g2 of g1.subGroups) {
                for (const r of g2.resources) {
                    for (const p of r.perms) ids.push(p.id);
                }
            }
        }
        return ids;
    }, [filteredTree]);

    const cycleTriState = (current: TriState): TriState => {
        // UNSET -> ALLOW -> DENY -> UNSET
        if (current === 'UNSET') return 'ALLOW';
        if (current === 'ALLOW') return 'DENY';
        return 'UNSET';
    };

    const getState = (permissionId: number): TriState => {
        return draft[permissionId] ?? toTriState(rolePermMap.get(permissionId));
    };

    const bulkSet = (permissionIds: number[], next: TriState) => {
        if (!permissionIds.length) return;
        setDraft(prev => {
            const copy = {...prev};
            for (const id of permissionIds) copy[id] = next;
            return copy;
        });
    };

    const setAllVisible = (next: TriState) => bulkSet(visiblePermissionIds, next);


    const groupPermissionIds = (l1Key: string, l2Key?: string): number[] => {
        const ids: number[] = [];
        for (const p of permissionVMs) {
            const {l1, l2} = splitResource(p.resource);
            if (l1 !== l1Key) continue;
            if (l2Key && l2 !== l2Key) continue;
            ids.push(p.id);
        }
        return ids;
  };

    const actionBucket = (perms: PermissionVM[]) => {
        const buckets: Record<StandardAction, PermissionVM[]> = {
            READ: [],
            WRITE: [],
            UPDATE: [],
            DELETE: [],
            EXEC: [],
            OTHER: [],
    };
        for (const p of perms) buckets[stdActionOf(p.action)].push(p);
        return buckets;
  };

    const renderActionCell = (list: PermissionVM[], title: string) => {
        if (!list.length)
            return (
                <div className="text-xs text-gray-300 text-center" title={title}>
                    -
                </div>
            );
        const states = list.map(p => getState(p.id));
        const allSame = states.every(s => s === states[0]);
        const agg: TriState | 'MIXED' = allSame ? states[0] : 'MIXED';

        const baseClass = agg === 'MIXED' ? 'bg-yellow-50 text-yellow-800 border-yellow-200' : triStateClass(agg);
        const label = agg === 'MIXED' ? '部分' : triStateLabel(agg);

        return (
            <button
                type="button"
                className={`w-full border rounded px-2 py-1 text-xs ${baseClass} hover:opacity-90`}
                title={`${title}\n\n${actionBadgeHint(list)}`}
                onClick={() => {
                    // 点击时：若 mixed 则先统一设为 ALLOW；否则按循环切换
                    const next = agg === 'MIXED' ? 'ALLOW' : cycleTriState(agg);
                    bulkSet(
                        list.map(p => p.id),
                        next,
                    );
                }}
            >
                {label}
            </button>
        );
    };

    const refreshRoleIds = async () => {
    setLoading(true);
    try {
        const ids = await listRoleIds();
        const normalized = (ids ?? []).map(normalizeRoleId).filter((v): v is number => typeof v === 'number');
        const deduped = Array.from(new Set(normalized)).sort((a, b) => a - b);

        // 额外取每个 roleId 的 roleName（从该 roleId 下任意一条 role_permissions 记录读取）
        const rows = await Promise.all(
            deduped.map(async roleId => {
                try {
                    const rps = await listRolePermissionsByRole(roleId);
                    const roleName = rps?.find(x => x.roleName && String(x.roleName).trim())?.roleName;
                    return {roleId, roleName} as RoleRow;
                } catch {
                    return {roleId} as RoleRow;
                }
            }),
        );

        setRoles(rows);
    } catch (e) {
      console.error(e);
        alert('加载 roleId 列表失败');
    } finally {
      setLoading(false);
    }
  };

    const refreshPermissions = async () => {
        const permsPage = await queryPermissions({pageNum: 1, pageSize: 1000});
        setPermissions(permsPage.content ?? []);
    };

    const refreshRegistration = async () => {
        setRegistrationLoading(true);
        try {
            const [settings, summaries] = await Promise.all([
                getRegistrationSettings(),
                listRoleSummaries(),
            ]);
            setDefaultRegisterRoleId(Number(settings.defaultRegisterRoleId) || 1);
            setRegistrationEnabled(settings.registrationEnabled !== false);
            setRoleSummaries(summaries ?? []);
        } catch (e) {
            console.error(e);
        } finally {
            setRegistrationLoading(false);
        }
    };

    const saveRegistration = async () => {
        setRegistrationSaving(true);
        try {
            const roleId = Number(defaultRegisterRoleId) || 1;
            await updateRegistrationSettings({defaultRegisterRoleId: roleId, registrationEnabled});
            setFeedback({type: 'success', message: '注册配置已保存'});
        } catch (e) {
            console.error(e);
            setFeedback({type: 'error', message: '保存注册配置失败'});
        } finally {
            setRegistrationSaving(false);
        }
    };

  useEffect(() => {
      (async () => {
          setLoading(true);
          try {
              await Promise.all([refreshRoleIds(), refreshPermissions(), refreshRegistration()]);
          } finally {
              setLoading(false);
      }
      })();
  }, []);

    const openCreateRole = async () => {
        setEditingRoleId(null);
        setRoleNameInput('');
        setRolePermissions([]);

        // 预加载权限列表，并初始化矩阵
        setMatrixLoading(true);
        setRoleModalOpen(true);
        try {
            const permsPage = await queryPermissions({pageNum: 1, pageSize: 1000});
            const perms = permsPage.content ?? [];
            setPermissions(perms);

            setDraft(initDraftFromPermissions(perms));
        } catch (e) {
            console.error(e);
            alert('加载权限列表失败');
        } finally {
            setMatrixLoading(false);
        }
    };

    const openEditRole = async (roleId: number) => {
        setEditingRoleId(roleId);
        setRoleModalOpen(true);

        setMatrixLoading(true);
        try {
            const [permsPage, rolePerms] = await Promise.all([
                queryPermissions({pageNum: 1, pageSize: 1000}),
                listRolePermissionsByRole(roleId),
            ]);
            const perms = permsPage.content ?? [];
            setPermissions(perms);
            setRolePermissions(rolePerms ?? []);

            const existingName = rolePerms?.find(x => x.roleName && String(x.roleName).trim())?.roleName;
            setRoleNameInput(existingName ? String(existingName) : '');

            setDraft(initDraftFromRolePerms(perms, rolePerms ?? []));
        } catch (e) {
            console.error(e);
            alert('加载角色权限矩阵失败');
        } finally {
            setMatrixLoading(false);
        }
    };

    const handleCreateRoleWithMatrix = async () => {
        const {payload, trimmedName} = buildUpsertPayload({
            mode: 'create',
            roleNameInput,
            permissions,
            draft,
            rolePermMap,
        });

        if (!trimmedName) {
            setFeedback({type: 'error', message: '请输入角色名'});
            return;
        }

        if (payload.length === 0) {
            setFeedback({type: 'error', message: '请至少配置一个权限为 允许 或 拒绝'});
            return;
        }

        setCreating(true);
        setFeedback(null);
        try {
            const saved = await createRoleWithMatrix(payload);
            const newRoleId = saved?.[0]?.roleId;
            if (typeof newRoleId !== 'number') {
                setFeedback({type: 'error', message: '创建失败：后端未返回 roleId'});
                return;
            }

            // 刷新列表（会带回 roleName）
            await refreshRoleIds();

            // 自动切到“编辑该角色”的状态
            setEditingRoleId(newRoleId);
            setRolePermissions(saved ?? []);

            // 将 draft 与后端返回对齐
            setDraft(initDraftFromRolePerms(permissions, saved ?? []));

            setFeedback({type: 'success', message: `创建成功，roleId=${newRoleId}`});
        } catch (e) {
            if (isAdminStepUpRequired(e)) {
                const r = await ensureAdminStepUp();
                if (r.ensured) {
                    const retry = await createRoleWithMatrix(payload);
                    const newRoleId = retry?.[0]?.roleId;
                    if (typeof newRoleId !== 'number') {
                        setFeedback({type: 'error', message: '创建失败：后端未返回 roleId'});
                        return;
                    }
                    await refreshRoleIds();
                    setEditingRoleId(newRoleId);
                    setRolePermissions(retry ?? []);
                    setDraft(initDraftFromRolePerms(permissions, retry ?? []));
                    setFeedback({type: 'success', message: `创建成功，roleId=${newRoleId}`});
                    return;
                }
            }
            console.error(e);
            setFeedback({type: 'error', message: '创建角色失败'});
        } finally {
            setCreating(false);
        }
    };


    const handleSave = async () => {
        if (!editingRoleId) return;

        const {payload} = buildUpsertPayload({
            mode: 'edit',
            roleId: editingRoleId,
            roleNameInput,
            permissions,
            draft,
            rolePermMap,
        });

        setSaving(true);
        setFeedback(null);
        try {
            await replaceRolePermissions(editingRoleId, payload);

            const latest = await listRolePermissionsByRole(editingRoleId);
            setRolePermissions(latest ?? []);

            await refreshRoleIds();

            setFeedback({type: 'success', message: '保存成功'});
        } catch (e) {
            if (isAdminStepUpRequired(e)) {
                const r = await ensureAdminStepUp();
                if (r.ensured) {
                    await replaceRolePermissions(editingRoleId, payload);
                    const latest = await listRolePermissionsByRole(editingRoleId);
                    setRolePermissions(latest ?? []);
                    await refreshRoleIds();
                    setFeedback({type: 'success', message: '保存成功'});
                    return;
                }
            }
            console.error(e);
            setFeedback({type: 'error', message: '保存失败'});
        } finally {
            setSaving(false);
        }
    };

  return (
    <div className="space-y-4 p-4 bg-white rounded-lg shadow">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-bold">角色管理</h2>
          <div className="flex gap-2">
              <Button variant="secondary" onClick={refreshRoleIds}>
                  <FaSync className="mr-2"/> 刷新列表
              </Button>
              <Button
                  variant="outline"
                  onClick={() => access.refresh()}
                  title="手动刷新当前会话权限；若你刚刚移除了自己访问本页的权限，刷新后可能会被路由守卫重定向"
              >
                  <FaSync className="mr-2" /> 刷新当前会话权限
              </Button>
              <Button onClick={openCreateRole}>
                  <FaPlus className="mr-2"/> 新增角色
              </Button>
          </div>
      </div>

        <div className="border rounded-lg p-4 bg-gray-50 space-y-3">
            <div className="flex items-center justify-between">
                <div className="font-medium">用户注册配置</div>
                <div className="flex gap-2">
                    <Button
                        variant="secondary"
                        onClick={refreshRegistration}
                        disabled={registrationLoading || registrationSaving}
                        className="whitespace-nowrap"
                    >
                        <FaSync className="mr-2" /> 刷新
                    </Button>
                    <Button
                        onClick={saveRegistration}
                        disabled={registrationLoading || registrationSaving}
                        className="whitespace-nowrap"
                    >
                        保存
                    </Button>
                </div>
            </div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_auto] md:items-center">
                <div className="flex items-center gap-2">
                    <Checkbox
                        id="registration-enabled"
                        checked={registrationEnabled}
                        onCheckedChange={() => setRegistrationEnabled(v => !v)}
                        disabled={registrationLoading || registrationSaving}
                    />
                    <Label htmlFor="registration-enabled" className="cursor-pointer">
                        允许用户注册
                    </Label>
                </div>
                <div className="text-sm text-gray-500">
                    关闭后 /register 将返回 403，前端注册页入口同步隐藏
                </div>
            </div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_auto] md:items-center">
                <select
                    className="border rounded px-3 py-2 bg-white"
                    value={String(defaultRegisterRoleId)}
                    onChange={(e) => setDefaultRegisterRoleId(Number(e.target.value))}
                    disabled={registrationLoading || registrationSaving}
                >
                    {roleSummaries.length === 0 ? (
                        <option value={String(defaultRegisterRoleId)}>
                            roleId={defaultRegisterRoleId}
                        </option>
                    ) : null}
                    {roleSummaries.map(r => (
                        <option key={r.roleId} value={String(r.roleId)}>
                            {r.roleName ? `${r.roleName} (roleId=${r.roleId})` : `roleId=${r.roleId}`}
                        </option>
                    ))}
                </select>
                <div className="text-sm text-gray-500">
                    影响 /register 新注册用户默认分配的 roleId
                </div>
            </div>
        </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-[1fr_auto] md:items-center">
        <Input
            placeholder="按 角色名 过滤（本地过滤）"
          value={nameKeyword}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNameKeyword(e.target.value)}
        />
          <Button onClick={() => setNameKeyword(v => v)} variant="secondary" className="whitespace-nowrap">
          <FaSearch className="mr-2" /> 搜索
        </Button>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">roleId</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">角色名</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                  <td colSpan={3} className="text-center py-4">
                  加载中...
                </td>
              </tr>
            ) : filteredRoles.length === 0 ? (
              <tr>
                  <td colSpan={3} className="text-center py-4">
                      暂无角色数据
                </td>
              </tr>
            ) : (
              filteredRoles.map(r => (
                  <tr key={r.roleId}>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{r.roleId}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{r.roleName ?? '-'}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-2">
                      <Button size="sm" variant="outline" onClick={() => openEditRole(r.roleId)}>
                          <FaEdit className="mr-2"/> 配置权限
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      className="bg-red-100 text-red-600 hover:bg-red-200"
                      onClick={() => handleDeleteRoleId(r.roleId)}
                      title="从列表移除（不影响用户分配关系）"
                    >
                        <FaTrash className="mr-2"/> 移除
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

        <Modal
            isOpen={isRoleModalOpen}
            onClose={() => setRoleModalOpen(false)}
            title={editingRoleId ? '编辑角色权限' : '新增角色权限'}
        >
            {/* Top bar moved into RolePermissionEditor to keep everything on one line */}

            {feedback ? (
                <div
                    className={
                        'mb-3 rounded border px-3 py-2 text-sm ' +
                        (feedback.type === 'success'
                            ? 'border-green-200 bg-green-50 text-green-700'
                            : feedback.type === 'error'
                                ? 'border-red-200 bg-red-50 text-red-700'
                                : 'border-blue-200 bg-blue-50 text-blue-700')
                    }
                >
                    <div className="flex items-start justify-between gap-3">
                        <div className="whitespace-pre-wrap">{feedback.message}</div>
                        <button
                            type="button"
                            className="text-gray-500 hover:text-gray-700"
                            onClick={() => setFeedback(null)}
                            aria-label="close"
                        >
                            ×
                        </button>
                    </div>
                </div>
            ) : null}

            <RolePermissionEditor
                mode={editingRoleId ? 'edit' : 'create'}
                roleId={editingRoleId}
                matrixLoading={matrixLoading}
                submitting={editingRoleId ? saving : creating}
                roleNameInput={roleNameInput}
                setRoleNameInput={setRoleNameInput}
                permKeyword={permKeyword}
                setPermKeyword={setPermKeyword}
                onSubmit={editingRoleId ? handleSave : handleCreateRoleWithMatrix}
                submitLabel={editingRoleId ? '保存' : '创建'}
                onRefreshAccess={undefined}
                bulkBar={
                    <>
                        <span className="text-gray-500">批量：</span>
                        <Button type="button" size="sm" variant="outline" onClick={() => setAllVisible('ALLOW')}>
                            全允许
                        </Button>
                        <Button type="button" size="sm" variant="outline" onClick={() => setAllVisible('DENY')}>
                            全拒绝
                        </Button>
                        <Button type="button" size="sm" variant="outline" onClick={() => setAllVisible('UNSET')}>
                            全清空
                        </Button>
                    </>
                }
                matrix={
                    <div className="space-y-3 max-h-[80vh] overflow-auto pr-1 text-base">
                        {filteredTree.map(g1 => {
                            const isCollapsed = collapsedL1[g1.key] ?? false;

                            return (
                                <div key={g1.key} className="border rounded">
                                    <div className="flex items-center justify-between px-3 py-2 bg-gray-50">
                                        <div className="font-semibold text-base">
                                            {g1.label} <span className="text-sm text-gray-400">({g1.key})</span>
                                        </div>
                                        <div className="flex gap-2">
                                            {(g1.key === 'admin' || g1.key === 'portal') ? (
                                                <Button
                                                    type="button"
                                                    size="sm"
                                                    variant="secondary"
                                                    className="whitespace-nowrap"
                                                    aria-expanded={!isCollapsed}
                                                    onClick={() =>
                                                        setCollapsedL1(prev => ({
                                                            ...prev,
                                                            [g1.key]: !(prev[g1.key] ?? false),
                                                        }))
                                                    }
                                                >
                                                    {isCollapsed ? '全部展开' : '全部折叠'}
                                                </Button>
                                            ) : null}
                                            <Button type="button" size="sm" variant="outline"
                                                    onClick={() => bulkSet(groupPermissionIds(g1.key), 'ALLOW')}>
                                                允许
                                            </Button>
                                            <Button type="button" size="sm" variant="outline"
                                                    onClick={() => bulkSet(groupPermissionIds(g1.key), 'DENY')}>
                                                拒绝
                                            </Button>
                                            <Button type="button" size="sm" variant="outline"
                                                    onClick={() => bulkSet(groupPermissionIds(g1.key), 'UNSET')}>
                                                清空
                                            </Button>
                                        </div>
                                    </div>

                                    {!isCollapsed ? (
                                        <div className="p-3 space-y-4">
                                            {g1.subGroups.map((g2: SubGroupVM) => {
                                                return (
                                                    <div key={`${g1.key}/${g2.key}`} className="border rounded">
                                                        <div
                                                            className="flex items-center justify-between px-3 py-2 bg-white">
                                                            <div className="text-base font-medium">
                                                                {g2.label}{' '}
                                                                <span
                                                                    className="text-sm text-gray-400">({g1.key}_{g2.key})</span>
                                                            </div>
                                                            <div className="flex gap-2">
                                                                <Button type="button" size="sm" variant="outline"
                                                                        onClick={() => bulkSet(groupPermissionIds(g1.key, g2.key), 'ALLOW')}>
                                                                    允许
                                                                </Button>
                                                                <Button type="button" size="sm" variant="outline"
                                                                        onClick={() => bulkSet(groupPermissionIds(g1.key, g2.key), 'DENY')}>
                                                                    拒绝
                                                                </Button>
                                                                <Button type="button" size="sm" variant="outline"
                                                                        onClick={() => bulkSet(groupPermissionIds(g1.key, g2.key), 'UNSET')}>
                                                                    清空
                                                                </Button>
                                                            </div>
                                                        </div>

                                                        <div className="p-2 overflow-x-auto">
                                                            <table
                                                                className="min-w-full divide-y divide-gray-200 text-base">
                                                                <thead className="bg-gray-50">
                                                                <tr>
                                                                    <th className="px-3 py-2 text-left text-sm font-medium text-gray-500 uppercase tracking-wider">
                                                                        resource
                                                                    </th>
                                                                    {(['READ', 'WRITE', 'UPDATE', 'DELETE', 'EXEC', 'OTHER'] as StandardAction[]).map(a => (
                                                                        <th
                                                                            key={a}
                                                                            className="px-2 py-2 text-center text-sm font-medium text-gray-500 uppercase tracking-wider whitespace-nowrap"
                                                                        >
                                                                            {stdActionLabel(a)}
                                                                        </th>
                                                                    ))}
                                                                </tr>
                                                                </thead>
                                                                <tbody className="bg-white divide-y divide-gray-200">
                                                                {g2.resources.map((rg: ResourceGroupVM) => {
                                                                    const buckets = actionBucket(rg.perms);
                                                                    const resIds = rg.perms.map((p: PermissionVM) => p.id);

                                                                    const states = resIds.map(getState);
                                                                    const unique = new Set(states);
                                                                    const agg: TriState | 'MIXED' = unique.size === 1 ? states[0] : 'MIXED';
                                                                    const aggLabel = agg === 'MIXED' ? '部分' : triStateLabel(agg);

                                                                    return (
                                                                        <tr key={rg.resourceKey}>
                                                                            <td className="px-3 py-2 text-base text-gray-700 whitespace-nowrap">
                                                                                <div
                                                                                    className="font-medium">{rg.resourceKey}</div>
                                                                                <ActionDescriptions perms={rg.perms}/>
                                                                                {agg === 'MIXED' ? (
                                                                                    <div
                                                                                        className="text-sm text-gray-400">{aggLabel}</div>
                                                                                ) : null}
                                                                            </td>
                                                                            <td className="px-2 py-2">{renderActionCell(buckets.READ, `${rg.resourceKey} / 读`)}</td>
                                                                            <td className="px-2 py-2">{renderActionCell(buckets.WRITE, `${rg.resourceKey} / 写`)}</td>
                                                                            <td className="px-2 py-2">{renderActionCell(buckets.UPDATE, `${rg.resourceKey} / 更`)}</td>
                                                                            <td className="px-2 py-2">{renderActionCell(buckets.DELETE, `${rg.resourceKey} / 删`)}</td>
                                                                            <td className="px-2 py-2">{renderActionCell(buckets.EXEC, `${rg.resourceKey} / 执行`)}</td>
                                                                            <td className="px-2 py-2">{renderActionCell(buckets.OTHER, `${rg.resourceKey} / 更多`)}</td>
                                                                        </tr>
                                                                    );
                                                                })}
                                                                </tbody>
                                                            </table>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    ) : null}
                                </div>
                            );
                        })}

                        {filteredTree.length === 0 ? <div className="text-base text-gray-500">无匹配权限</div> : null}
                    </div>
                }
            />
      </Modal>
        {adminStepUpModal}
    </div>
  );

    async function handleDeleteRoleId(roleId: number) {
        if (!window.confirm(`确定要从“角色列表”移除 roleId=${roleId} 吗？\n\n注意：这不会删除 user_role_links（用户分配关系）。\n如果你希望该 roleId 不再出现在自动列表里，请先清空它的 role_permissions。`)) {
            return;
        }
        setRoles(prev => prev.filter(r => r.roleId !== roleId));
    }
};


export default RolesManagement;
