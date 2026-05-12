// user_roles 已移除：这个 service 仅保留占位，避免旧页面引用导致编译失败。
export const rolesService = {
  async getAllRoles(): Promise<never> {
    throw new Error('user_roles 已删除：不再提供角色列表接口');
  },
  async queryRoles(): Promise<never> {
    throw new Error('user_roles 已删除：不再提供角色管理接口');
  },
  async getRoleById(): Promise<never> {
    throw new Error('user_roles 已删除：不再提供角色管理接口');
  },
  async createRole(): Promise<never> {
    throw new Error('user_roles 已删除：不再提供角色管理接口');
  },
  async updateRole(): Promise<never> {
    throw new Error('user_roles 已删除：不再提供角色管理接口');
  },
  async deleteRole(): Promise<never> {
    throw new Error('user_roles 已删除：不再提供角色管理接口');
  },
};
