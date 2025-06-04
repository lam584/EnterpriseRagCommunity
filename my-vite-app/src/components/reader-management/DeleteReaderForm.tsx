// src/components/reader-management/DeleteReaderForm.tsx
import React, { useState } from 'react';
import { fetchReaderById, deleteReader } from '../../services/readerService';
import type { ReaderDTO } from '../../services/readerService';

const DeleteReaderForm: React.FC = () => {
    // 状态管理
    const [readerId, setReaderId] = useState<string>('');
    const [reader, setReader] = useState<ReaderDTO | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [showReaderInfo, setShowReaderInfo] = useState<boolean>(false);

    // 处理输入变化
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setReaderId(e.target.value);
        // 重置状态
        setError(null);
        setSuccess(null);
        setShowReaderInfo(false);
        setReader(null);
    };

    // 处理查找读者
    const handleSearch = async () => {
        if (!readerId.trim()) {
            setError('请输入读者ID');
            return;
        }

        const id = parseInt(readerId, 10);
        if (isNaN(id)) {
            setError('读者ID必须是数字');
            return;
        }

        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            const readerData = await fetchReaderById(id);
            setReader(readerData);
            setShowReaderInfo(true);
            setSuccess('读者信息获取成功');
        } catch (err) {
            setError(err instanceof Error ? err.message : '查找读者失败');
            setShowReaderInfo(false);
            setReader(null);
        } finally {
            setLoading(false);
        }
    };

    // 处理确认删除
    const handleConfirmDelete = async () => {
        if (!reader || !reader.id) {
            setError('无效的读者信息');
            return;
        }

        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            await deleteReader(reader.id);
            setSuccess('读者删除成功');
            // 重置表单和状态
            setReaderId('');
            setShowReaderInfo(false);
            setReader(null);
        } catch (err) {
            setError(err instanceof Error ? err.message : '删除读者失败');
        } finally {
            setLoading(false);
        }
    };

    // 处理取消删除
    const handleCancelDelete = () => {
        setReaderId('');
        setShowReaderInfo(false);
        setReader(null);
        setError(null);
        setSuccess(null);
    };

    return (
        <div className="max-w-2xl mx-auto mt-10 bg-white p-6 rounded shadow">
            <h1 className="text-2xl font-bold mb-4">删除读者</h1>

            {/* 错误提示 */}
            {error && (
                <div className="bg-red-100 text-red-800 px-4 py-2 rounded mb-4">
                    {error}
                </div>
            )}

            {/* 成功提示 */}
            {success && (
                <div className="bg-green-100 text-green-800 px-4 py-2 rounded mb-4">
                    {success}
                </div>
            )}

            <div className="mb-4">
                <label htmlFor="readerId" className="block text-gray-700 font-medium mb-2">请输入读者ID:</label>
                <input
                    type="text"
                    id="readerId"
                    value={readerId}
                    onChange={handleInputChange}
                    className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    disabled={loading}
                />
            </div>

            {!showReaderInfo && (
                <button
                    onClick={handleSearch}
                    className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600 disabled:bg-blue-300"
                    disabled={loading || !readerId.trim()}
                >
                    {loading ? '查找中...' : '查找读者'}
                </button>
            )}

            {showReaderInfo && reader && (
                <>
                    <div className="border p-4 rounded-md bg-gray-50 mb-6">
                        <h2 className="font-bold text-xl mb-2">读者信息</h2>
                        <div className="text-gray-700">
                            <p><span className="font-medium">读者ID：</span> {reader.id}</p>
                            <p><span className="font-medium">账号：</span> {reader.account}</p>
                            <p><span className="font-medium">手机号：</span> {reader.phone}</p>
                            <p><span className="font-medium">邮箱：</span> {reader.email}</p>
                            <p><span className="font-medium">性别：</span> {
