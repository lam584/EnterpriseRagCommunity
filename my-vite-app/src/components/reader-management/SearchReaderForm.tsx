// src/components/reader-management/SearchReaderForm.tsx
import React, { useState } from 'react';

const SearchReaderForm: React.FC = () => {
    // 表单字段状态
    const [formData, setFormData] = useState({
        id: '',
        username: '',
        phone: '',
        email: '',
        role: '普通读者',
        gender: '请选择',
        startDate: '',
        endDate: ''
    });

    // 精确搜索复选框状态
    const [exactSearch, setExactSearch] = useState({
        id: false,
        username: false,
        phone: false,
        email: false
    });

    // 搜索结果状态
    const [showResults, setShowResults] = useState(false);
    const [showSuccess, setShowSuccess] = useState(false);

    // 模拟搜索结果数据
    const searchResults = [
        {
            id: '1',
            username: 'reader1',
            phone: '13900000001',
            email: 'reader1@example.com',
            registerDate: '2023-01-01',
            role: '普通读者',
            gender: '男',
            isActive: true
        }
    ];

    // 处理输入变化
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { id, value } = e.target;
        setFormData({
            ...formData,
            [id]: value
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
    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();

        // 模拟API搜索请求
        console.log('搜索条件:', {
            ...formData,
            exactSearch
        });

        // 显示搜索结果
        setShowResults(true);
        setShowSuccess(true);
    };

    return (
        <div className="max-w-7xl mx-auto p-6 bg-white shadow-md rounded-md mt-10">
            <h1 className="text-xl font-bold mb-6">读者查询</h1>

            <form className="grid grid-cols-2 gap-6" onSubmit={handleSearch}>
                <div>
                    <label htmlFor="id" className="block text-sm font-medium mb-1">id</label>
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
                        <option value="普通读者">普通读者</option>
                        <option value="管理员">管理员</option>
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
                    <input
                        type="text"
                        id="startDate"
                        value={formData.startDate}
                        onChange={handleInputChange}
                        placeholder="yyyy/mm/日"
                        className="w-full border border-gray-300 rounded-md p-2"
                    />
                </div>

                <div>
                    <label htmlFor="endDate" className="block text-sm font-medium mb-1">注册日期 (结束)</label>
                    <input
                        type="text"
                        id="endDate"
                        value={formData.endDate}
                        onChange={handleInputChange}
                        placeholder="yyyy/mm/日"
                        className="w-full border border-gray-300 rounded-md p-2"
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
                            {searchResults.map((reader, index) => (
                                <tr key={index}>
                                    <td className="border border-gray-300 p-2 text-center">{reader.id}</td>
                                    <td className="border border-gray-300 p-2 text-center">{reader.username}</td>
                                    <td className="border border-gray-300 p-2 text-center">{reader.phone}</td>
                                    <td className="border border-gray-300 p-2 text-center">{reader.email}</td>
                                    <td className="border border-gray-300 p-2 text-center">{reader.role}</td>
                                    <td className="border border-gray-300 p-2 text-center">{reader.gender}</td>
                                    <td className="border border-gray-300 p-2 text-center">{reader.isActive ? '是' : '否'}</td>
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
