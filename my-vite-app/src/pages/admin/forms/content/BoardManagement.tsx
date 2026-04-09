import React, { useState, useEffect, useMemo } from 'react';
import {
  BoardAccessControlDTO,
  BoardCreateDTO,
  BoardDTO,
  BoardQueryDTO,
  BoardUpdateDTO,
  adminCreateBoard,
  adminDeleteBoard,
  adminListBoards,
  adminSearchBoards,
  adminUpdateBoard,
  getBoardAccessControl,
  updateBoardAccessControl,
} from '../../../../services/boardService';
import { listRoleSummaries, type RoleSummaryDTO } from '../../../../services/rolePermissionsService';
import { userAccessService } from '../../../../services/userAccessService';
import type { UserDTO } from '../../../../types/userAccess';

const BoardManagement: React.FC = () => {
  // 表单状态
  const [createForm, setCreateForm] = useState<BoardCreateDTO>({
    tenantId: undefined,
    parentId: undefined,
    name: '',
    description: '',
    visible: true,
    sortOrder: 0
  });

  // 搜索表单状态
  const [searchForm, setSearchForm] = useState<BoardQueryDTO>({
    page: 1,
    pageSize: 10,
    sortBy: 'sortOrder',
    sortOrderDirection: 'asc'
  });

  // 编辑表单状态
  const [editForm, setEditForm] = useState<BoardDTO | null>(null);

  // 数据状态
  const [boards, setBoards] = useState<BoardDTO[]>([]);
  const [filteredBoards, setFilteredBoards] = useState<BoardDTO[]>([]);
  
  // UI状态
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  const [roleSummaries, setRoleSummaries] = useState<RoleSummaryDTO[]>([]);
  const [rolesLoading, setRolesLoading] = useState(false);

  const [aclLoading, setAclLoading] = useState(false);
  const [aclSaving, setAclSaving] = useState(false);
  const [aclForm, setAclForm] = useState<Omit<BoardAccessControlDTO, 'boardId'>>({
    viewRoleIds: [],
    postRoleIds: [],
    moderatorUserIds: [],
  });
  const [moderatorUsersById, setModeratorUsersById] = useState<Record<number, UserDTO>>({});
  const [moderatorKeyword, setModeratorKeyword] = useState('');
  const [moderatorSearching, setModeratorSearching] = useState(false);
  const [moderatorSearchResults, setModeratorSearchResults] = useState<UserDTO[]>([]);
  const [viewRoleToAdd, setViewRoleToAdd] = useState('');
  const [postRoleToAdd, setPostRoleToAdd] = useState('');

  // 初始化数据
  useEffect(() => {
    loadBoards();
    loadRoleSummaries();
  }, []);

  const loadBoards = async () => {
    try {
      setLoading(true);
      const data = await adminListBoards();
      setBoards(data);
      setFilteredBoards(data);
      setMessage(null);
    } catch (err: any) {
      setMessage(err?.message || '加载数据失败');
    } finally {
      setLoading(false);
    }
  };

  const loadRoleSummaries = async () => {
    try {
      setRolesLoading(true);
      const list = await listRoleSummaries();
      const sorted = (list ?? []).slice().sort((a, b) => (a.roleId ?? 0) - (b.roleId ?? 0));
      setRoleSummaries(sorted);
    } catch (err: any) {
      setMessage(err?.message || '加载角色列表失败');
    } finally {
      setRolesLoading(false);
    }
  };

  // 创建表单处理
  const handleCreateChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target as HTMLInputElement;
    
    if (type === 'checkbox') {
      const checked = (e.target as HTMLInputElement).checked;
      setCreateForm(prev => ({
        ...prev,
        [name]: checked
      }));
    } else if (type === 'number') {
      setCreateForm(prev => ({
        ...prev,
        [name]: value === '' ? undefined : Number(value)
      }));
    } else {
      setCreateForm(prev => ({
        ...prev,
        [name]: value
      }));
    }
    
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: '' }));
    }
  };

  // 搜索表单处理
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target as HTMLInputElement;
    
    if (type === 'number') {
      setSearchForm(prev => ({
        ...prev,
        [name]: value === '' ? undefined : Number(value)
      }));
    } else if (type === 'checkbox') {
      const checked = (e.target as HTMLInputElement).checked;
      setSearchForm(prev => ({
        ...prev,
        [name]: checked
      }));
    } else if (name === 'visible') {
      const trimmed = value.trim().toLowerCase();
      setSearchForm(prev => ({
        ...prev,
        visible: trimmed === '' ? undefined : trimmed === 'true'
      }));
    } else {
      setSearchForm(prev => ({
        ...prev,
        [name]: value
      }));
    }
  };

  // 编辑表单处理
  const handleEditChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    if (!editForm) return;
    
    const { name, value, type } = e.target as HTMLInputElement;
    
    if (type === 'checkbox') {
      const checked = (e.target as HTMLInputElement).checked;
      setEditForm(prev => prev ? {
        ...prev,
        [name]: checked
      } : null);
    } else if (type === 'number') {
      setEditForm(prev => prev ? {
        ...prev,
        [name]: value === '' ? undefined : Number(value)
      } : null);
    } else {
      setEditForm(prev => prev ? {
        ...prev,
        [name]: value
      } : null);
    }
  };

  // 验证创建表单
  const validateCreateForm = (data: BoardCreateDTO) => {
    const errors: Record<string, string> = {};
    if (data.name == null || data.name.trim() === '') {
      errors.name = '名称是必填项';
    } else if (data.name.length > 64) {
      errors.name = '名称不能超过64个字符';
    }
    if (data.description && data.description.length > 255) {
      errors.description = '描述不能超过255个字符';
    }
    if (data.sortOrder != null && !Number.isInteger(data.sortOrder)) {
      errors.sortOrder = '排序值必须是整数';
    }
    return errors;
  };

  // 提交创建表单
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    const formErrors = validateCreateForm(createForm);
    setErrors(formErrors);
    
    if (Object.keys(formErrors).length > 0) {
      return;
    }
    
    try {
      setLoading(true);
      const created = await adminCreateBoard(createForm);
      setBoards(prev => [...prev, created]);
      setFilteredBoards(prev => [...prev, created]);
      const shouldSaveAcl =
        (aclForm.viewRoleIds?.length ?? 0) > 0 ||
        (aclForm.postRoleIds?.length ?? 0) > 0 ||
        (aclForm.moderatorUserIds?.length ?? 0) > 0;
      if (shouldSaveAcl) {
        setAclSaving(true);
        try {
          await updateBoardAccessControl(created.id, aclForm);
          setMessage(`版块 "${created.name}" 创建成功，权限已保存`);
          setCreateForm({
            tenantId: undefined,
            parentId: undefined,
            name: '',
            description: '',
            visible: true,
            sortOrder: 0
          });
          resetAcl();
          return;
        } catch (err: any) {
          setEditForm(created);
          setMessage(`版块 "${created.name}" 创建成功，但权限保存失败：${err?.message || '未知错误'}`);
          return;
        } finally {
          setAclSaving(false);
        }
      }

      setMessage(`版块 "${created.name}" 创建成功`);
      setCreateForm({
        tenantId: undefined,
        parentId: undefined,
        name: '',
        description: '',
        visible: true,
        sortOrder: 0
      });
      resetAcl();
    } catch (err: any) {
      const fieldErrors = err?.fieldErrors as Record<string, string> | undefined;
      if (fieldErrors) {
        setErrors(fieldErrors);
      }
      setMessage(err?.message || '创建失败');
    } finally {
      setLoading(false);
    }
  };

  // 执行搜索
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setLoading(true);
      const result = await adminSearchBoards(searchForm);
      setFilteredBoards(result);
      setMessage(`找到 ${result.length} 个版块`);
    } catch (err: any) {
      setMessage(err?.message || '搜索失败');
    } finally {
      setLoading(false);
    }
  };

  // 重置搜索
  const resetSearch = () => {
    setSearchForm({
      page: 1,
      pageSize: 10,
      sortBy: 'sortOrder',
      sortOrderDirection: 'asc'
    });
    setFilteredBoards(boards);
    setMessage(null);
  };

  const resetAcl = () => {
    setAclForm({ viewRoleIds: [], postRoleIds: [], moderatorUserIds: [] });
    setModeratorUsersById({});
    setModeratorKeyword('');
    setModeratorSearchResults([]);
    setViewRoleToAdd('');
    setPostRoleToAdd('');
  };

  const loadAclForBoard = async (boardId: number) => {
    try {
      setAclLoading(true);
      const acl = await getBoardAccessControl(boardId);
      setAclForm({
        viewRoleIds: acl.viewRoleIds ?? [],
        postRoleIds: acl.postRoleIds ?? [],
        moderatorUserIds: acl.moderatorUserIds ?? [],
      });

      const ids = (acl.moderatorUserIds ?? []).filter((x) => typeof x === 'number' && Number.isFinite(x));
      if (ids.length === 0) {
        setModeratorUsersById({});
        return;
      }

      const settled = await Promise.allSettled(ids.map((id) => userAccessService.getUserById(id)));
      const next: Record<number, UserDTO> = {};
      for (const s of settled) {
        if (s.status !== 'fulfilled') continue;
        const u = s.value;
        if (!u || typeof u.id !== 'number') continue;
        next[u.id] = u;
      }
      setModeratorUsersById(next);
    } catch (err: any) {
      setMessage(err?.message || '加载版块权限失败');
      resetAcl();
    } finally {
      setAclLoading(false);
    }
  };

  const saveAcl = async () => {
    if (!editForm) return;
    try {
      setAclSaving(true);
      const saved = await updateBoardAccessControl(editForm.id, aclForm);
      setAclForm({
        viewRoleIds: saved.viewRoleIds ?? [],
        postRoleIds: saved.postRoleIds ?? [],
        moderatorUserIds: saved.moderatorUserIds ?? [],
      });
      setMessage('版块权限已保存');
    } catch (err: any) {
      setMessage(err?.message || '保存版块权限失败');
    } finally {
      setAclSaving(false);
    }
  };

  const searchModerators = async () => {
    const kw = moderatorKeyword.trim();
    if (!kw) {
      setModeratorSearchResults([]);
      return;
    }
    try {
      setModeratorSearching(true);
      // 后端 queryUsers 对多个字段是 AND 关系，分开查询再合并可实现“用户名或邮箱”搜索。
      const [byUsername, byEmail] = await Promise.all([
        userAccessService.queryUsers({
          pageNum: 1,
          pageSize: 10,
          username: kw,
          includeDeleted: false,
        }),
        userAccessService.queryUsers({
          pageNum: 1,
          pageSize: 10,
          email: kw,
          includeDeleted: false,
        }),
      ]);

      const merged: UserDTO[] = [];
      const seen = new Set<number>();
      for (const u of [...(byUsername?.content ?? []), ...(byEmail?.content ?? [])]) {
        if (!u || typeof u.id !== 'number') continue;
        if (seen.has(u.id)) continue;
        seen.add(u.id);
        merged.push(u);
      }
      setModeratorSearchResults(merged);
    } catch (err: any) {
      setMessage(err?.message || '搜索用户失败');
    } finally {
      setModeratorSearching(false);
    }
  };

  const addModerator = (user: UserDTO) => {
    if (!user || typeof user.id !== 'number') return;
    setAclForm((prev) => {
      const next = new Set<number>(prev.moderatorUserIds ?? []);
      next.add(user.id);
      return { ...prev, moderatorUserIds: Array.from(next) };
    });
    setModeratorUsersById((prev) => ({ ...prev, [user.id]: user }));
  };

  const removeModerator = (userId: number) => {
    setAclForm((prev) => ({
      ...prev,
      moderatorUserIds: (prev.moderatorUserIds ?? []).filter((id) => id !== userId),
    }));
    setModeratorUsersById((prev) => {
      const next = { ...prev };
      delete next[userId];
      return next;
    });
  };

  const setAclRoleIds = (key: 'viewRoleIds' | 'postRoleIds', ids: number[]) => {
    const uniq = Array.from(new Set(ids.filter((x) => Number.isFinite(x) && x > 0))).sort((a, b) => a - b);
    setAclForm((prev) => ({ ...prev, [key]: uniq }));
  };

  const addAclRoleIds = (key: 'viewRoleIds' | 'postRoleIds', ids: number[]) => {
    setAclForm((prev) => {
      const next = new Set<number>((prev[key] ?? []).filter((x) => Number.isFinite(x) && x > 0));
      for (const id of ids) {
        if (!Number.isFinite(id) || id <= 0) continue;
        next.add(id);
      }
      return { ...prev, [key]: Array.from(next).sort((a, b) => a - b) };
    });
  };

  const removeAclRoleId = (key: 'viewRoleIds' | 'postRoleIds', roleId: number) => {
    setAclForm((prev) => ({
      ...prev,
      [key]: (prev[key] ?? []).filter((id) => id !== roleId),
    }));
  };

  const roleSummaryById = useMemo(() => {
    const next: Record<number, RoleSummaryDTO> = {};
    for (const r of roleSummaries) {
      if (typeof r.roleId !== 'number') continue;
      next[r.roleId] = r;
    }
    return next;
  }, [roleSummaries]);

  const getRoleLabel = (roleId: number) => {
    const r = roleSummaryById[roleId];
    if (!r) return `roleId=${roleId}`;
    return r.roleName ? `${r.roleName} (roleId=${r.roleId})` : `roleId=${r.roleId}`;
  };

  // 开始编辑
  const startEdit = (board: BoardDTO) => {
    setEditForm({...board});
    setErrors({});
    setMessage(null);
    resetAcl();
    loadAclForBoard(board.id);
  };

  // 取消编辑
  const cancelEdit = () => {
    setEditForm(null);
    resetAcl();
  };

  // 保存编辑
  const saveEdit = async () => {
    if (!editForm) return;
    
    try {
      setLoading(true);
      // 准备更新数据
      const updateData: BoardUpdateDTO = {
        id: editForm.id,
        tenantId: editForm.tenantId,
        parentId: editForm.parentId,
        name: editForm.name,
        description: editForm.description,
        visible: editForm.visible,
        sortOrder: editForm.sortOrder
      };
      
      const updated = await adminUpdateBoard(updateData);
      
      // 更新数据
      const updatedBoards = boards.map(board => 
        board.id === updated.id ? updated : board
      );
      
      setBoards(updatedBoards);
      setFilteredBoards(updatedBoards);
      setEditForm(null);
      resetAcl();
      setMessage('版块更新成功');
    } catch (err: any) {
      const fieldErrors = err?.fieldErrors as Record<string, string> | undefined;
      if (fieldErrors) {
        setErrors(fieldErrors);
      }
      setMessage(err?.message || '更新失败');
    } finally {
      setLoading(false);
    }
  };

  // 删除版块
  const handleDeleteBoard = async (id: number) => {
    if (window.confirm('确定要删除这个版块吗？')) {
      try {
        setLoading(true);
        await adminDeleteBoard(id);
        
        const updatedBoards = boards.filter(board => board.id !== id);
        setBoards(updatedBoards);
        setFilteredBoards(updatedBoards);
        setMessage('版块删除成功');
      } catch (err: any) {
        setMessage(err?.message || '删除失败');
      } finally {
        setLoading(false);
      }
    }
  };

  const viewRoleIdSet = new Set<number>(aclForm.viewRoleIds ?? []);
  const postRoleIdSet = new Set<number>(aclForm.postRoleIds ?? []);
  const availableViewRoleSummaries = roleSummaries.filter((r) => typeof r.roleId === 'number' && !viewRoleIdSet.has(r.roleId));
  const availablePostRoleSummaries = roleSummaries.filter((r) => typeof r.roleId === 'number' && !postRoleIdSet.has(r.roleId));

  return (
    <div className="space-y-4">
      {message && (
        <div className={`p-3 rounded ${message.includes('失败') ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'}`}>
          {message}
        </div>
      )}
      
      {/* 创建/编辑表单 - 始终显示在顶部 */}
      <div className="bg-white rounded-lg shadow p-4 space-y-4">
        <div className="space-y-3">
          <h2 className="text-2xl font-bold text-gray-800">版块管理</h2>
          <h3 className="text-base font-bold font-medium text-gray-900">
            {editForm ? '编辑版块' : '创建版块'}
          </h3>
          <form
            id="create-board-form"
            onSubmit={editForm ? (e) => { e.preventDefault(); saveEdit(); } : handleCreateSubmit}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end"
          >
          <div className="lg:col-span-2">
            <label className="block text-xs font-medium text-gray-700 mb-0.5">租户ID（可选）</label>
            <input
              name="tenantId"
              type="number"
              value={editForm ? editForm.tenantId || '' : createForm.tenantId || ''}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-1.5 text-sm ${errors.tenantId ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="例如：1"
            />
            {errors.tenantId && <p className="text-xs text-red-600 mt-1">{errors.tenantId}</p>}
          </div>
          
          <div className="lg:col-span-2">
            <label className="block text-xs font-medium text-gray-700 mb-0.5">父级版块ID（可选）</label>
            <input
              name="parentId"
              type="number"
              value={editForm ? editForm.parentId || '' : createForm.parentId || ''}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-1.5 text-sm ${errors.parentId ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="例如：上级版块ID"
            />
            {errors.parentId && <p className="text-xs text-red-600 mt-1">{errors.parentId}</p>}
          </div>
          
          <div className="lg:col-span-2">
            <label className="block text-xs font-medium text-gray-700 mb-0.5">排序权重</label>
            <input
              name="sortOrder"
              type="number"
              value={editForm ? editForm.sortOrder : createForm.sortOrder}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-1.5 text-sm ${errors.sortOrder ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="默认 0，值越小越靠前"
            />
            {errors.sortOrder && <p className="text-xs text-red-600 mt-1">{errors.sortOrder}</p>}
          </div>
          
          <div className="sm:col-span-2 lg:col-span-4">
            <label className="block text-xs font-medium text-gray-700 mb-0.5">名称 *</label>
            <input
              name="name"
              value={editForm ? editForm.name : createForm.name}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-1.5 text-sm ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="版块名称"
              maxLength={64}
            />
            <p className="text-[11px] text-gray-400 mt-1">不超过 64 个字符</p>
            {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
          </div>
          
          <div className="lg:col-span-2 flex items-center">
            <label className="inline-flex items-center gap-2 text-xs font-medium text-gray-700">
              <input
                type="checkbox"
                name="visible"
                checked={editForm ? editForm.visible : createForm.visible}
                onChange={editForm ? handleEditChange : handleCreateChange}
                className="rounded border-gray-300"
              />
              是否可见
            </label>
          </div>
          
          <div className="sm:col-span-2 lg:col-span-6">
            <label className="block text-xs font-medium text-gray-700 mb-0.5">描述</label>
            <textarea
              name="description"
              value={editForm ? editForm.description || '' : createForm.description || ''}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-1.5 text-sm ${errors.description ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="版块描述"
              maxLength={255}
              rows={2}
            />
            {errors.description && <p className="text-xs text-red-600 mt-1">{errors.description}</p>}
          </div>
          
          {editForm ? (
            <div className="sm:col-span-2 lg:col-span-6 flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={cancelEdit}
                className="rounded border border-gray-300 px-4 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
                disabled={loading}
              >
                取消
              </button>
              <button
                type="submit"
                className="rounded bg-blue-600 text-white px-4 py-1.5 text-sm disabled:bg-blue-300"
                disabled={loading}
              >
                {loading ? '保存中...' : '保存'}
              </button>
            </div>
          ) : null}
          </form>
        </div>

        <div className="space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div className="space-y-0.5">
            <h3 className="text-base font-medium text-gray-900">权限配置</h3>
          </div>
          <div className="flex gap-2">
            {editForm ? (
              <>
                <button
                  type="button"
                  onClick={() => loadAclForBoard(editForm.id)}
                  className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                  disabled={aclLoading || aclSaving}
                >
                  {aclLoading ? '加载中...' : '刷新'}
                </button>
                <button
                  type="button"
                  onClick={saveAcl}
                  className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:bg-blue-300"
                  disabled={aclLoading || aclSaving}
                >
                  {aclSaving ? '保存中...' : '保存权限'}
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={resetAcl}
                className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                disabled={rolesLoading || loading || aclSaving}
              >
                重置权限
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="block text-sm font-medium text-gray-700">可访问角色</label>
              <span className="text-xs text-gray-500">{aclForm.viewRoleIds.length} 个</span>
            </div>
            <div className="text-xs text-gray-500">为空表示所有人可访问</div>
            {aclForm.viewRoleIds.length === 0 ? (
              <div className="text-sm text-gray-500">未限制</div>
            ) : (
              <div className="flex flex-wrap gap-2">
                {aclForm.viewRoleIds.map((id) => (
                  <span key={id} className="inline-flex items-center gap-1.5 rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-700">
                    {getRoleLabel(id)}
                    <button
                      type="button"
                      onClick={() => removeAclRoleId('viewRoleIds', id)}
                      className="text-gray-500 hover:text-gray-800"
                      disabled={rolesLoading || loading || aclLoading || aclSaving}
                      aria-label="remove"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
            <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-2 items-center">
              <select
                value={viewRoleToAdd}
                onChange={(e) => setViewRoleToAdd(e.target.value)}
                className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm"
                disabled={rolesLoading || loading || aclLoading || aclSaving}
              >
                <option value="">{rolesLoading ? '加载角色中...' : '选择要添加的角色'}</option>
                {availableViewRoleSummaries.map((r) => (
                  <option key={r.roleId} value={String(r.roleId)}>
                    {r.roleName ? `${r.roleName} (roleId=${r.roleId})` : `roleId=${r.roleId}`}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={() => {
                  const id = Number(viewRoleToAdd);
                  if (Number.isFinite(id) && id > 0) addAclRoleIds('viewRoleIds', [id]);
                  setViewRoleToAdd('');
                }}
                className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white disabled:bg-blue-300"
                disabled={!viewRoleToAdd || rolesLoading || loading || aclLoading || aclSaving}
              >
                添加
              </button>
            </div>
            <button
              type="button"
              onClick={() => setAclRoleIds('viewRoleIds', [])}
              className="text-sm text-gray-600 hover:text-gray-900 disabled:opacity-60"
              disabled={rolesLoading || loading || aclLoading || aclSaving}
            >
              清空
            </button>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="block text-sm font-medium text-gray-700">可发帖角色</label>
              <span className="text-xs text-gray-500">{aclForm.postRoleIds.length} 个</span>
            </div>
            <div className="text-xs text-gray-500">为空表示所有登录用户可发帖</div>
            {aclForm.postRoleIds.length === 0 ? (
              <div className="text-sm text-gray-500">未限制</div>
            ) : (
              <div className="flex flex-wrap gap-2">
                {aclForm.postRoleIds.map((id) => (
                  <span key={id} className="inline-flex items-center gap-1.5 rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-700">
                    {getRoleLabel(id)}
                    <button
                      type="button"
                      onClick={() => removeAclRoleId('postRoleIds', id)}
                      className="text-gray-500 hover:text-gray-800"
                      disabled={rolesLoading || loading || aclLoading || aclSaving}
                      aria-label="remove"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
            <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-2 items-center">
              <select
                value={postRoleToAdd}
                onChange={(e) => setPostRoleToAdd(e.target.value)}
                className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm"
                disabled={rolesLoading || loading || aclLoading || aclSaving}
              >
                <option value="">{rolesLoading ? '加载角色中...' : '选择要添加的角色'}</option>
                {availablePostRoleSummaries.map((r) => (
                  <option key={r.roleId} value={String(r.roleId)}>
                    {r.roleName ? `${r.roleName} (roleId=${r.roleId})` : `roleId=${r.roleId}`}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={() => {
                  const id = Number(postRoleToAdd);
                  if (Number.isFinite(id) && id > 0) addAclRoleIds('postRoleIds', [id]);
                  setPostRoleToAdd('');
                }}
                className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white disabled:bg-blue-300"
                disabled={!postRoleToAdd || rolesLoading || loading || aclLoading || aclSaving}
              >
                添加
              </button>
            </div>
            <button
              type="button"
              onClick={() => setAclRoleIds('postRoleIds', [])}
              className="text-sm text-gray-600 hover:text-gray-900 disabled:opacity-60"
              disabled={rolesLoading || loading || aclLoading || aclSaving}
            >
              清空
            </button>
          </div>

          <div className="md:col-span-2 space-y-2">
            <div className="flex items-center justify-between">
              <label className="block text-sm font-medium text-gray-700">版主</label>
              <span className="text-xs text-gray-500">{aclForm.moderatorUserIds.length} 人</span>
            </div>

            {aclForm.moderatorUserIds.length === 0 ? (
              <div className="text-sm text-gray-500">未配置版主</div>
            ) : (
              <div className="flex flex-wrap gap-2">
                {aclForm.moderatorUserIds.map((id) => {
                  const u = moderatorUsersById[id];
                  const label = u ? `${u.username} (id=${u.id})` : `userId=${id}`;
                  return (
                    <span key={id} className="inline-flex items-center gap-1.5 rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-700">
                      {label}
                      <button
                        type="button"
                        onClick={() => removeModerator(id)}
                        className="text-gray-500 hover:text-gray-800"
                        disabled={loading || aclLoading || aclSaving}
                        aria-label="remove"
                      >
                        ×
                      </button>
                    </span>
                  );
                })}
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-2 items-center">
              <input
                value={moderatorKeyword}
                onChange={(e) => setModeratorKeyword(e.target.value)}
                className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm"
                placeholder="搜索用户（邮箱/用户名）"
                disabled={moderatorSearching || loading || aclLoading || aclSaving}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    searchModerators();
                  }
                }}
              />
              <button
                type="button"
                onClick={searchModerators}
                className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                disabled={moderatorSearching || loading || aclLoading || aclSaving}
              >
                {moderatorSearching ? '搜索中...' : '搜索用户'}
              </button>
            </div>

            {moderatorSearchResults.length > 0 ? (
              <div className="overflow-hidden rounded border border-gray-200">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">ID</th>
                      <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">用户名</th>
                      <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">邮箱</th>
                      <th className="px-3 py-2" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 bg-white">
                    {moderatorSearchResults.map((u) => (
                      <tr key={u.id}>
                        <td className="px-3 py-2 text-sm text-gray-700">{u.id}</td>
                        <td className="px-3 py-2 text-sm text-gray-700">{u.username}</td>
                        <td className="px-3 py-2 text-sm text-gray-500">{u.email}</td>
                        <td className="px-3 py-2 text-right">
                          <button
                            type="button"
                            onClick={() => addModerator(u)}
                            className="rounded bg-blue-600 px-2.5 py-1 text-xs text-white disabled:bg-blue-300"
                            disabled={(aclForm.moderatorUserIds ?? []).includes(u.id) || loading || aclLoading || aclSaving}
                          >
                            添加
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </div>
        </div>
      </div>

        {editForm ? null : (
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => {
                setCreateForm({
                  tenantId: undefined,
                  parentId: undefined,
                  name: '',
                  description: '',
                  visible: true,
                  sortOrder: 0
                });
                resetAcl();
                setErrors({});
                setMessage(null);
              }}
              className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-50"
              disabled={loading}
            >
              重置
            </button>
            <button
              type="submit"
              form="create-board-form"
              className="rounded bg-blue-600 text-white px-4 py-2 disabled:bg-blue-300"
              disabled={loading || aclSaving}
            >
              {loading || aclSaving ? '创建中...' : '创建'}
            </button>
          </div>
        )}
      </div>

      {/* 搜索表单 - 显示在中间 */}
      <div className="bg-white rounded-lg shadow p-4 space-y-4">
        <div className="space-y-3">
          <h3 className="text-base font-medium text-gray-900">搜索版块</h3>
          <form onSubmit={handleSearch} className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">版块ID</label>
            <input
              name="id"
              type="number"
              value={searchForm.id || ''}
              onChange={handleSearchChange}
              className="w-full rounded border border-gray-300 px-3 py-2"
              placeholder="版块ID"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">租户ID</label>
            <input
              name="tenantId"
              type="number"
              value={searchForm.tenantId || ''}
              onChange={handleSearchChange}
              className="w-full rounded border border-gray-300 px-3 py-2"
              placeholder="租户ID"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">名称</label>
            <input
              name="nameLike"
              value={searchForm.nameLike || ''}
              onChange={handleSearchChange}
              className="w-full rounded border border-gray-300 px-3 py-2"
              placeholder="版块名称关键词"
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">是否可见</label>
            <select
              name="visible"
              value={searchForm.visible === undefined ? '' : searchForm.visible.toString()}
              onChange={handleSearchChange}
              className="w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="">全部</option>
              <option value="true">可见</option>
              <option value="false">不可见</option>
            </select>
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">排序字段</label>
            <select
              name="sortBy"
              value={searchForm.sortBy || 'sortOrder'}
              onChange={handleSearchChange}
              className="w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="sortOrder">排序权重</option>
              <option value="name">名称</option>
              <option value="createdAt">创建时间</option>
            </select>
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">排序方向</label>
            <select
              name="sortOrderDirection"
              value={searchForm.sortOrderDirection || 'asc'}
              onChange={handleSearchChange}
              className="w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="asc">升序</option>
              <option value="desc">降序</option>
            </select>
          </div>
          
          <div className="md:col-span-2 flex justify-end gap-3 pt-3">
            <button
              type="button"
              onClick={resetSearch}
              className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              重置
            </button>
            <button
              type="submit"
              className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm"
            >
              搜索
            </button>
          </div>
        </form>
        </div>
      </div>
      
      {/* 版块列表 - 显示在底部，页面加载时默认显示全部板块 */}
      <div className="bg-white rounded-lg shadow p-4 space-y-4">
        <div className="space-y-3">
          <div className="flex justify-between items-center">
            <h3 className="text-base font-medium text-gray-900">版块列表</h3>
            <button
              onClick={loadBoards}
              className="rounded bg-gray-100 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-200"
            >
              刷新
            </button>
          </div>
        
        {loading ? (
          <div className="text-center py-8">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500"></div>
            <p className="mt-2 text-gray-500">加载中...</p>
          </div>
        ) : filteredBoards.length === 0 ? (
          <div className="text-center py-8">
            <p className="text-gray-500">暂无版块数据</p>
          </div>
        ) : (
          <div className="overflow-hidden shadow ring-1 ring-black ring-opacity-5 rounded-lg">
            <table className="min-w-full divide-y divide-gray-300">
              <thead className="bg-gray-50">
                <tr>
                  <th scope="col" className="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">
                    ID
                  </th>
                  <th scope="col" className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                    名称
                  </th>
                  <th scope="col" className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                    描述
                  </th>
                  <th scope="col" className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                    可见性
                  </th>
                  <th scope="col" className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                    排序
                  </th>
                  <th scope="col" className="relative py-3.5 pl-3 pr-4 sm:pr-6">
                    <span className="sr-only">操作</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {filteredBoards.map((board) => (
                  <tr key={board.id}>
                    <td className="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">
                      {board.id}
                    </td>
                    <td className="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                      {board.name}
                    </td>
                    <td className="px-3 py-4 text-sm text-gray-500 max-w-xs truncate">
                      {board.description}
                    </td>
                    <td className="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                      {board.visible ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                          可见
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                          不可见
                        </span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                      {board.sortOrder}
                    </td>
                    <td className="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                      <button
                        onClick={() => startEdit(board)}
                        className="text-blue-600 hover:text-blue-900 mr-3"
                      >
                        编辑
                      </button>
                      <button
                        onClick={() => handleDeleteBoard(board.id)}
                        className="text-red-600 hover:text-red-900"
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        </div>
      </div>
    </div>
  );
};

export default BoardManagement;
