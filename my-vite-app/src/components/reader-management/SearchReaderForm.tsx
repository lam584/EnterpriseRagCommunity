import React, { useState, useEffect } from 'react';
import DatePicker from 'react-datepicker'; // ✅ 正确导入日期选择器组件
import 'react-datepicker/dist/react-datepicker.css'; // ✅ 导入样式
import { searchReaders, ReaderDTO } from '../../services/readerService';
import { fetchReaderPermissions, ReaderPermissionDTO } from '../../services/readerPermissionService';

const SearchReaderForm: React.FC = () => {
    // 表单字段状态 - 将 startDate 和 endDate 改为 Date | null 类型
    const [formData, setFormData] = useState({
        id: '',
        username: '',
        phone: '',
        email: '',
        role: '', // 空表示“全部”
        gender: '请选择',
        startDate: null as Date | null,
        endDate: null as Date | null,
    });

    // 精确搜索复选框状态
    const [exactSearch, setExactSearch] = useState({
        id: false,
        username: false,
        phone: false,
        email: false
    });

    // 角色列表
    const [roles, setRoles] = useState<ReaderPermissionDTO[]>([]);

    // 搜索结果状态
    const [showResults, setShowResults] = useState(false);
    const [showSuccess, setShowSuccess] = useState(false);
    const [results, setResults] = useState<ReaderDTO[]>([]);

    // 组件挂载时获取角色列表
    useEffect(() => {
        const loadRoles = async () => {
            try {
                const permissions = await fetchReaderPermissions();
                setRoles(permissions);
            } catch (error) {
                console.error('获取角色列表失败', error);
            }
        };

        loadRoles();
    }, []);

    // 处理输入变化
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setFormData({
            ...formData,
            [id]: value
        });
    };

    // 处理日期选择器变化
    const handleDateChange = (date: Date | null, field: 'startDate' | 'endDate') => {
        setFormData({
            ...formData,
            [field]: date
        });
    };

    // 处理精确搜索选项变化
    const handleExactSearchChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
        setExactSearch({
            ...exactSearch,
            [field]: e.target.checked
        });
    };

    // 处理搜索提交
    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();

        try {
            const idParam = formData.id ? parseInt(formData.id) : undefined;
            const accountParam = formData.username || undefined;
            const phoneParam = formData.phone || undefined;
            const emailParam = formData.email || undefined;
            const genderParam = formData.gender !== '请选择' ? formData.gender : undefined;

            // 如果选择了角色，则传 roleId，否则不传
            const roleParam = formData.role || undefined;

            // 注册时间范围
            const startDateParam = formData.startDate ? formData.startDate.toISOString().split('T')[0] : undefined;
            const endDateParam = formData.endDate ? formData.endDate.toISOString().split('T')[0] : undefined;

            const data = await searchReaders(
                idParam,
                accountParam,
                phoneParam,
                emailParam,
                genderParam,
                roleParam,
                startDateParam,
                endDateParam
            );
            setResults(data);
            setShowResults(true);
            setShowSuccess(true);
        } catch (error) {
            console.error('搜索失败', error);
            setShowSuccess(false);
            setShowResults(false);
        }
    };

    return (
        <div className="max-w-7xl mx-auto p-6 bg-white shadow-md rounded-md mt-10">
            <h1 className="text-xl font-bold mb-6">读者查询</h1>

            <form className="grid grid-cols-2 gap-6" onSubmit={handleSearch}>
                <div>
                    <label htmlFor="id" className="block text-sm font-medium mb-1">ID</label>
                    <input
                        type="text"
                        id="id"
                        value={formData.id}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded-md p-2"
                    />
                    <div className="mt-2">
                        <input
                            type="checkbox"
                            id="exactSearchId"
                            checked={exactSearch.id}
                            onChange={handleExactSearchChange('id')}
                            className="mr-2"
                        />
                        <label htmlFor="exactSearchId" className="text-sm">精确搜索</label>
                    </div>
                </div>

                <div>
                    <label htmlFor="username" className="block text-sm font-medium mb-1">用户名</label>
                    <input
                        type="text"
                        id="username"
                        value={formData.username}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded-md p-2"
                    />
                    <div className="mt-2">
                        <input
                            type="checkbox"
                            id="exactSearchUsername"
                            checked={exactSearch.username}
                            onChange={handleExactSearchChange('username')}
                            className="mr-2"
                        />
                        <label htmlFor="exactSearchUsername" className="text-sm">精确搜索</label>
                    </div>
                </div>

                <div>
                    <label htmlFor="phone" className="block text-sm font-medium mb-1">手机号</label>
                    <input
                        type="text"
                        id="phone"
                        value={formData.phone}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded-md p-2"
                    />
                    <div className="mt-2">
                        <input
                            type="checkbox"
                            id="exactSearchPhone"
                            checked={exactSearch.phone}
                            onChange={handleExactSearchChange('phone')}
                            className="mr-2"
                        />
                        <label htmlFor="exactSearchPhone" className="text-sm">精确搜索</label>
                    </div>
                </div>

                <div>
                    <label htmlFor="email" className="block text-sm font-medium mb-1">邮箱</label>
                    <input
                        type="text"
                        id="email"
                        value={formData.email}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded-md p-2"
                    />
                    <div className="mt-2">
                        <input
                            type="checkbox"
                            id="exactSearchEmail"
                            checked={exactSearch.email}
                            onChange={handleExactSearchChange('email')}
                            className="mr-2"
                        />
                        <label htmlFor="exactSearchEmail" className="text-sm">精确搜索</label>
                    </div>
                </div>

                <div>
                    <label htmlFor="role" className="block text-sm font-medium mb-1">角色:</label>
                    <select
                        id="role"
                        value={formData.role}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded-md p-2"
                    >
                        <option value="">全部</option>
                        {roles.map((role) => (
                            <option key={role.id} value={role.id.toString()}>
                                {role.roles}
                            </option>
                        ))}
                    </select>
                </div>

                <div>
                    <label htmlFor="gender" className="block text-sm font-medium mb-1">性别:</label>
                    <select
                        id="gender"
                        value={formData.gender}
                        onChange={handleInputChange}
                        className="w-full border border-gray-300 rounded-md p-2"
                    >
                        <option value="请选择">请选择</option>
                        <option value="男">男</option>
                        <option value="女">女</option>
                    </select>
                </div>

                <div>
                    <label htmlFor="startDate" className="block text-sm font-medium mb-1">注册日期 (起始)</label>
                    <DatePicker
                        selected={formData.startDate}
                        onChange={(date) => handleDateChange(date, 'startDate')}
                        placeholderText="选择开始日期"
                        dateFormat="yyyy/MM/dd"
                        className="w-full border border-gray-300 rounded-md p-2"
                        popperModifiers={[
                            {
                                name: 'offset',
                                options: { offset: [5, 10] },
                                fn: (args) => {
                                    const x = args.x + 5;
                                    const y = args.y + 10;
                                    return { x, y };
                                }
                            },
                            {
                                name: 'preventOverflow',
                                options: { altBoundary: true },
                                fn: (args) => {
                                    return args;
                                }
                            }
                        ]}
                    />
                </div>

                <div>
                    <label htmlFor="endDate" className="block text-sm font-medium mb-1">注册日期 (结束)</label>
                    <DatePicker
                        selected={formData.endDate}
                        onChange={(date) => handleDateChange(date, 'endDate')}
                        placeholderText="选择结束日期"
                        dateFormat="yyyy/MM/dd"
                        className="w-full border border-gray-300 rounded-md p-2"
                        popperModifiers={[
                            {
                                name: 'offset',
                                options: { offset: [5, 10] },
                                fn: (args) => {
                                    const x = args.x + 5;
                                    const y = args.y + 10;
                                    return { x, y };
                                }
                            },
                            {
                                name: 'preventOverflow',
                                options: { altBoundary: true },
                                fn: (args) => {
                                    return args;
                                }
                            }
                        ]}
                    />
                </div>

                <div className="col-span-2">
                    <button
                        type="submit"
                        className="mt-6 bg-green-500 text-white px-6 py-2 rounded-md hover:bg-green-600"
                    >
                        高级搜索
                    </button>
                </div>
            </form>

            {showSuccess && (
                <div className="mt-4 bg-green-100 text-green-700 p-4 rounded-md">
                    读者搜索成功!
                </div>
            )}

            {showResults && (
                <div className="mt-6">
                    <h2 className="text-lg font-bold mb-4">搜索结果</h2>
                    <table className="w-full border border-gray-300 text-sm">
                        <thead>
                        <tr className="bg-gray-100">
                            <th className="border border-gray-300 p-2">ID</th>
                            <th className="border border-gray-300 p-2">用户名</th>
                            <th className="border border-gray-300 p-2">手机号</th>
                            <th className="border border-gray-300 p-2">邮箱</th>
                            <th className="border border-gray-300 p-2">注册日期</th>
                            <th className="border border-gray-300 p-2">角色</th>
                            <th className="border border-gray-300 p-2">性别</th>
                            <th className="border border-gray-300 p-2">是否激活</th>
                        </tr>
                        </thead>
                        <tbody>
                        {results.map((reader, index) => (
                            <tr key={index}>
                                <td className="border border-gray-300 p-2 text-center">{reader.id}</td>
                                <td className="border border-gray-300 p-2 text-center">{reader.account}</td>
                                <td className="border border-gray-300 p-2 text-center">{reader.phone}</td>
                                <td className="border border-gray-300 p-2 text-center">{reader.email}</td>
                                <td className="border border-gray-300 p-2 text-center">
                                    {reader.createdAt ? new Date(reader.createdAt).toLocaleDateString() : ''}
                                </td>
                                <td className="border border-gray-300 p-2 text-center">
                                    {reader.permission?.roles || '无权限'}
                                </td>
                                <td className="border border-gray-300 p-2 text-center">{reader.sex}</td>
                                <td className="border border-gray-300 p-2 text-center">
                                    {reader.isActive ? (
                                        <span className="text-green-600">已激活</span>
                                    ) : (
                                        <span className="text-red-600">未激活</span>
                                    )}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default SearchReaderForm;