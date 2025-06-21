// filepath: i:\待办事项\短学期\NewsPublishingSystem\my-vite-app\src\components\Admin-management\DeleteAdmin.tsx
import React, { useState, useEffect, useRef } from 'react';

// 定义管理员数据类型 - 基于AdministratorDTO的结构
interface AdminDTO {
    id: number;
    account: string;
    phone: string;
    email: string;
    sex: string;
    permissionsId: number;
    isActive: boolean;
    registeredAt: string;
    updatedAt: string;
}

// 定义搜索条件
interface AdminSearchCriteria {
    id?: number;
    account?: string;
    phone?: string;
    email?: string;
}

// 模拟API服务
// 在实际应用中，这些函数应该位于一个单独的服务文件中并与后端API交互
const fetchAdmins = async (): Promise<AdminDTO[]> => {
    // 模拟API请求
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve([
                {
                    id: 1,
                    account: 'admin1',
                    phone: '13800000001',
                    email: 'admin1@example.com',
                    sex: '男',
                    permissionsId: 1,
                    isActive: true,
                    registeredAt: '2023-01-01T08:00:00',
                    updatedAt: '2023-01-10T10:30:00'
                },
                {
                    id: 2,
                    account: 'admin2',
                    phone: '13800000002',
                    email: 'admin2@example.com',
                    sex: '女',
                    permissionsId: 2,
                    isActive: true,
                    registeredAt: '2023-02-01T09:00:00',
                    updatedAt: '2023-02-15T11:20:00'
                }
            ]);
        }, 500);
    });
};

const searchAdmins = async (criteria: AdminSearchCriteria): Promise<AdminDTO[]> => {
    // 模拟API请求
    return new Promise<AdminDTO[]>((resolve) => {
        setTimeout(() => {
            // 模拟数据
            const admins = [
                {
                    id: 1,
                    account: 'admin1',
                    phone: '13800000001',
                    email: 'admin1@example.com',
                    sex: '男',
                    permissionsId: 1,
                    isActive: true,
                    registeredAt: '2023-01-01T08:00:00',
                    updatedAt: '2023-01-10T10:30:00'
                },
                {
                    id: 2,
                    account: 'admin2',
                    phone: '13800000002',
                    email: 'admin2@example.com',
                    sex: '女',
                    permissionsId: 2,
                    isActive: true,
                    registeredAt: '2023-02-01T09:00:00',
                    updatedAt: '2023-02-15T11:20:00'
                }
            ];

            // 简单过滤：只有当字段不为空时才过滤
            let results = [...admins];
            if (criteria.id !== undefined) {
                results = results.filter(a => a.id === criteria.id);
            }
            if (criteria.account) {
                results = results.filter(a => a.account.includes(criteria.account!));
            }
            if (criteria.phone) {
                results = results.filter(a => a.phone.includes(criteria.phone!));
            }
            if (criteria.email) {
                results = results.filter(a => a.email.includes(criteria.email!));
            }

            resolve(results);
        }, 300);
    });
};

const fetchAdminById = async (id: number): Promise<AdminDTO> => {
    // 模拟API请求
    return new Promise((resolve) => {
        setTimeout(() => {
            const admin = {
                id,
                account: `admin${id}`,
                phone: `1380000000${id}`,
                email: `admin${id}@example.com`,
                sex: id % 2 === 0 ? '女' : '男',
                permissionsId: id % 3 + 1,
                isActive: true,
                registeredAt: '2023-01-01T08:00:00',
                updatedAt: '2023-01-10T10:30:00'
            };
            resolve(admin);
        }, 300);
    });
};

const deleteAdmin = async (id: number): Promise<boolean> => {
    // 模拟API请求
    return new Promise((resolve) => {
        setTimeout(() => {
            console.log(`删除管理员ID: ${id}`);
            resolve(true);
        }, 1000);
    });
};

type SearchField = 'id' | 'account' | 'phone' | 'email';
const MAX_RECENT = 10;

const DeleteAdmin: React.FC = () => {
    // 搜索相关状态
    const [searchField, setSearchField] = useState<SearchField>('id');
    const [keyword, setKeyword] = useState<string>('');
    const [recentAdmins, setRecentAdmins] = useState<AdminDTO[]>([]);
    const [filteredAdmins, setFilteredAdmins] = useState<AdminDTO[]>([]);
    const [dropdownOpen, setDropdownOpen] = useState<boolean>(false);
    const searchTimer = useRef<number | undefined>(undefined);
    const blurTimer = useRef<number | undefined>(undefined);
    const dropdownRef = useRef<HTMLDivElement>(null);

    // 选中 & 详细信息状态
    const [admin, setAdmin] = useState<AdminDTO | null>(null);
    const [showInfo, setShowInfo] = useState<boolean>(false);

    // 全局加载／提示
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);

    // 1. 初始化「最近更新」列表
    useEffect(() => {
        fetchAdmins()
            .then(list => {
                const recent = list
                    .sort((a, b) => {
                        const tA = new Date(a.updatedAt || a.registeredAt || '').getTime();
                        const tB = new Date(b.updatedAt || b.registeredAt || '').getTime();
                        return tB - tA;
                    })
                    .slice(0, MAX_RECENT);
                setRecentAdmins(recent);
                setFilteredAdmins(recent);
            })
            .catch(() => console.error('初始化最近管理员列表失败'));
    }, []);

    // 2. 执行搜索
    const performSearch = async (kw: string) => {
        if (!kw.trim()) {
            // 关键字为空 → 展示最近更新
            setFilteredAdmins(recentAdmins);
            return;
        }
        // 构造搜索条件
        const criteria: AdminSearchCriteria = {};
        if (searchField === 'id') {
            const idNum = Number(kw);
            if (!isNaN(idNum)) criteria.id = idNum;
        } else {
            criteria[searchField] = kw;
        }

        try {
            const res = await searchAdmins(criteria);
            setFilteredAdmins(res);
        } catch {
            setFilteredAdmins([]);
        }
    };

    // 3. 输入变化 + 防抖
    const handleKeywordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setKeyword(val);
        setError(null);
        setSuccess(null);
        setShowInfo(false);
        setAdmin(null);

        window.clearTimeout(searchTimer.current);
        searchTimer.current = window.setTimeout(() => {
            performSearch(val);
            setDropdownOpen(true);
        }, 300);
    };

    // 4. 搜索字段切换时立即触发一次搜索（如果有关键字）
    const handleFieldChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        const f = e.target.value as SearchField;
        setSearchField(f);
        if (keyword.trim()) {
            window.clearTimeout(searchTimer.current);
            window.setTimeout(() => performSearch(keyword), 0);
        }
    };

    // 5. 选择一个管理员
    const selectAdmin = async (id: number) => {
        setDropdownOpen(false);
        setError(null);
        setSuccess(null);

        setLoading(true);
        setShowInfo(true);

        try {
            const adminData = await fetchAdminById(id);
            setAdmin(adminData);
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
        } catch (err) {
            setError('获取管理员信息失败！');
            setAdmin(null);
        } finally {
            setLoading(false);
        }
    };

    // 6. 删除管理员确认
    const handleDelete = async () => {
        if (!admin || !admin.id) {
            setError('请先选择要删除的管理员！');
            return;
        }

        if (!window.confirm(`确定要删除管理员 ${admin.account}(ID: ${admin.id}) 吗？此操作不可恢复！`)) {
            return;
        }

        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            await deleteAdmin(admin.id);
            setSuccess(`成功删除管理员 ${admin.account}！`);
            setShowInfo(false);
            setAdmin(null);
            setKeyword('');

            // 刷新管理员列表
            const updatedList = await fetchAdmins();
            const recent = updatedList
                .sort((a, b) => {
                    const tA = new Date(a.updatedAt || a.registeredAt || '').getTime();
                    const tB = new Date(b.updatedAt || b.registeredAt || '').getTime();
                    return tB - tA;
                })
                .slice(0, MAX_RECENT);
            setRecentAdmins(recent);
            setFilteredAdmins(recent);
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
        } catch (err) {
            setError('删除管理员失败！');
        } finally {
            setLoading(false);
        }
    };

    // 7. 点击外部区域关闭下拉
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
                window.clearTimeout(blurTimer.current);
                blurTimer.current = window.setTimeout(() => setDropdownOpen(false), 200);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            window.clearTimeout(blurTimer.current);
            window.clearTimeout(searchTimer.current);
        };
    }, []);

    return (
        <div className="bg-white shadow-md rounded-lg p-6">
            <h2 className="text-xl font-bold mb-6">删除管理员</h2>

            {/* 全局错误/成功提示 */}
            {error && (
                <div className="mb-4 p-3 bg-red-100 text-red-700 rounded-md">
                    <p>{error}</p>
                </div>
            )}

            {success && (
                <div className="mb-4 p-3 bg-green-100 text-green-700 rounded-md">
                    <p>{success}</p>
                </div>
            )}

            {/* 搜索框 */}
            <div className="mb-6">
                <label className="block text-sm font-medium text-gray-700 mb-1">搜索管理员</label>
                <div className="flex">
                    <select
                        value={searchField}
                        onChange={handleFieldChange}
                        className="mr-2 rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                    >
                        <option value="id">管理员ID</option>
                        <option value="account">用户名</option>
                        <option value="phone">手机号</option>
                        <option value="email">邮箱</option>
                    </select>

                    <div className="relative flex-1" ref={dropdownRef}>
                        <input
                            type="text"
                            value={keyword}
                            onChange={handleKeywordChange}
                            onFocus={() => setDropdownOpen(true)}
                            placeholder={`请输入${
                                searchField === 'id' ? 'ID' : 
                                searchField === 'account' ? '用户名' : 
                                searchField === 'phone' ? '手机号' : 
                                '邮箱'
                            }进行搜索`}
                            className="w-full px-3 py-2 rounded-md border border-gray-300 focus:outline-none focus:ring-2 focus:ring-purple-500"
                        />

                        {/* 下拉搜索结果 */}
                        {dropdownOpen && (
                            <div className="absolute z-10 w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg max-h-60 overflow-auto">
                                {filteredAdmins.length === 0 ? (
                                    <div className="p-3 text-gray-500">未找到符合条件的管理员</div>
                                ) : (
                                    filteredAdmins.map(item => (
                                        <div
                                            key={item.id}
                                            onClick={() => selectAdmin(item.id)}
                                            className="p-3 hover:bg-gray-100 cursor-pointer border-b border-gray-100 last:border-0"
                                        >
                                            <div className="flex justify-between items-center">
                                                <span className="font-medium">{item.account}</span>
                                                <span className="text-gray-500 text-sm">ID: {item.id}</span>
                                            </div>
                                            <div className="text-sm text-gray-600">{item.email}</div>
                                        </div>
                                    ))
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* 管理员详情 */}
            {showInfo && (
                <div className="mt-6">
                    {loading ? (
                        <div className="flex justify-center p-6">
                            <div className="animate-spin rounded-full h-8 w-8 border-4 border-purple-500 border-t-transparent"></div>
                        </div>
                    ) : admin ? (
                        <div>
                            <h3 className="text-lg font-medium mb-4 pb-2 border-b border-gray-200">管理员详情</h3>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <p className="text-gray-500">管理员ID</p>
                                    <p>{admin.id}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">用户名</p>
                                    <p>{admin.account}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">邮箱</p>
                                    <p>{admin.email || '-'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">手机号</p>
                                    <p>{admin.phone || '-'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">性别</p>
                                    <p>{admin.sex || '-'}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">权限级别</p>
                                    <p>
                                        {admin.permissionsId === 1 && '普通管理员'}
                                        {admin.permissionsId === 2 && '高级管理员'}
                                        {admin.permissionsId === 3 && '超级管理员'}
                                        {(!admin.permissionsId || admin.permissionsId > 3) && '未知'}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">账号状态</p>
                                    <p>
                                        {admin.isActive ? (
                                            <span className="text-green-500">已激活</span>
                                        ) : (
                                            <span className="text-red-500">已禁用</span>
                                        )}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-gray-500">注册时间</p>
                                    <p>{new Date(admin.registeredAt).toLocaleString()}</p>
                                </div>
                                <div>
                                    <p className="text-gray-500">最后更新</p>
                                    <p>{admin.updatedAt ? new Date(admin.updatedAt).toLocaleString() : '-'}</p>
                                </div>
                            </div>

                            <div className="mt-8 flex justify-center">
                                <button
                                    onClick={handleDelete}
                                    disabled={loading}
                                    className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 focus:outline-none disabled:bg-red-300"
                                >
                                    {loading ? "处理中..." : "删除此管理员"}
                                </button>
                            </div>
                        </div>
                    ) : (
                        <div className="text-center py-4 text-gray-500">未找到管理员信息</div>
                    )}
                </div>
            )}
        </div>
    );
};

export default DeleteAdmin;
