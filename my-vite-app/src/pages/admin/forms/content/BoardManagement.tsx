import React, { useState, useEffect } from 'react';
import { BoardDTO, BoardCreateDTO, BoardUpdateDTO, BoardQueryDTO, createBoard, listBoards, updateBoard, deleteBoard, searchBoards } from '../../../../services/boardService';

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

  // 初始化数据
  useEffect(() => {
    loadBoards();
  }, []);

  const loadBoards = async () => {
    try {
      setLoading(true);
      const data = await listBoards();
      setBoards(data);
      setFilteredBoards(data);
      setMessage(null);
    } catch (err: any) {
      setMessage(err?.message || '加载数据失败');
    } finally {
      setLoading(false);
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
      const created = await createBoard(createForm);
      setBoards(prev => [...prev, created]);
      setFilteredBoards(prev => [...prev, created]);
      setMessage(`版块 "${created.name}" 创建成功`);
      setCreateForm({
        tenantId: undefined,
        parentId: undefined,
        name: '',
        description: '',
        visible: true,
        sortOrder: 0
      });
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
      const result = await searchBoards(searchForm);
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

  // 开始编辑
  const startEdit = (board: BoardDTO) => {
    setEditForm({...board});
  };

  // 取消编辑
  const cancelEdit = () => {
    setEditForm(null);
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
      
      const updated = await updateBoard(updateData);
      
      // 更新数据
      const updatedBoards = boards.map(board => 
        board.id === updated.id ? updated : board
      );
      
      setBoards(updatedBoards);
      setFilteredBoards(updatedBoards);
      setEditForm(null);
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
        await deleteBoard(id);
        
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

  return (
    <div className="bg-white rounded-lg shadow p-6 space-y-6">
      <h2 className="text-2xl font-bold text-gray-800">版块管理</h2>
      
      {message && (
        <div className={`p-3 rounded ${message.includes('失败') ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'}`}>
          {message}
        </div>
      )}
      
      {/* 创建/编辑表单 - 始终显示在顶部 */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-gray-900">
          {editForm ? '编辑版块' : '创建版块'}
        </h3>
        <form onSubmit={editForm ? (e) => { e.preventDefault(); saveEdit(); } : handleCreateSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">租户ID（可选）</label>
            <input
              name="tenantId"
              type="number"
              value={editForm ? editForm.tenantId || '' : createForm.tenantId || ''}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-2 ${errors.tenantId ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="例如：1"
            />
            {errors.tenantId && <p className="text-xs text-red-600 mt-1">{errors.tenantId}</p>}
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">父级版块ID（可选）</label>
            <input
              name="parentId"
              type="number"
              value={editForm ? editForm.parentId || '' : createForm.parentId || ''}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-2 ${errors.parentId ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="例如：上级版块ID"
            />
            {errors.parentId && <p className="text-xs text-red-600 mt-1">{errors.parentId}</p>}
          </div>
          
          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">名称 *</label>
            <input
              name="name"
              value={editForm ? editForm.name : createForm.name}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-2 ${errors.name ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="版块名称"
              maxLength={64}
            />
            <p className="text-xs text-gray-400 mt-1">不超过 64 个字符</p>
            {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
          </div>
          
          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
            <textarea
              name="description"
              value={editForm ? editForm.description || '' : createForm.description || ''}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-2 ${errors.description ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="版块描述"
              maxLength={255}
              rows={3}
            />
            {errors.description && <p className="text-xs text-red-600 mt-1">{errors.description}</p>}
          </div>
          
          <div>
            <label className="inline-flex items-center gap-2 text-sm font-medium text-gray-700">
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
          
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">排序权重</label>
            <input
              name="sortOrder"
              type="number"
              value={editForm ? editForm.sortOrder : createForm.sortOrder}
              onChange={editForm ? handleEditChange : handleCreateChange}
              className={`w-full rounded border px-3 py-2 ${errors.sortOrder ? 'border-red-500' : 'border-gray-300'}`}
              placeholder="默认 0，值越小越靠前"
            />
            {errors.sortOrder && <p className="text-xs text-red-600 mt-1">{errors.sortOrder}</p>}
          </div>
          
          <div className="md:col-span-2 flex justify-end gap-3 pt-4">
            {editForm ? (
              <>
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-50"
                  disabled={loading}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="rounded bg-blue-600 text-white px-4 py-2 disabled:bg-blue-300"
                  disabled={loading}
                >
                  {loading ? '保存中...' : '保存'}
                </button>
              </>
            ) : (
              <>
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
                  className="rounded bg-blue-600 text-white px-4 py-2 disabled:bg-blue-300"
                  disabled={loading}
                >
                  {loading ? '创建中...' : '创建'}
                </button>
              </>
            )}
          </div>
        </form>
      </div>
      
      {/* 搜索表单 - 显示在中间 */}
      <div className="space-y-4">
        <h3 className="text-lg font-medium text-gray-900">搜索版块</h3>
        <form onSubmit={handleSearch} className="grid grid-cols-1 md:grid-cols-2 gap-6">
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
          
          <div className="md:col-span-2 flex justify-end gap-3 pt-4">
            <button
              type="button"
              onClick={resetSearch}
              className="rounded border border-gray-300 px-4 py-2 text-gray-700 hover:bg-gray-50"
            >
              重置
            </button>
            <button
              type="submit"
              className="rounded bg-blue-600 text-white px-4 py-2"
            >
              搜索
            </button>
          </div>
        </form>
      </div>
      
      {/* 版块列表 - 显示在底部，页面加载时默认显示全部板块 */}
      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <h3 className="text-lg font-medium text-gray-900">版块列表</h3>
          <button
            onClick={loadBoards}
            className="rounded bg-gray-100 px-3 py-1 text-sm text-gray-700 hover:bg-gray-200"
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
  );
};

export default BoardManagement;