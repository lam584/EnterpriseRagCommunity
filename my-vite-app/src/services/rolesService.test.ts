import { describe, expect, it } from 'vitest';
import { rolesService } from './rolesService';

describe('rolesService', () => {
  it('getAllRoles rejects with deleted message', async () => {
    await expect(rolesService.getAllRoles()).rejects.toThrow('user_roles 已删除：不再提供角色列表接口');
  });

  it('other role methods reject with deleted message', async () => {
    await expect(rolesService.queryRoles()).rejects.toThrow('user_roles 已删除：不再提供角色管理接口');
    await expect(rolesService.getRoleById()).rejects.toThrow('user_roles 已删除：不再提供角色管理接口');
    await expect(rolesService.createRole()).rejects.toThrow('user_roles 已删除：不再提供角色管理接口');
    await expect(rolesService.updateRole()).rejects.toThrow('user_roles 已删除：不再提供角色管理接口');
    await expect(rolesService.deleteRole()).rejects.toThrow('user_roles 已删除：不再提供角色管理接口');
  });
});
