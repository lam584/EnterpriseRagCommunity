export type PortalSubNavItem = {
  id: string;
  label: string;
  /** relative path under section, e.g. 'home' */
  path: string;
  description?: string;
};

export type PortalSection = {
  id: 'discover' | 'posts' | 'interact' | 'assistant' | 'account';
  label: string;
  basePath: `/portal/${string}`;
  children: PortalSubNavItem[];
};

export const portalSections: PortalSection[] = [
  {
    id: 'discover',
    label: '浏览与发现',
    basePath: '/portal/discover',
    children: [
      { id: 'home', label: '首页', path: 'home', description: '新鲜内容' },
      { id: 'trending', label: '热榜', path: 'trending', description: '当前社区热门内容' },
      { id: 'boards', label: '版块', path: 'boards', description: '按版块浏览内容' },
      { id: 'tags', label: '标签', path: 'tags', description: '按标签筛选与发现' },
      { id: 'search', label: '搜索', path: 'search', description: '关键词搜索帖子与用户' },
    ],
  },
  {
    id: 'posts',
    label: '帖子',
    basePath: '/portal/posts',
    children: [
      // { id: 'feed', label: '信息流', path: 'feed', description: '最新/关注/推荐帖子流' },
      { id: 'create', label: '发帖', path: 'create', description: '发布新帖子' },
      { id: 'drafts', label: '草稿箱', path: 'drafts', description: '未发布内容' },
      { id: 'mine', label: '我的帖子', path: 'mine', description: '我发布过的帖子' },
      { id: 'bookmarks', label: '收藏', path: 'bookmarks', description: '我收藏的帖子' },
    ],
  },
  {
    id: 'interact',
    label: '互动记录',
    basePath: '/portal/interact',
    children: [
      { id: 'notifications', label: '通知', path: 'notifications', description: '互动通知总览' },
      { id: 'replies', label: '回复', path: 'replies', description: '回复我的评论/帖子' },
      { id: 'likes', label: '点赞', path: 'likes', description: '我点赞过的内容' },
      { id: 'mentions', label: '提及', path: 'mentions', description: '有人 @ 我' },
      { id: 'reports', label: '举报', path: 'reports', description: '我的举报记录' },
    ],
  },
  {
    id: 'assistant',
    label: '智能助手',
    basePath: '/portal/assistant',
    children: [
      { id: 'chat', label: '对话', path: 'chat', description: 'RAG 问答入口' },
      { id: 'history', label: '历史', path: 'history', description: '历史会话与追问' },
      { id: 'collections', label: '收藏夹', path: 'collections', description: '引用/知识片段收藏' },
      { id: 'settings', label: '设置', path: 'settings', description: '检索与生成偏好' },
    ],
  },
  {
    id: 'account',
    label: '账户',
    basePath: '/portal/account',
    children: [
      { id: 'profile', label: '个人资料', path: 'profile', description: '昵称、头像、简介等' },
      { id: 'security', label: '安全', path: 'security', description: '密码、登录设备与风控' },
      { id: 'preferences', label: '偏好', path: 'preferences', description: '语言、展示与通知偏好' },
      { id: 'connections', label: '绑定', path: 'connections', description: '邮箱/手机/第三方账号' },
    ],
  },
];

/**
 * 用于“卡片内标签/操作条”这类区域：收敛容器宽度，避免覆盖卡片点击区域。
 * 在对应组件里把：className="mt-3 flex flex-wrap items-center gap-2"
 * 替换为：className={`${cardInlineRowClass} ...`}
 */
export const cardInlineRowClass =
  'mt-3 inline-flex max-w-full flex-wrap items-center gap-2 align-top';

export function getPortalSection(id: PortalSection['id']): PortalSection {
  const s = portalSections.find((x) => x.id === id);
  if (!s) throw new Error(`Unknown portal section: ${id}`);
  return s;
}
