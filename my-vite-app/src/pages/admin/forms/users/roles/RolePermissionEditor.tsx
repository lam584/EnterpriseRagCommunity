import React from 'react';
import {FaSave, FaSync} from 'react-icons/fa';

import {Button} from '../../../../../components/ui/button';
import {Input} from '../../../../../components/ui/input';

type RolePermissionEditorProps = {
    mode: 'create' | 'edit';
    roleId?: number | null;
    matrixLoading: boolean;
    submitting: boolean;
    roleNameInput: string;
    setRoleNameInput: (v: string) => void;
    permKeyword: string;
    setPermKeyword: (v: string) => void;
    onSubmit: () => void;
    submitLabel: string;
    onRefreshAccess?: () => void;
    bulkBar: React.ReactNode;
    matrix: React.ReactNode;
};

const RolePermissionEditor: React.FC<RolePermissionEditorProps> = props => {
    return (
        <div className="space-y-4">
            {/* Single-row toolbar: roleId + roleName + search + bulk + actions */}
            <div className="flex flex-wrap items-center gap-2">
                {props.roleId ? (
                    <div className="text-sm text-gray-600 whitespace-nowrap">
                        角色id: <span className="font-semibold text-gray-900">{props.roleId}</span>
                    </div>
                ) : null}

                <div className="flex items-center gap-2">
                    <span className="text-sm text-gray-600 whitespace-nowrap">角色名</span>
                    <Input
                        value={props.roleNameInput}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => props.setRoleNameInput(e.target.value)}
                        placeholder="请输入角色名"
                        className="w-full md:w-[200px]"
                    />
                </div>

                <div className="flex items-center gap-2">
                    <span className="text-sm text-gray-600 whitespace-nowrap">搜索权限</span>
                    <Input
                        value={props.permKeyword}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => props.setPermKeyword(e.target.value)}
                        placeholder="resource / action / 描述"
                        className="w-full md:w-[240px]"
                    />
                </div>

                <div className="flex flex-wrap items-center gap-2 text-xs">{props.bulkBar}</div>

                <div className="ml-auto flex flex-wrap items-center gap-2">
                    {props.onRefreshAccess ? (
                        <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={props.onRefreshAccess}
                            title="手动刷新当前会话权限；若你刚刚移除了自己访问本页的权限，刷新后可能会被路由守卫重定向"
                        >
                            <FaSync className="mr-2" /> 刷新当前会话权限
                        </Button>
                    ) : null}

                    <Button type="button" onClick={props.onSubmit} disabled={props.submitting || props.matrixLoading}>
                        <FaSave className="mr-2" /> {props.submitting ? `${props.submitLabel}中…` : props.submitLabel}
                    </Button>
                </div>
            </div>

            <div className="rounded-md p-4">{props.matrixLoading ? <div className="text-base text-gray-500">加载中...</div> : props.matrix}</div>
        </div>
    );
};

export default RolePermissionEditor;
export type {RolePermissionEditorProps};
