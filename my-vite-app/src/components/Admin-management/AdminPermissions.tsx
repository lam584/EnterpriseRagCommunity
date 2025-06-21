// src/components/editor-management/EditorPermissions.tsx
import React, { useState } from 'react';

// 定义权限类型
interface Permission {
    id: string;
    name: string;
    description: string;
    checked: boolean;
}

// 定义管理员员类型
interface Editor {
    id: string;
    username: string;
    name: string;
    role: string;
    department: string;
}

const EditorPermissions: React.FC = () => {
    const [selectedEditor, setSelectedEditor] = useState<Editor | null>(null);
    const [editorSearchTerm, setEditorSearchTerm] = useState('');
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState({ type: '', text: '' });

    // 模拟管理员员数据
    const mockEditors: Editor[] = [
        { id: '1', username: 'editor1', name: '张管理员', role: '普通管理员', department: '新闻部' },
        { id: '2', username: 'editor2', name: '王管理员', role: '高级管理员', department: '体育部' },
        { id: '3', username: 'chief1', name: '李主编', role: '主编', department: '社会新闻部' },
        { id: '4', username: 'editor3', name: '赵管理员', role: '普通管理员', department: '娱乐部' },
        { id: '5', username: 'senior1', name: '刘高编', role: '高级管理员', department: '科技部' },
    ];

    // 模拟权限列表
    const [permissions, setPermissions] = useState<Permission[]>([
        { id: 'create_news', name: '发布新闻', description: '允许管理员员创建并发布新闻', checked: false },
        { id: 'edit_news', name: '管理员新闻', description: '允许管理员员管理员任何新闻', checked: false },
        { id: 'delete_news', name: '删除新闻', description: '允许管理员员删除任何新闻', checked: false },
        { id: 'manage_categories', name: '管理新闻分类', description: '允许管理员员创建、管理员和删除新闻分类', checked: false },
        { id: 'approve_comments', name: '审核评论', description: '允许管理员员审核和管理用户评论', checked: false },
        { id: 'view_stats', name: '查看统计', description: '允许管理员员查看新闻统计数据', checked: false },
        { id: 'manage_editors', name: '管理其他管理员员', description: '允许管理员员管理其他管理员员信息（仅限主编）', checked: false },
    ]);

    // 搜索管理员员
    const handleSearchEditor = () => {
        if (!editorSearchTerm.trim()) {
            setMessage({ type: 'error', text: '请输入搜索关键词' });
            return;
        }

        setLoading(true);
        setMessage({ type: '', text: '' });

        // 模拟API搜索
        setTimeout(() => {
            const results = mockEditors.filter(editor =>
                editor.username.includes(editorSearchTerm) ||
                editor.name.includes(editorSearchTerm) ||
                editor.department.includes(editorSearchTerm)
            );

            if (results.length === 0) {
                setMessage({ type: 'info', text: '未找到匹配的管理员员' });
                setSelectedEditor(null);
            } else {
                // 取第一个匹配的管理员员
                setSelectedEditor(results[0]);

                // 根据角色设置默认权限
                let updatedPermissions = [...permissions];
                if (results[0].role === '主编') {
                    // 主编拥有全部权限
                    updatedPermissions = updatedPermissions.map(p => ({ ...p, checked: true }));
                } else if (results[0].role === '高级管理员') {
                    // 高级管理员拥有除管理其他管理员员外的权限
                    updatedPermissions = updatedPermissions.map(p => ({
                        ...p,
                        checked: p.id !== 'manage_editors'
                    }));
                } else {
                    // 普通管理员只有基本权限
                    updatedPermissions = updatedPermissions.map(p => ({
                        ...p,
                        checked: ['create_news', 'edit_news', 'view_stats'].includes(p.id)
                    }));
                }
                setPermissions(updatedPermissions);
            }

            setLoading(false);
        }, 800);
    };

    // 处理权限变更
    const handlePermissionChange = (permissionId: string, checked: boolean) => {
        setPermissions(permissions.map(p =>
            p.id === permissionId ? { ...p, checked } : p
        ));
    };

    // 保存权限设置
    const handleSavePermissions = () => {
        if (!selectedEditor) {
            return;
        }

        setMessage({ type: 'info', text: '正在保存权限设置...' });

        // 获取已选择的权限ID列表
        const selectedPermissions = permissions
            .filter(p => p.checked)
            .map(p => p.id);

        // 模拟API请求
        setTimeout(() => {
            console.log(`为管理员员 ${selectedEditor.username} 设置权限:`, selectedPermissions);
            setMessage({ type: 'success', text: '权限设置已成功保存！' });
        }, 1000);
    };

    return (
        <div>
            <h2 className="text-xl font-bold mb-4">管理员员权限管理</h2>

            {/* 搜索管理员员 */}
            <div className="mb-6 p-4 bg-gray-50 rounded-md">
                <div className="flex">
                    <input
                        type="text"
                        value={editorSearchTerm}
                        onChange={(e) => setEditorSearchTerm(e.target.value)}
                        placeholder="输入用户名、姓名或部门搜索管理员员"
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-l-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                    />
                    <button
                        onClick={handleSearchEditor}
                        disabled={loading}
                        className="px-4 py-2 bg-purple-600 text-white rounded-r-md hover:bg-purple-700 focus:outline-none disabled:bg-purple-300"
                    >
                        {loading ? '搜索中...' : '搜索'}
                    </button>
                </div>
            </div>

            {message.text && (
                <div className={`p-3 rounded mb-4 ${
                    message.type === 'error' ? 'bg-red-100 text-red-700' : 
                    message.type === 'success' ? 'bg-green-100 text-green-700' :
                    'bg-blue-100 text-blue-700'
                }`}>
                    {message.text}
                </div>
            )}

            {/* 管理员员信息与权限设置 */}
            {selectedEditor && (
                <div>
                    <div className="mb-6 p-4 bg-purple-50 rounded-md">
                        <h3 className="font-bold text-lg mb-2">管理员员信息</h3>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <span className="text-gray-600">用户名:</span>
                                <span className="ml-2 font-medium">{selectedEditor.username}</span>
                            </div>
                            <div>
                                <span className="text-gray-600">姓名:</span>
                                <span className="ml-2 font-medium">{selectedEditor.name}</span>
                            </div>
                            <div>
                                <span className="text-gray-600">角色:</span>
                                <span className="ml-2 font-medium">{selectedEditor.role}</span>
                            </div>
                            <div>
                                <span className="text-gray-600">部门:</span>
                                <span className="ml-2 font-medium">{selectedEditor.department}</span>
                            </div>
                        </div>
                    </div>

                    <h3 className="font-bold text-lg mb-3">权限设置</h3>

                    <div className="border rounded-md">
                        {permissions.map((permission) => (
                            <div
                                key={permission.id}
                                className="flex items-center p-3 border-b last:border-b-0"
                            >
                                <input
                                    type="checkbox"
                                    id={permission.id}
                                    checked={permission.checked}
                                    onChange={(e) => handlePermissionChange(permission.id, e.target.checked)}
                                    className="h-5 w-5 text-purple-600 rounded focus:ring-purple-500"
                                />
                                <div className="ml-3">
                                    <label htmlFor={permission.id} className="font-medium">
                                        {permission.name}
                                    </label>
                                    <p className="text-sm text-gray-500">{permission.description}</p>
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="mt-6 flex justify-end">
                        <button
                            onClick={handleSavePermissions}
                            className="px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 focus:outline-none"
                        >
                            保存权限设置
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default EditorPermissions;
