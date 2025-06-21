// src/components/reader-management/DeleteUser.tsx
import React, { useState, useEffect, useRef } from 'react';
import {
    fetchReaders,
    searchReaders,
    fetchReaderById,
    deleteReader,
    ReaderDTO,
    ReaderSearchCriteria
} from '../../services/UserService.ts';

type SearchField = 'id' | 'account' | 'phone' | 'email';
const MAX_RECENT = 10;

const DeleteUser: React.FC = () => {
    // 搜索相关状态
    const [searchField, setSearchField] = useState<SearchField>('id');
    const [keyword, setKeyword] = useState<string>('');
    const [recentReaders, setRecentReaders] = useState<ReaderDTO[]>([]);
    const [filteredReaders, setFilteredReaders] = useState<ReaderDTO[]>([]);
    const [dropdownOpen, setDropdownOpen] = useState<boolean>(false);
    const searchTimer = useRef<number | undefined>(undefined);
    const blurTimer = useRef<number | undefined>(undefined);

    // 选中 & 详细信息状态
    const [selectedReaderId, setSelectedReaderId] = useState<number | undefined>(undefined);
    const [reader, setReader] = useState<ReaderDTO | null>(null);
    const [showInfo, setShowInfo] = useState<boolean>(false);

    // 全局加载／提示
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);

    // 1. 初始化「最近更新」列表
    useEffect(() => {
        fetchReaders()
            .then(list => {
                const recent = list
                    .sort((a, b) => {
                        const tA = new Date(a.updatedAt || a.createdAt || '').getTime();
                        const tB = new Date(b.updatedAt || b.createdAt || '').getTime();
                        return tB - tA;
                    })
                    .slice(0, MAX_RECENT);
                setRecentReaders(recent);
                setFilteredReaders(recent);
            })
            .catch(() => console.error('初始化最近用户列表失败'));
    }, []);

    // 2. 执行搜索
    const performSearch = async (kw: string) => {
        if (!kw.trim()) {
            // 关键字为空 → 展示最近更新
            setFilteredReaders(recentReaders);
            return;
        }
        // 构造搜索条件
        const criteria: ReaderSearchCriteria = {};
        if (searchField === 'id') {
            const idNum = Number(kw);
            if (!isNaN(idNum)) criteria.id = idNum;
        } else {
            criteria[searchField] = kw;
        }

        try {
            const res = await searchReaders(criteria);
            setFilteredReaders(res);
        } catch {
            setFilteredReaders([]);
        }
    };

    // 3. 输入变化 + 防抖
    const handleKeywordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setKeyword(val);
        setError(null);
        setSuccess(null);
        setShowInfo(false);
        setReader(null);
        setSelectedReaderId(undefined);

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

    // 5. 聚焦 / 失焦 控制下拉
    const handleInputFocus = () => {
        setDropdownOpen(true);
        if (!keyword.trim()) {
            setFilteredReaders(recentReaders);
        }
    };
    const handleInputBlur = () => {
        blurTimer.current = window.setTimeout(() => {
            setDropdownOpen(false);
        }, 200);
    };


    // 7. 选中条目 → 拉取详情 & 显示 ID
    const handleSelect = async (r: ReaderDTO) => {
        if (!r.id) return;
        window.clearTimeout(blurTimer.current);
        setDropdownOpen(false);
        setSelectedReaderId(r.id);
        // setKeyword(r.id.toString());
        setError(null);
        setSuccess(null);

        setLoading(true);
        try {
            const data = await fetchReaderById(r.id);
            setReader(data);
            setShowInfo(true);
            setSuccess('用户信息加载成功');
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : '加载失败';
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    // 8. 确认删除
    const handleConfirmDelete = async () => {
        if (selectedReaderId === undefined) {
            setError('请先选择有效的用户');
            return;
        }
        setLoading(true);
        setError(null);
        setSuccess(null);
        try {
            await deleteReader(selectedReaderId);
            setSuccess('删除成功');
            // 重置状态
            setKeyword('');
            setReader(null);
            setShowInfo(false);
            setSelectedReaderId(undefined);
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : '删除失败';
            setError(msg);
        } finally {
            setLoading(false);
        }
    };
    const handleCancel = () => {
        setKeyword('');
        setReader(null);
        setShowInfo(false);
        setSelectedReaderId(undefined);
        setError(null);
        setSuccess(null);
    };

    return (
        <div className="max-w-2xl mx-auto mt-10 p-6 bg-white shadow rounded relative">
            <h1 className="text-2xl font-bold mb-4">删除用户</h1>
            {error && <div className="mb-4 p-3 bg-red-100 text-red-700 border border-red-400 rounded">{error}</div>}
            {success && <div className="mb-4 p-3 bg-green-100 text-green-700 border border-green-400 rounded">{success}</div>}

            {/* 搜索条件 */}
            <div className="mb-4 flex space-x-2">
                <select
                    value={searchField}
                    onChange={handleFieldChange}
                    className="border p-2 rounded"
                >
                    <option value="id">按 ID</option>
                    <option value="account">按 账号</option>
                    <option value="phone">按 手机号</option>
                    <option value="email">按 邮箱</option>
                </select>
                <div className="relative flex-1">
                    <input
                        type="text"
                        value={keyword}
                        onChange={handleKeywordChange}
                        onFocus={handleInputFocus}
                        onBlur={handleInputBlur}
                        disabled={loading}
                        placeholder="请输入关键字"
                        className="w-full border p-2 rounded focus:outline-none focus:ring-2 focus:ring-blue-400"
                    />
                    {dropdownOpen && (
                        <div
                            className="absolute top-full left-0 right-0 mt-1 bg-white border rounded shadow max-h-60 overflow-auto z-10"
                        >
                            {filteredReaders.length > 0 ? (
                                filteredReaders.map(r => (
                                    <div
                                        key={r.id}
                                        className="p-2 hover:bg-gray-100 cursor-pointer border-b last:border-none"
                                        onClick={() => handleSelect(r)}
                                    >
                                        <div className="font-medium">{r.account}（ID:{r.id}）</div>
                                        <div className="text-xs text-gray-600">
                                            {r.phone} | {r.email}
                                        </div>
                                    </div>
                                ))
                            ) : (
                                <div className="p-2 text-gray-500">暂无匹配结果</div>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {/* 新增：选中后提示已选 ID */}
            {selectedReaderId && (
                <div className="mt-2 p-2 bg-blue-50 border border-blue-200 rounded">
                    <span className="text-blue-700">已选择用户ID: {selectedReaderId}</span>
                </div>
            )}

            {/* 搜索后显示的用户详情 */}
            {showInfo && reader && (
                <div className="mb-4 border rounded p-4 bg-gray-50">
                    <p><strong>账号：</strong>{reader.account}</p>
                    <p><strong>手机号：</strong>{reader.phone}</p>
                    <p><strong>邮箱：</strong>{reader.email}</p>
                    {/* 新增更多信息 */}
                    <p><strong>性别：</strong>{reader.sex || '-'}</p>
                    <p><strong>权限：</strong>{reader.permission?.roles || '-'}</p>
                    <p><strong>状态：</strong>{reader.isActive ? '激活' : '禁用'}</p>
                    <p><strong>创建时间：</strong>{reader.createdAt ? new Date(reader.createdAt).toLocaleString() : '-'}</p>
                    <p><strong>更新时间：</strong>{reader.updatedAt ? new Date(reader.updatedAt).toLocaleString() : '-'}</p>
                </div>
            )}

            {/* 操作按钮 */}
            <div className="flex space-x-4">
                <button
                    onClick={handleConfirmDelete}
                    disabled={loading || !showInfo}
                    className={`px-4 py-2 rounded text-white ${
                        loading || !showInfo ? 'bg-gray-400 cursor-not-allowed' : 'bg-red-500 hover:bg-red-600'
                    }`}
                >
                    {loading ? '处理中...' : '确认删除'}
                </button>
                <button
                    onClick={handleCancel}
                    disabled={loading}
                    className={`px-4 py-2 rounded text-white ${
                        loading ? 'bg-gray-400 cursor-not-allowed' : 'bg-gray-500 hover:bg-gray-600'
                    }`}
                >
                    取消
                </button>
            </div>
        </div>
    );
};

export default DeleteUser;