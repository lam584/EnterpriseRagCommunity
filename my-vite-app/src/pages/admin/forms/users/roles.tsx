import React, { useEffect, useMemo, useState } from 'react';
import { Button } from '../../../../components/ui/button';
import { Input } from '../../../../components/ui/input';
import { Checkbox } from '../../../../components/ui/checkbox';
import { Label } from '../../../../components/ui/label';
import { queryPermissions, PermissionsUpdateDTO } from '../../../../services/permissionsService';
import {
  RoleCreateDTO,
  // RoleUpdateDTO,
  RoleQueryDTO,
  rolesService,
} from '../../../../services/rolesService';
import {
  deleteRolePermission,
  listRolePermissionsByRole,
  upsertRolePermission,
} from '../../../../services/rolePermissionsService';
import { getCurrentAdmin } from '../../../../services/authService';

// 说明：
// - 角色 CRUD 走 /api/user-roles（后端当前 controller 未标注 @PreAuthorize）。
//   如果后端最终要求 ADMIN 权限，请在后端加权限控制后，这里无需改动。
// - 角色-权限矩阵接口返回的是 Entity（RolePermissionsEntity），为了避免 Hibernate proxy 序列化风险，
//   后端更推荐返回 DTO；但在“零修改底层”的约束下，前端这里做了最小字段读取（roleId/permissionId/allow）。

type RoleDTO = {
  /**
   * 兼容：有些后端/序列化可能返回 id 为 string，或字段名为 roleId。
   * 页面内部会统一 normalize 成 number。
   */
  id?: number | string;
  roleId?: number | string;
  tenantId?: number;
  roles: string;
  canLogin: boolean;
  canViewAnnouncement: boolean;
  canViewHelpArticles: boolean;
  canResetOwnPassword: boolean;
  canComment: boolean;
  notes?: string;
};

type Page<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
};

type RolePermissionEntityLike = {
  roleId: number;
  permissionId: number;
  allow: boolean;
};

const Modal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}> = ({ isOpen, onClose, title, children }) => {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl p-6">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold">{title}</h3>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">
            &times;
          </button>
        </div>
        {children}
      </div>
    </div>
  );
};

// const toRoleUpdate = (role: RoleDTO): RoleUpdateDTO => {
//   if (typeof role.id !== 'number') throw new Error('role.id is required');
//   return {
//     id: role.id,
//     tenantId: role.tenantId,
//     roles: role.roles,
//     canLogin: role.canLogin,
//     canViewAnnouncement: role.canViewAnnouncement,
//     canViewHelpArticles: role.canViewHelpArticles,
//     canResetOwnPassword: role.canResetOwnPassword,
//     canComment: role.canComment,
//     notes: role.notes,
//   };
// };

const emptyCreateForm = (): RoleCreateDTO => ({
  tenantId: undefined,
  roles: '',
  canLogin: true,
  canViewAnnouncement: false,
  canViewHelpArticles: false,
  canResetOwnPassword: false,
  canComment: false,
  notes: '',
});

const RolesManagement: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize] = useState(10);

  const [rolesPage, setRolesPage] = useState<Page<RoleDTO>>({
    content: [],
    totalPages: 0,
    totalElements: 0,
    size: 10,
    number: 0,
  });

  const [nameKeyword, setNameKeyword] = useState('');

  // Role Modal
  const [isRoleModalOpen, setRoleModalOpen] = useState(false);
  const [currentRole, setCurrentRole] = useState<RoleDTO | null>(null);
  const [roleForm, setRoleForm] = useState<RoleCreateDTO>(emptyCreateForm());

  // Permissions matrix
  const [permissions, setPermissions] = useState<PermissionsUpdateDTO[]>([]);
  const [rolePermissions, setRolePermissions] = useState<RolePermissionEntityLike[]>([]);
  const [matrixLoading, setMatrixLoading] = useState(false);

  // 多租户：尽量从当前登录管理员信息中推断 tenantId
  const [defaultTenantId, setDefaultTenantId] = useState<number | undefined>(undefined);

  const rolePermAllowSet = useMemo(() => {
    const set = new Set<number>();
    for (const rp of rolePermissions) {
      if (rp.allow) set.add(rp.permissionId);
    }
    return set;
  }, [rolePermissions]);

  const parseRoleId = (role?: Partial<RoleDTO> | null): number | undefined => {
    const raw = role?.id ?? role?.roleId;
    if (raw == null) return undefined;
    const n = typeof raw === 'number' ? raw : Number(String(raw));
    return Number.isFinite(n) ? n : undefined;
  };

  const normalizeRole = (role: RoleDTO): RoleDTO & { id?: number } => {
    const id = parseRoleId(role);
    return {
      ...role,
      id,
    };
  };

  const fetchRoles = async () => {
    setLoading(true);
    try {
      const query: RoleQueryDTO = { pageNum, pageSize };
      const res = await rolesService.queryRoles(query);

      // 统一修正 id 字段（避免 id 为 string 或字段名不一致）
      const page = res as unknown as Page<RoleDTO>;
      const normalized: Page<RoleDTO> = {
        ...page,
        content: (page.content ?? []).map(normalizeRole),
      };

      setRolesPage(normalized);
    } catch (e) {
      console.error(e);
      alert('加载角色列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageNum, pageSize]);

  const handleSearch = () => {
    // 后端 /api/user-roles 目前不支持 keyword 查询，这里做本地过滤；
    // 若后端新增 query 参数/Specification，再改成服务端搜索。
    // 这里仍重置页码以获得一致的用户体验。
    setPageNum(1);
  };

  const filteredRoles = useMemo(() => {
    const keyword = nameKeyword.trim().toLowerCase();
    const content = rolesPage.content ?? [];
    if (!keyword) return content;
    return content.filter(r => (r.roles ?? '').toLowerCase().includes(keyword));
  }, [rolesPage.content, nameKeyword]);

  const fetchTenantId = async () => {
    try {
      const admin = await getCurrentAdmin();
      if (typeof admin?.tenantId === 'number') {
        setDefaultTenantId(admin.tenantId);
      }
    } catch (e) {
      // 不打断页面：tenantId 可能由后端默认/或用户手动输入
      console.warn('Failed to fetch current-admin for tenantId', e);
    }
  };

  useEffect(() => {
    fetchTenantId();
  }, []);

  const openCreateRole = () => {
    setCurrentRole(null);
    setRoleForm({
      ...emptyCreateForm(),
      tenantId: defaultTenantId,
    });
    setPermissions([]);
    setRolePermissions([]);
    setRoleModalOpen(true);
  };

  const openEditRole = async (role: RoleDTO) => {
    const normalizedRole = normalizeRole(role);

    // 如果列表数据没有 id（历史数据/后端刚修），主动补拉详情
    const roleToEdit = normalizedRole;
    const roleId = parseRoleId(normalizedRole);
    if (roleId == null) {
      console.warn('Role from list has no id, fallback to fetch details', role);
      try {
        // 这里无法推断 id，只能直接提示用户刷新或从后端修复数据
        // 但保留弹窗可编辑体验
      } catch {
        // ignore
      }
    }

    setCurrentRole(roleToEdit);
    setRoleForm({
      tenantId: roleToEdit.tenantId ?? defaultTenantId,
      roles: roleToEdit.roles,
      canLogin: roleToEdit.canLogin,
      canViewAnnouncement: roleToEdit.canViewAnnouncement,
      canViewHelpArticles: roleToEdit.canViewHelpArticles,
      canResetOwnPassword: roleToEdit.canResetOwnPassword,
      canComment: roleToEdit.canComment,
      notes: roleToEdit.notes ?? '',
    });

    setRoleModalOpen(true);

    // 加载权限列表 + 当前角色权限关系
    if (typeof roleId === 'number') {
      setMatrixLoading(true);
      try {
        const [permsPage, rolePerms] = await Promise.all([
          queryPermissions({ pageNum: 1, pageSize: 200 }),
          listRolePermissionsByRole(roleId),
        ]);
        setPermissions(permsPage.content ?? []);
        setRolePermissions(rolePerms as RolePermissionEntityLike[]);
      } catch (e) {
        console.error(e);
        alert('加载角色权限矩阵失败');
      } finally {
        setMatrixLoading(false);
      }
    }
  };

  const handleDeleteRole = async (id?: number | string) => {
    const roleId = Number(id);
    if (!Number.isFinite(roleId)) {
      console.warn('Delete role aborted: invalid id', id);
      alert('删除失败：角色 ID 缺失或非法');
      return;
    }
    if (!window.confirm('确定要删除该角色吗？')) return;
    try {
      await rolesService.deleteRole(roleId);
      await fetchRoles();
    } catch (e) {
      console.error(e);
      alert('删除角色失败');
    }
  };

  const handleSubmitRole = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      // 兜底：提交前再尝试补齐 tenantId（避免用户打开页面后 tenantId 延迟加载）
      let payload: RoleCreateDTO = roleForm;
      if (payload.tenantId == null) {
        try {
          const admin = await getCurrentAdmin();
          if (typeof admin?.tenantId === 'number') {
            payload = { ...payload, tenantId: admin.tenantId };
            setRoleForm(payload);
          }
        } catch {
          // ignore
        }
      }

      if (payload.tenantId == null) {
        alert('tenantId 不能为空（无法从当前登录用户推断，请手动填写 tenantId）');
        return;
      }

      const editingId = parseRoleId(currentRole);
      if (typeof editingId === 'number') {
        await rolesService.updateRole(editingId, payload);
      } else {
        await rolesService.createRole(payload);
      }
      setRoleModalOpen(false);
      await fetchRoles();
    } catch (e) {
      console.error(e);
      alert('保存角色失败');
    }
  };

  const togglePermissionAllow = async (permissionId: number, currentlyAllowed: boolean) => {
    const roleId = parseRoleId(currentRole);
    if (!roleId) {
      alert('请先保存角色，再配置权限');
      return;
    }

    try {
      if (currentlyAllowed) {
        await deleteRolePermission(roleId, permissionId);
      } else {
        await upsertRolePermission({ roleId, permissionId, allow: true });
      }

      // 刷新当前角色的权限关系
      const updated = await listRolePermissionsByRole(roleId);
      setRolePermissions(updated as RolePermissionEntityLike[]);
    } catch (e) {
      console.error(e);
      alert('更新角色权限失败');
    }
  };

  return (
    <div className="space-y-4 p-4 bg-white rounded-lg shadow">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-bold">角色管理</h2>
        <Button onClick={openCreateRole}>新增角色</Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Input
          placeholder="角色名称关键字（本地过滤）"
          value={nameKeyword}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNameKeyword(e.target.value)}
        />
        <Button onClick={handleSearch} variant="secondary">
          搜索
        </Button>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">角色</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">备注</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan={4} className="text-center py-4">
                  加载中...
                </td>
              </tr>
            ) : filteredRoles.length === 0 ? (
              <tr>
                <td colSpan={4} className="text-center py-4">
                  暂无数据
                </td>
              </tr>
            ) : (
              filteredRoles.map(r => (
                <tr key={parseRoleId(r) ?? r.roles}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{parseRoleId(r) ?? '—'}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{r.roles}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{r.notes ?? '—'}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-2">
                    <Button size="sm" variant="outline" onClick={() => openEditRole(r)}>
                      编辑/配置权限
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      className="bg-red-100 text-red-600 hover:bg-red-200"
                      onClick={() => handleDeleteRole(parseRoleId(r) ?? r.id ?? r.roleId)}
                    >
                      删除
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex justify-between items-center mt-4">
        <div className="text-sm text-gray-500">共 {rolesPage.totalElements} 条记录</div>
        <div className="space-x-2">
          <Button
            variant="outline"
            size="sm"
            disabled={pageNum === 1}
            onClick={() => setPageNum(p => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          <span className="text-sm text-gray-600">第 {pageNum} 页</span>
          <Button
            variant="outline"
            size="sm"
            disabled={rolesPage.totalPages ? pageNum >= rolesPage.totalPages : filteredRoles.length < pageSize}
            onClick={() => setPageNum(p => p + 1)}
          >
            下一页
          </Button>
        </div>
      </div>

      <Modal
        isOpen={isRoleModalOpen}
        onClose={() => setRoleModalOpen(false)}
        title={currentRole ? `编辑角色：${currentRole.roles}` : '新增角色'}
      >
        <form onSubmit={handleSubmitRole} className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <Label>角色名称</Label>
              <Input
                required
                value={roleForm.roles}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setRoleForm({ ...roleForm, roles: e.target.value })}
                placeholder="例如：ADMIN"
              />
            </div>

            <div>
              <Label>tenantId</Label>
              <Input
                value={roleForm.tenantId ?? ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  const raw = e.target.value.trim();
                  setRoleForm({
                    ...roleForm,
                    tenantId: raw === '' ? undefined : Number(raw),
                  });
                }}
                placeholder={defaultTenantId != null ? `默认：${defaultTenantId}` : '例如：1'}
                inputMode="numeric"
              />
              <div className="mt-1 text-xs text-gray-500">
                新建角色时后端要求 tenantId；通常会从当前登录管理员自动推断。
              </div>
            </div>

            <div>
              <Label>备注</Label>
              <Input
                value={roleForm.notes ?? ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setRoleForm({ ...roleForm, notes: e.target.value })}
                placeholder="可选"
              />
            </div>

            <div className="flex items-center space-x-2">
              <Checkbox
                checked={!!roleForm.canLogin}
                onCheckedChange={(v) => setRoleForm({ ...roleForm, canLogin: Boolean(v) })}
              />
              <Label>可登录</Label>
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                checked={!!roleForm.canComment}
                onCheckedChange={(v) => setRoleForm({ ...roleForm, canComment: Boolean(v) })}
              />
              <Label>可评论</Label>
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                checked={!!roleForm.canViewAnnouncement}
                onCheckedChange={(v) => setRoleForm({ ...roleForm, canViewAnnouncement: Boolean(v) })}
              />
              <Label>可查看公告</Label>
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                checked={!!roleForm.canViewHelpArticles}
                onCheckedChange={(v) => setRoleForm({ ...roleForm, canViewHelpArticles: Boolean(v) })}
              />
              <Label>可查看帮助文章</Label>
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                checked={!!roleForm.canResetOwnPassword}
                onCheckedChange={(v) => setRoleForm({ ...roleForm, canResetOwnPassword: Boolean(v) })}
              />
              <Label>可重置自己的密码</Label>
            </div>
          </div>

          {/* 角色-权限矩阵 */}
          {typeof parseRoleId(currentRole) === 'number' ? (
            <div className="border rounded-md p-4">
              <div className="flex items-center justify-between mb-3">
                <div>
                  <div className="text-base font-semibold">角色权限</div>
                  <div className="text-sm text-gray-500">
                    勾选表示 allow=true；取消勾选会删除 role-permission 记录。
                  </div>
                </div>
              </div>

              {matrixLoading ? (
                <div className="text-sm text-gray-500">加载中...</div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2 max-h-64 overflow-auto pr-2">
                  {permissions.map(p => {
                    const key = `${p.id}:${p.resource}:${p.action}`;
                    const allowed = rolePermAllowSet.has(p.id);
                    return (
                      <label key={key} className="flex items-start gap-2 p-2 rounded hover:bg-gray-50">
                        <Checkbox checked={allowed} onCheckedChange={() => togglePermissionAllow(p.id, allowed)} />
                        <span className="text-sm">
                          <span className="font-medium">{p.resource}</span>
                          <span className="text-gray-500"> / {p.action}</span>
                          {p.description ? <span className="text-gray-400">（{p.description}）</span> : null}
                        </span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
          ) : (
            <div className="text-sm text-gray-500">新角色需要先保存后才能配置权限。</div>
          )}

          <div className="flex justify-end space-x-2 pt-2">
            <Button type="button" variant="ghost" onClick={() => setRoleModalOpen(false)}>
              取消
            </Button>
            <Button type="submit">保存</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default RolesManagement;

