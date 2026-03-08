export type PortalSubNavItem = {
  id: string;
  label: string;
  /** relative path under section, e.g. 'home' */
  path: string;
  to?: `/${string}`;
  description?: string;
};

export type PortalSection = {
  id: 'discover' | 'posts' | 'interact' | 'assistant' | 'account' | 'compose' | 'search' | 'moderation';
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
      { id: 'hot', label: '热榜', path: 'hot', description: '当前社区热门内容' },
      { id: 'boards', label: '版块', path: 'boards', description: '按版块浏览内容' },
    ],
  },
  {
    id: 'search',
    label: '搜索',
    basePath: '/portal/search',
    children: [],
  },
  {
    id: 'interact',
    label: '通知',
    basePath: '/portal/interact',
    children: [
      { id: 'all', label: '全部', path: 'all', description: '全部通知' },
      { id: 'replies', label: '回复', path: 'replies', description: '回复我的评论/帖子' },
      { id: 'likes', label: '点赞', path: 'likes', description: '我点赞过的内容' },
      { id: 'mentions', label: '提及', path: 'mentions', description: '有人 @ 我' },
      { id: 'reports', label: '举报', path: 'reports', description: '我的举报记录' },
      { id: 'security', label: '安全', path: 'security', description: '账号安全通知' },
      { id: 'moderation', label: '审核', path: 'moderation', description: '审核通知' },
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
    id: 'moderation',
    label: '版主中心',
    basePath: '/portal/moderation',
    children: [
      { id: 'queue', label: '审核队列', path: 'queue', description: '处理你负责版块的待审内容' },
      { id: 'logs', label: '我的治理记录', path: 'logs', description: '查看并导出自己的治理审计记录' },
    ],
  },
  {
    id: 'account',
    label: '账户',
    basePath: '/portal/account',
    children: [
      { id: 'profile', label: '个人资料', path: 'profile', description: '昵称、头像、简介等' },
      { id: 'security', label: '安全', path: 'security', description: '密码、二次验证与邮箱安全' },
      { id: 'preferences', label: '偏好', path: 'preferences', description: '语言、展示与通知偏好' },
      { id: 'mine', label: '我的帖子', path: 'mine', description: '我发布过的帖子' },
      { id: 'bookmarks', label: '收藏', path: 'bookmarks', description: '我收藏的帖子' },
    ],
  },
    {
        id: 'compose',
        label: '发帖',
        basePath: '/portal/posts',
        children: [
            {id: 'create', label: '发帖', path: 'create', description: '创建新帖子'},
            {id: 'drafts', label: '草稿箱', path: 'drafts', description: '未发布内容'},
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

export function getPortalSection(id: PortalSection['id'] | string): PortalSection {
  const s = portalSections.find((x) => x.id === id);
  if (!s) throw new Error(`Unknown portal section: ${id}`);
  return s;
}
