import React, { useState, useEffect } from 'react';
import { userAccessService } from '../../../../services/userAccessService';
import { UserDTO, UserCreateDTO } from '../../../../types/userAccess';
import { Button } from '../../../../components/ui/button';
import { Input } from '../../../../components/ui/input';
import { Label } from '../../../../components/ui/label';
import { Checkbox } from '../../../../components/ui/checkbox';
import { Badge } from '../../../../components/ui/badge';
import { FaEdit, FaTrash, FaUserTag, FaPlus, FaSearch, FaBan, FaUnlock } from 'react-icons/fa';
import { listRoleSummaries, RoleSummaryDTO } from '../../../../services/rolePermissionsService';
import { getRegistrationSettings } from '../../../../services/adminSettingsService';
import { useAdminStepUp } from '../../../../components/admin/useAdminStepUp';
import { isAdminStepUpRequired } from '../../../../services/apiError';

export default function UserManagementPage() {
    const [users, setUsers] = useState<UserDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [page, setPage] = useState(1);
    const [totalPages, setTotalPages] = useState(0);

    // Search (split fields)
    const [usernameQuery, setUsernameQuery] = useState('');
    const [emailQuery, setEmailQuery] = useState('');

    // Advanced filters
    const [isAdvancedOpen, setIsAdvancedOpen] = useState(false);
    const [filters, setFilters] = useState<{
        status?: string;
        includeDeleted: boolean;
        createdAfter?: string;
        createdBefore?: string;
        lastLoginFrom?: string;
        lastLoginTo?: string;
    }>({
        includeDeleted: false // 默认：只看未删除
    });

    const [deletingIds, setDeletingIds] = useState<Set<number>>(new Set());
    const [banLoadingIds, setBanLoadingIds] = useState<Set<number>>(new Set());

    // Modals state
    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [isEditOpen, setIsEditOpen] = useState(false);
    const [isRolesOpen, setIsRolesOpen] = useState(false);

    // Form state
    const [currentUser, setCurrentUser] = useState<Partial<UserDTO>>({});
    const [createForm, setCreateForm] = useState<UserCreateDTO>({
        email: '',
        username: '',
        passwordHash: '',
        status: 'ACTIVE',
        isDeleted: false
    });
    const [createRoleIds, setCreateRoleIds] = useState<number[]>([]);

    // Roles state
    const [availableRoles, setAvailableRoles] = useState<RoleSummaryDTO[]>([]);
    const [rolesLoading, setRolesLoading] = useState(false);
    const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([]);
    const [targetUserId, setTargetUserId] = useState<number | null>(null);
    const { ensureAdminStepUp, adminStepUpModal } = useAdminStepUp();

    useEffect(() => {
        fetchUsers();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [page, usernameQuery, emailQuery, filters]);

    const fetchUsers = async () => {
        setLoading(true);
        try {
            const username = usernameQuery.trim();
            const email = emailQuery.trim();

            const res = await userAccessService.queryUsers({
                pageNum: page,
                pageSize: 10,
                // 分开传递：后端可按 LIKE / contains 做模糊搜索；不做长度限制
                username: username.length > 0 ? username : undefined,
                email: email.length > 0 ? email : undefined,
                status: filters.status ? [filters.status] : undefined,
                createdAfter: filters.createdAfter || undefined,
                createdBefore: filters.createdBefore || undefined,
                lastLoginFrom: filters.lastLoginFrom || undefined,
                lastLoginTo: filters.lastLoginTo || undefined,
                includeDeleted: filters.includeDeleted
            });
            setUsers(res.content);
            setTotalPages(res.totalPages);
        } catch (error) {
            console.error(error);
            alert('获取用户列表失败');
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        // 切回第一页；真正查询由 useEffect 触发，避免 setPage(1) 后 fetchUsers() 仍使用旧 page。
        setPage(1);
    };

    const applyAdvancedFilters = () => {
        setPage(1);
    };

    const resetFilters = () => {
        setUsernameQuery('');
        setEmailQuery('');
        setFilters({ includeDeleted: false });
        setPage(1);
    };

    const handleCreate = async () => {
        try {
            await userAccessService.createUser({
                ...createForm,
                roleIds: createRoleIds
            });
            setIsCreateOpen(false);
            fetchUsers();
            setCreateForm({ email: '', username: '', passwordHash: '', status: 'ACTIVE', isDeleted: false } as never);
            setCreateRoleIds([]);
            alert('用户创建成功');
        } catch (error) {
            console.error(error);
            alert('创建用户失败');
        }
    };

    const openCreateModal = async () => {
        setCreateForm({ email: '', username: '', passwordHash: '', status: 'ACTIVE', isDeleted: false } as never);
        setCreateRoleIds([]);
        setIsCreateOpen(true);

        setRolesLoading(true);
        try {
            const [roles, settings] = await Promise.all([
                listRoleSummaries(),
                getRegistrationSettings(),
            ]);
            setAvailableRoles(roles);
            const defaultRoleId = Number(settings.defaultRegisterRoleId) || 1;
            setCreateRoleIds([defaultRoleId]);
        } catch (e) {
            console.error(e);
        } finally {
            setRolesLoading(false);
        }
    };

    const toggleCreateRole = (roleId: number) => {
        setCreateRoleIds(prev =>
            prev.includes(roleId)
                ? prev.filter(id => id !== roleId)
                : [...prev, roleId]
        );
    };

    const handleUpdate = async () => {
        if (!currentUser.id) return;
        try {
            await userAccessService.updateUser({
                id: currentUser.id,
                email: currentUser.email,
                username: currentUser.username,
                status: currentUser.status,
                isDeleted: typeof currentUser.isDeleted === 'boolean' ? currentUser.isDeleted : undefined
            });
            setIsEditOpen(false);
            fetchUsers();
            alert('用户更新成功');
        } catch (error) {
            console.error(error);
            alert('更新用户失败');
        }
    };

    const handleDelete = async (user: UserDTO) => {
        if (!user?.id) return;
        if (deletingIds.has(user.id)) return;

        const isSoftDeleted = user.isDeleted === true;

        try {
            setDeletingIds(prev => {
                const next = new Set(prev);
                next.add(user.id);
                return next;
            });

            if (!isSoftDeleted) {
                const ok = confirm('将该用户标记为已删除（软删除，可恢复/可审计）。是否继续？');
                if (!ok) return;

                await userAccessService.updateUser({
                    id: user.id,
                    isDeleted: true
                });

                alert('已标记为软删除');
                await fetchUsers();
                return;
            }

            // 已软删除 -> 硬删（永久删除）二次确认
            const ok1 = confirm('该用户已标记为已删除。继续将永久删除（不可恢复）？');
            if (!ok1) return;
            const ok2 = confirm('请再次确认：真的要永久删除该用户吗？此操作不可撤销。');
            if (!ok2) return;

            await userAccessService.hardDeleteUser(user.id);
            alert('用户已永久删除');
            await fetchUsers();
        } catch (error) {
            console.error(error);
            const msg = error instanceof Error ? error.message : String(error);

            // Friendlier admin hint for FK conflicts / referenced content
            if (
                msg &&
                (msg.includes('foreign key constraint') ||
                    msg.includes('Cannot delete') ||
                    msg.includes('无法永久删除') ||
                    msg.includes('关联内容'))
            ) {
                alert(
                    `无法永久删除该用户：该账号仍被帖子/评论等内容引用。\n\n` +
                        `建议：\n` +
                        `1）保留为软删除（推荐，可审计）；\n` +
                        `2）如必须删除，请先处理/迁移该用户名下的内容后再试。\n\n` +
                        `后端返回：${msg}`
                );
                return;
            }

            alert(`删除用户失败：${msg || '可能无权限或存在关联数据'}`);
        } finally {
            setDeletingIds(prev => {
                const next = new Set(prev);
                next.delete(user.id);
                return next;
            });
        }
    };

    const openRolesModal = async (user: UserDTO) => {
        setTargetUserId(user.id);
        setIsRolesOpen(true);

        setRolesLoading(true);
        try {
            // 1) load all roles (roleId + roleName)
            const roles = await listRoleSummaries();
            setAvailableRoles(roles);

            // 2) load user's current links (pre-select)
            const userRoles = await userAccessService.getUserRoles(user.id);
            setSelectedRoleIds(userRoles.map(r => r.roleId));
        } catch (error) {
            console.error(error);
            alert('加载角色失败');
            setAvailableRoles([]);
            setSelectedRoleIds([]);
        } finally {
            setRolesLoading(false);
        }
    };

    const handleAssignRoles = async () => {
        if (!targetUserId) return;
        const reason = window.prompt('请输入变更原因（用于审计，可留空）') ?? '';
        const normalizedRoleIds = Array.from(new Set(selectedRoleIds)).filter(id => Number.isFinite(id) && id > 0);
        if (normalizedRoleIds.length !== selectedRoleIds.length) {
            alert('角色分配失败：包含非法角色ID');
            return;
        }
        const availableIds = new Set(availableRoles.map(r => r.roleId));
        const unknown = normalizedRoleIds.filter(id => !availableIds.has(id));
        if (unknown.length > 0) {
            alert(`角色分配失败：选择的角色已不存在（${unknown.join(', ')}）`);
            return;
        }
        try {
            await userAccessService.assignRoles(targetUserId, normalizedRoleIds, { adminReason: reason.trim() || undefined });
            setIsRolesOpen(false);
            alert('角色分配成功');
        } catch (error) {
            if (isAdminStepUpRequired(error)) {
                const r = await ensureAdminStepUp();
                if (r.ensured) {
                    await userAccessService.assignRoles(targetUserId, normalizedRoleIds, { adminReason: reason.trim() || undefined });
                    setIsRolesOpen(false);
                    alert('角色分配成功');
                    return;
                }
            }
            console.error(error);
            alert('角色分配失败');
        }
    };

    const toggleRole = (roleId: number) => {
        setSelectedRoleIds(prev =>
            prev.includes(roleId)
                ? prev.filter(id => id !== roleId)
                : [...prev, roleId]
        );
    };

    const statusText: Record<string, string> = {
        ACTIVE: '启用',
        DISABLED: '禁用',
        EMAIL_UNVERIFIED: '未验证邮箱',
        DELETED: '已删除'
    };

    const formatDateTimeZhCn = (value?: string | null) => {
        if (!value) return '—';
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) return String(value);
        return d.toLocaleString('zh-CN');
    };

    const banInfoOf = (user: UserDTO): { active: boolean; reason?: string; at?: string } => {
        const meta = user?.metadata;
        if (!meta || typeof meta !== 'object') return { active: false };
        const ban = (meta as Record<string, unknown>)['ban'];
        if (!ban || typeof ban !== 'object') return { active: false };
        const b = ban as Record<string, unknown>;
        const active = b['active'] === true;
        return {
            active,
            reason: typeof b['reason'] === 'string' ? b['reason'] : undefined,
            at: typeof b['bannedAt'] === 'string' ? b['bannedAt'] : undefined,
        };
    };

    const handleBan = async (user: UserDTO) => {
        if (!user?.id) return;
        if (banLoadingIds.has(user.id)) return;
        const reason = window.prompt('请输入封禁原因（必填）：');
        if (reason === null) return;
        const reasonTrim = reason.trim();
        if (!reasonTrim) {
            alert('必须填写封禁原因');
            return;
        }
        const ok = window.confirm(`确认封禁用户 ${user.username}（ID=${user.id}）？`);
        if (!ok) return;

        try {
            setBanLoadingIds(prev => {
                const next = new Set(prev);
                next.add(user.id);
                return next;
            });
            await userAccessService.banUser(user.id, reasonTrim);
            await fetchUsers();
            alert('已封禁用户');
        } catch (e) {
            console.error(e);
            alert(`封禁失败：${e instanceof Error ? e.message : String(e)}`);
        } finally {
            setBanLoadingIds(prev => {
                const next = new Set(prev);
                next.delete(user.id);
                return next;
            });
        }
    };

    const handleUnban = async (user: UserDTO) => {
        if (!user?.id) return;
        if (banLoadingIds.has(user.id)) return;
        const reason = window.prompt('请输入解封原因（必填）：');
        if (reason === null) return;
        const reasonTrim = reason.trim();
        if (!reasonTrim) {
            alert('必须填写解封原因');
            return;
        }
        const ok = window.confirm(`确认解封用户 ${user.username}（ID=${user.id}）？`);
        if (!ok) return;

        try {
            setBanLoadingIds(prev => {
                const next = new Set(prev);
                next.add(user.id);
                return next;
            });
            await userAccessService.unbanUser(user.id, reasonTrim);
            await fetchUsers();
            alert('已解封用户');
        } catch (e) {
            console.error(e);
            alert(`解封失败：${e instanceof Error ? e.message : String(e)}`);
        } finally {
            setBanLoadingIds(prev => {
                const next = new Set(prev);
                next.delete(user.id);
                return next;
            });
        }
    };

    return (
        <div className="p-4 bg-white space-y-4">
            <div className="flex justify-between items-center">
                <h1 className="text-xl font-bold">用户管理</h1>
                <Button onClick={openCreateModal}>
                    <FaPlus className="mr-2" /> 创建用户
                </Button>
            </div>

            <form onSubmit={handleSearch} className="flex gap-4 mb-2 items-center flex-wrap">
                <Input
                    placeholder="按用户名模糊搜索…"
                    value={usernameQuery}
                    onChange={e => setUsernameQuery(e.target.value)}
                    className="max-w-sm"
                />
                <Input
                    placeholder="按邮箱模糊搜索…"
                    value={emailQuery}
                    onChange={e => setEmailQuery(e.target.value)}
                    className="max-w-sm"
                />

                <Button type="submit" variant="secondary">
                    <FaSearch className="mr-2" /> 搜索
                </Button>
                <Button
                    type="button"
                    variant="outline"
                    onClick={() => setIsAdvancedOpen(v => !v)}
                >
                    高级筛选
                </Button>
                <Button type="button" variant="outline" onClick={resetFilters}>
                    清空
                </Button>

                <div className="w-full text-xs text-gray-500">
                    提示：用户名/邮箱两个条件会同时生效（取交集）；任意一个留空即可。
                </div>
            </form>

            {isAdvancedOpen && (
                <div className="border rounded-md p-4 bg-gray-50 space-y-3">
                    <div className="flex flex-wrap gap-4 items-end">
                        <div className="min-w-[220px]">
                            <Label>用户状态</Label>
                            <select
                                className="w-full border rounded-md p-2 bg-white"
                                value={filters.status ?? ''}
                                onChange={e => setFilters(prev => ({ ...prev, status: e.target.value || undefined }))}
                            >
                                <option value="">全部</option>
                                <option value="ACTIVE">启用</option>
                                <option value="DISABLED">禁用</option>
                                <option value="EMAIL_UNVERIFIED">未验证邮箱</option>
                                <option value="DELETED">已删除</option>
                            </select>
                            <div className="text-xs text-gray-500 mt-1">（若枚举与后端不一致，请按后端实际值调整）</div>
                        </div>

                        <div className="flex items-center gap-2 mt-6">
                            <Checkbox
                                id="include-deleted"
                                checked={filters.includeDeleted}
                                onCheckedChange={() =>
                                    setFilters(prev => ({ ...prev, includeDeleted: !prev.includeDeleted }))
                                }
                            />
                            <Label htmlFor="include-deleted" className="cursor-pointer">
                                显示已删除用户
                            </Label>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <Label>创建时间（起）</Label>
                            <Input
                                type="date"
                                value={filters.createdAfter ?? ''}
                                onChange={e => setFilters(prev => ({ ...prev, createdAfter: e.target.value || undefined }))}
                            />
                        </div>
                        <div>
                            <Label>创建时间（止）</Label>
                            <Input
                                type="date"
                                value={filters.createdBefore ?? ''}
                                onChange={e => setFilters(prev => ({ ...prev, createdBefore: e.target.value || undefined }))}
                            />
                        </div>
                        <div>
                            <Label>上次登录（起）</Label>
                            <Input
                                type="date"
                                value={filters.lastLoginFrom ?? ''}
                                onChange={e => setFilters(prev => ({ ...prev, lastLoginFrom: e.target.value || undefined }))}
                            />
                        </div>
                        <div>
                            <Label>上次登录（止）</Label>
                            <Input
                                type="date"
                                value={filters.lastLoginTo ?? ''}
                                onChange={e => setFilters(prev => ({ ...prev, lastLoginTo: e.target.value || undefined }))}
                            />
                        </div>
                    </div>

                    <div className="flex justify-end">
                        <Button type="button" onClick={applyAdvancedFilters} disabled={loading}>
                            应用筛选
                        </Button>
                    </div>
                </div>
            )}

            <div className="rounded-md">
                <table className="w-full text-sm text-left">
                    <thead className="bg-gray-100 border-b">
                    <tr>
                        <th className="p-4 font-medium">编号</th>
                        <th className="p-4 font-medium">用户名</th>
                        <th className="p-4 font-medium">邮箱</th>
                        <th className="p-4 font-medium">状态</th>
                        <th className="p-4 font-medium">封禁信息</th>
                        <th className="p-4 font-medium">上次登录</th>
                        <th className="p-4 font-medium">是否删除</th>
                        <th className="p-4 font-medium">创建时间</th>
                        <th className="p-4 font-medium text-right">操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    {loading ? (
                        <tr><td colSpan={9} className="p-4 text-center">加载中…</td></tr>
                    ) : users.map(user => (
                        (() => {
                            const ban = banInfoOf(user);
                            const banActive = ban.active || user.status === 'DISABLED';
                            const banText = banActive
                                ? `${ban.at ? formatDateTimeZhCn(ban.at) : '—'}${ban.reason ? ` · ${ban.reason}` : ''}`
                                : '—';
                            return (
                        <tr key={user.id} className="border-b hover:bg-gray-50">
                            <td className="p-4">{user.id}</td>
                            <td className="p-4 font-medium">{user.username}</td>
                            <td className="p-4">{user.email}</td>
                            <td className="p-4">
                                <Badge variant={user.status === 'ACTIVE' ? 'default' : 'secondary'}>
                                    {statusText[user.status] ?? user.status}
                                </Badge>
                            </td>
                            <td className="p-4">
                                {banActive ? (
                                    <div className="space-y-1">
                                        <Badge className="bg-red-100 text-red-800">封禁中</Badge>
                                        <div className="text-xs text-gray-600 break-words">{banText}</div>
                                    </div>
                                ) : (
                                    <span className="text-gray-600">—</span>
                                )}
                            </td>
                            <td className="p-4">{formatDateTimeZhCn(user.lastLoginAt)}</td>
                            <td className="p-4">
                                {user.isDeleted === true ? (
                                    <Badge variant="secondary">已删除</Badge>
                                ) : (
                                    <span className="text-gray-600">否</span>
                                )}
                            </td>
                            <td className="p-4">
                                {user.createdAt ? new Date(user.createdAt).toLocaleDateString('zh-CN') : '—'}
                            </td>
                            <td className="p-4 text-right space-x-2">
                                <Button size="sm" variant="outline" onClick={() => openRolesModal(user)} title="分配角色">
                                    <FaUserTag className="h-4 w-4" />
                                </Button>
                                {banActive ? (
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => handleUnban(user)}
                                        disabled={banLoadingIds.has(user.id)}
                                        title="解封用户"
                                    >
                                        <FaUnlock className="h-4 w-4" />
                                    </Button>
                                ) : (
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => handleBan(user)}
                                        disabled={banLoadingIds.has(user.id)}
                                        title="封禁用户"
                                    >
                                        <FaBan className="h-4 w-4" />
                                    </Button>
                                )}
                                <Button size="sm" variant="outline" onClick={() => {
                                    setCurrentUser(user);
                                    setIsEditOpen(true);
                                }} title="编辑">
                                    <FaEdit className="h-4 w-4" />
                                </Button>
                                <Button
                                    size="sm"
                                    className="bg-red-600 hover:bg-red-700 text-white"
                                    onClick={() => handleDelete(user)}
                                    disabled={deletingIds.has(user.id)}
                                    title={user.isDeleted === true ? '永久删除' : '软删除'}
                                >
                                    <FaTrash className="h-4 w-4" />
                                </Button>
                            </td>
                        </tr>
                            );
                        })()
                    ))}
                    </tbody>
                </table>
            </div>

            <div className="flex justify-center mt-4 gap-2">
                <Button
                    variant="outline"
                    disabled={page === 1}
                    onClick={() => setPage(p => p - 1)}
                >
                    上一页
                </Button>
                <span className="py-2 px-4">第 {page} 页，共 {Math.max(totalPages, 1)} 页</span>
                <Button
                    variant="outline"
                    disabled={totalPages <= 0 || page >= totalPages}
                    onClick={() => setPage(p => p + 1)}
                >
                    下一页
                </Button>
            </div>
            {/* Create Modal */}
            {isCreateOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white p-6 rounded-lg w-full max-w-md">
                        <h2 className="text-xl font-bold mb-4">创建用户</h2>
                        <div className="space-y-4">
                            <div>
                                <Label>用户名</Label>
                                <Input
                                    value={createForm.username}
                                    onChange={e => setCreateForm({ ...createForm, username: e.target.value })}
                                />
                            </div>
                            <div>
                                <Label>邮箱</Label>
                                <Input
                                    value={createForm.email}
                                    onChange={e => setCreateForm({ ...createForm, email: e.target.value })}
                                />
                            </div>
                            <div>
                                <Label>密码</Label>
                                <Input
                                    type="password"
                                    value={createForm.passwordHash}
                                    onChange={e => setCreateForm({ ...createForm, passwordHash: e.target.value })}
                                />
                            </div>
                            <div>
                                <Label>状态</Label>
                                <select
                                    className="w-full border rounded-md p-2"
                                    value={createForm.status}
                                    onChange={e => setCreateForm({ ...createForm, status: e.target.value as never })}
                                >
                                    <option value="ACTIVE">启用</option>
                                    <option value="DISABLED">禁用</option>
                                    <option value="EMAIL_UNVERIFIED">未验证邮箱</option>
                                    <option value="DELETED">已删除</option>
                                </select>
                            </div>
                            <div>
                                <Label>分配角色</Label>
                                {rolesLoading ? (
                                    <div className="p-3 text-sm text-gray-600">加载角色中…</div>
                                ) : availableRoles.length === 0 ? (
                                    <div className="p-3 text-sm text-gray-600">暂无可分配角色</div>
                                ) : (
                                    <div className="max-h-48 overflow-y-auto space-y-2 border p-3 rounded-md bg-white">
                                        {availableRoles.map(role => (
                                            <div key={role.roleId} className="flex items-center space-x-2">
                                                <Checkbox
                                                    id={`create-role-${role.roleId}`}
                                                    checked={createRoleIds.includes(role.roleId)}
                                                    onCheckedChange={() => toggleCreateRole(role.roleId)}
                                                />
                                                <Label htmlFor={`create-role-${role.roleId}`} className="cursor-pointer">
                                                    ROLE_ID_{role.roleId}
                                                    <span className="text-gray-500 ml-2">
                                                        {role.roleName ? `(${role.roleName})` : '(未命名)'}
                                                    </span>
                                                </Label>
                                            </div>
                                        ))}
                                    </div>
                                )}
                                <div className="text-xs text-gray-500 mt-2">
                                    默认已预选“注册默认角色”（可在角色管理页修改）。
                                </div>
                            </div>
                            <div className="flex justify-end gap-2 mt-6">
                                <Button variant="outline" onClick={() => setIsCreateOpen(false)}>取消</Button>
                                <Button onClick={handleCreate}>创建</Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Edit Modal */}
            {isEditOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white p-6 rounded-lg w-full max-w-md">
                        <h2 className="text-xl font-bold mb-4">编辑用户</h2>
                        <div className="space-y-4">
                            <div>
                                <Label>用户名</Label>
                                <Input
                                    value={currentUser.username || ''}
                                    onChange={e => setCurrentUser({ ...currentUser, username: e.target.value })}
                                />
                            </div>
                            <div>
                                <Label>邮箱</Label>
                                <Input
                                    value={currentUser.email || ''}
                                    onChange={e => setCurrentUser({ ...currentUser, email: e.target.value })}
                                />
                            </div>
                            <div>
                                <Label>状态</Label>
                                <select
                                    className="w-full border rounded-md p-2"
                                    value={currentUser.status}
                                    onChange={e => setCurrentUser({ ...currentUser, status: e.target.value as never })}
                                >
                                    <option value="ACTIVE">启用</option>
                                    <option value="DISABLED">禁用</option>
                                    <option value="EMAIL_UNVERIFIED">未验证邮箱</option>
                                    <option value="DELETED">已删除</option>
                                </select>
                            </div>
                            <div className="flex justify-end gap-2 mt-6">
                                <Button variant="outline" onClick={() => setIsEditOpen(false)}>取消</Button>
                                <Button onClick={handleUpdate}>保存</Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Roles Modal */}
            {isRolesOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white p-6 rounded-lg w-full max-w-lg">
                        <h2 className="text-xl font-bold mb-4">分配角色</h2>

                        {rolesLoading ? (
                            <div className="p-4 text-center text-sm text-gray-600">加载角色中…</div>
                        ) : availableRoles.length === 0 ? (
                            <div className="p-4 text-center text-sm text-gray-600">
                                暂无可分配角色（角色列表为空或接口无权限）。
                            </div>
                        ) : (
                            <div className="max-h-60 overflow-y-auto space-y-2 border p-4 rounded-md">
                                {availableRoles.map(role => (
                                    <div key={role.roleId} className="flex items-center space-x-2">
                                        <Checkbox
                                            id={`role-${role.roleId}`}
                                            checked={selectedRoleIds.includes(role.roleId)}
                                            onCheckedChange={() => toggleRole(role.roleId)}
                                        />
                                        <Label htmlFor={`role-${role.roleId}`} className="cursor-pointer">
                                            ROLE_ID_{role.roleId}
                                            <span className="text-gray-500 ml-2">
                                                {role.roleName ? `(${role.roleName})` : '(未命名)'}
                                            </span>
                                        </Label>
                                    </div>
                                ))}
                            </div>
                        )}

                        <div className="flex justify-end gap-2 mt-6">
                            <Button variant="outline" onClick={() => setIsRolesOpen(false)}>取消</Button>
                            <Button onClick={handleAssignRoles} disabled={rolesLoading}>保存角色</Button>
                        </div>
                    </div>
                </div>
            )}
            {adminStepUpModal}
        </div>
    );
}
