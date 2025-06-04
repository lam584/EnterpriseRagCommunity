// src/components/reader-management/EditReaderForm.tsx
import React, { useState, useEffect, useRef } from 'react';
import { fetchReaders, fetchReaderById, updateReader, ReaderDTO } from '../../services/readerService';
import { fetchReaderPermissions, ReaderPermissionDTO } from '../../services/readerPermissionService';

// 扩展 ReaderDTO 接口，确保包含时间字段
interface ExtendedReaderDTO extends ReaderDTO {
  createdAt?: string;
  updatedAt?: string;
}

const EditReaderForm: React.FC = () => {
  const [readers, setReaders] = useState<ExtendedReaderDTO[]>([]);
  const [filteredReaders, setFilteredReaders] = useState<ExtendedReaderDTO[]>([]);
  const [permissions, setPermissions] = useState<ReaderPermissionDTO[]>([]);
  const [selectedReaderId, setSelectedReaderId] = useState<string>('');

  // 读者表单数据
  const [form, setForm] = useState<ReaderDTO>({
    account: '',
    phone: '',
    email: '',
    sex: '男',
    permission: { id: 0 },
    isActive: true
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);
  const [changePassword, setChangePassword] = useState<boolean>(false);
  const [newPassword, setNewPassword] = useState<string>('');

  // 搜索条件
  const [searchCriteria, setSearchCriteria] = useState({
    keyword: '',
    searchField: 'account' // 默认按账号搜索
  });

  // 下拉菜单是否展开
  const [dropdownOpen, setDropdownOpen] = useState(false);
  // 最大显示数量
  const MAX_RECENT_READERS = 10;

  // 添加一个引用来追踪下拉框状态
  const dropdownRef = useRef<HTMLDivElement>(null);
  // 添加一个定时器引用
  const timeoutRef = useRef<number | null>(null);

  useEffect(() => {
    // 加载所有读者和权限数据
    Promise.all([
      fetchReaders().catch(() => []),
      fetchReaderPermissions().catch(() => [])
    ]).then(([readersData, permissionsData]) => {
      setReaders(readersData);
      setPermissions(permissionsData);

      // 默认展示最近更新的读者
      const sortedReaders = [...readersData].sort((a, b) => {
        const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        return dateB - dateA; // 降序排列，最新的在前面
      }).slice(0, MAX_RECENT_READERS);

      setFilteredReaders(sortedReaders);
      setDropdownOpen(true);
    });
  }, []);

  // 当搜索条件变化时，过滤读者
  useEffect(() => {
    if (!searchCriteria.keyword.trim()) {
      // 当搜索框为空时，显示最近更新的读者
      const recentReaders = [...readers].sort((a, b) => {
        const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        return dateB - dateA; // 降序排列，最新的在前面
      }).slice(0, MAX_RECENT_READERS);

      setFilteredReaders(recentReaders);
      setDropdownOpen(true);
      return;
    }

    const keyword = searchCriteria.keyword.toLowerCase();
    const filtered = readers.filter(reader => {
      switch (searchCriteria.searchField) {
        case 'id':
          return reader.id?.toString() === keyword || reader.id?.toString().includes(keyword);
        case 'phone':
          return reader.phone.toLowerCase().includes(keyword);
        case 'email':
          return reader.email.toLowerCase().includes(keyword);
        case 'account':
        default:
          return reader.account.toLowerCase().includes(keyword);
      }
    });
    setFilteredReaders(filtered);
    setDropdownOpen(true);
  }, [searchCriteria, readers]);

  // 添加点击外部关闭下拉框的处理函数
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setSearchCriteria(prev => ({ ...prev, [name]: value }));
  };

  // 添加搜索框失焦处理函数
  const handleSearchBlur = () => {
    // 使用setTimeout延迟关闭下拉框，让用户有时间点击选项
    timeoutRef.current = window.setTimeout(() => {
      setDropdownOpen(false);
    }, 200); // 200毫秒延迟
  };

  // 当鼠标进入下拉框时取消定时器
  const handleDropdownMouseEnter = () => {
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  };

  // 当鼠标离开下拉框时启动关闭定时器
  const handleDropdownMouseLeave = () => {
    timeoutRef.current = window.setTimeout(() => {
      setDropdownOpen(false);
    }, 200);
  };

  const handleReaderSelect = async (readerId: number) => {
    setSelectedReaderId(String(readerId));
    setDropdownOpen(false);
    setLoading(true);
    setMessage(null);
    setChangePassword(false);
    setNewPassword('');

    try {
      const reader = await fetchReaderById(readerId);
      setForm(reader);
      setErrors({});
    } catch (err) {
      setMessage({type: 'error', text: '加载读者信息失败'});
    } finally {
      setLoading(false);
    }
  };

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};
    if (!form.account) newErrors.account = '账号不能为空';
    if (!form.phone) newErrors.phone = '手机号不能为空';
    if (!form.email) newErrors.email = '邮箱不能为空';
    if (changePassword && !newPassword) newErrors.password = '新密码不能为空';

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;

    if (name === 'permission') {
      const id = Number(value);
      setForm(f => ({ ...f, permission: { id } } as any));
    } else {
      setForm(f => ({ ...f, [name]: value } as any));
    }

    if (errors[name]) {
      setErrors(prev => {
        const newErrors = {...prev};
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || !form.id) return;

    setLoading(true);
    setMessage(null);

    try {
      const readerToUpdate = { ...form };

      // 如果需要更改密码
      if (changePassword) {
        readerToUpdate.password = newPassword;
      }

      await updateReader(form.id, readerToUpdate);
      setMessage({type: 'success', text: '读者信息更新成功！'});

      // 刷新读者列表
      const updatedReaders = await fetchReaders();
      setReaders(updatedReaders);
      setFilteredReaders(updatedReaders);

      // 密码更改后，重置相关状态
      setChangePassword(false);
      setNewPassword('');
    } catch (err) {
      setMessage({type: 'error', text: '更新失败，请检查填写内容或稍后重试'});
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex">
      <div className="flex-1 bg-white shadow-md rounded-md p-6">
        <h2 className="text-lg font-bold mb-4">编辑读者信息</h2>

        {message && (
          <div className={`p-4 mb-4 rounded ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
            {message.text}
          </div>
        )}

        {/* 搜索读者部分 */}
        <div className="mb-6">
          <label className="block mb-1">搜索并选择要编辑的读者</label>
          <div className="flex mb-2">
            <select
              name="searchField"
              value={searchCriteria.searchField}
              onChange={handleSearchChange}
              className="border border-gray-300 rounded-l p-2"
            >
              <option value="id">ID</option>
              <option value="account">账号</option>
              <option value="phone">手机号</option>
              <option value="email">邮箱</option>
            </select>
            <input
              name="keyword"
              value={searchCriteria.keyword}
              onChange={handleSearchChange}
              className="flex-1 border border-gray-300 p-2 rounded-r"
              placeholder={searchCriteria.searchField === 'id' ? "输入读者ID..." : "输入关键词搜索读者..."}
              onFocus={() => setDropdownOpen(true)}
              onBlur={handleSearchBlur}
              disabled={loading}
            />
          </div>

          {dropdownOpen && filteredReaders.length > 0 && (
            <div className="relative" ref={dropdownRef}>
              <div
                className="absolute z-10 w-full bg-white border border-gray-300 rounded shadow-lg overflow-y-auto"
                style={{ maxHeight: '360px' }}
                onMouseEnter={handleDropdownMouseEnter}
                onMouseLeave={handleDropdownMouseLeave}
              >
                {filteredReaders.map(reader => (
                  <div
                    key={reader.id}
                    className="p-2 hover:bg-gray-100 cursor-pointer border-b border-gray-200"
                    onClick={() => reader.id && handleReaderSelect(reader.id)}
                  >
                    <div className="font-medium">
                      <span className="text-gray-500 mr-2">ID: {reader.id}</span>
                      {reader.account}
                    </div>
                    <div className="text-sm text-gray-600">
                      {reader.phone} | {reader.email} | {reader.sex}
                    </div>
                    {reader.updatedAt && (
                      <div className="text-xs text-gray-500">
                        更新时间: {new Date(reader.updatedAt).toLocaleString()}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {filteredReaders.length === 0 && searchCriteria.keyword && (
            <div className="text-gray-500 mt-1">未找到匹配的读者</div>
          )}

          {selectedReaderId && (
            <div className="mt-2 p-2 bg-blue-50 border border-blue-200 rounded">
              <span className="text-blue-700">已选择读者ID: {selectedReaderId}</span>
            </div>
          )}
        </div>

        {selectedReaderId && (
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="grid grid-cols-2 gap-4">
              {/* 账号 */}
              <div>
                <label className="block mb-1">账号</label>
                <input
                  name="account"
                  value={form.account}
                  onChange={handleChange}
                  className={`w-full border p-2 rounded ${errors.account ? 'border-red-500' : 'border-gray-300'}`}
                />
                {errors.account && <p className="text-red-500 text-sm mt-1">{errors.account}</p>}
              </div>

              {/* 手机号 */}
              <div>
                <label className="block mb-1">手机号</label>
                <input
                  name="phone"
                  value={form.phone}
                  onChange={handleChange}
                  className={`w-full border p-2 rounded ${errors.phone ? 'border-red-500' : 'border-gray-300'}`}
                />
                {errors.phone && <p className="text-red-500 text-sm mt-1">{errors.phone}</p>}
              </div>

              {/* 邮箱 */}
              <div>
                <label className="block mb-1">邮箱</label>
                <input
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  className={`w-full border p-2 rounded ${errors.email ? 'border-red-500' : 'border-gray-300'}`}
                />
                {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email}</p>}
              </div>

              {/* 性别 */}
              <div>
                <label className="block mb-1">性别</label>
                <select
                  name="sex"
                  value={form.sex || '男'}
                  onChange={handleChange}
                  className="w-full border border-gray-300 p-2 rounded"
                >
                  <option value="男">男</option>
                  <option value="女">女</option>
                  <option value="其他">其他</option>
                </select>
              </div>

              {/* 权限 - 修正显示权限名称的部分 */}
              <div>
                <label className="block mb-1">权限角色</label>
                <select
                    name="permission"
                    value={form.permission?.id || ''}
                    onChange={handleChange}
                    className="w-full border border-gray-300 p-2 rounded"
                >
                  <option value="">选择权限角色...</option>
                  {permissions.map(perm => (
                      <option key={perm.id} value={perm.id}>
                        {perm.roles || `权限ID: ${perm.id}`}  {/* 使用 roles */}
                      </option>
                  ))}
                </select>
              </div>

              {/* 状态 */}
              <div>
                <label className="block mb-1">状态</label>
                <select
                  name="isActive"
                  value={form.isActive?.toString() || 'true'}
                  onChange={(e) => setForm(prev => ({ ...prev, isActive: e.target.value === 'true' }))}
                  className="w-full border border-gray-300 p-2 rounded"
                >
                  <option value="true">激活</option>
                  <option value="false">禁用</option>
                </select>
              </div>

              {/* 修改密码区域 */}
              <div className="col-span-2 mt-4">
                <div className="flex items-center mb-2">
                  <input
                    type="checkbox"
                    id="changePassword"
                    checked={changePassword}
                    onChange={(e) => setChangePassword(e.target.checked)}
                    className="mr-2"
                  />
                  <label htmlFor="changePassword" className="text-sm">修改密码</label>
                </div>

                {changePassword && (
                  <div>
                    <input
                      type="password"
                      placeholder="输入新密码"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      className={`w-full border p-2 rounded ${errors.password ? 'border-red-500' : 'border-gray-300'}`}
                    />
                    {errors.password && <p className="text-red-500 text-sm mt-1">{errors.password}</p>}
                  </div>
                )}
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className={`bg-blue-500 text-white px-6 py-2 rounded hover:bg-blue-600 ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
            >
              {loading ? '更新中...' : '保存修改'}
            </button>
          </form>
        )}
      </div>

      <div className="w-1/4 ml-6">
        <div className="bg-white shadow-md rounded-md p-6">
          <h2 className="text-xl font-bold mb-4">编辑指南</h2>
          <ul className="list-disc pl-5 space-y-2 text-gray-700">
            <li>使用搜索框查找并选择要编辑的读者</li>
            <li>可以按ID、账号、手机号或邮箱搜索</li>
            <li>修改相关信息后点击保存</li>
            <li>账号、手机号和邮箱为必填项</li>
            <li>如需修改密码，请勾选"修改密码"选项</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default EditReaderForm;
