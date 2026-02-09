import React from 'react';

export const DiscoverPage: React.FC = () => (
  <div>
    <h2 className="text-xl font-semibold mb-4">浏览与发现</h2>
    <p className="text-gray-600">探索热度榜、版块浏览、标签浏览。</p>
      <a href="/admin" target="_blank" rel="noopener noreferrer">点击进入管理员后台</a>
  </div>
);

export const PostsPage: React.FC = () => (
  <div>
    <h2 className="text-xl font-semibold mb-4">帖子</h2>
    <p className="text-gray-600">发布新帖、编辑与草稿箱。</p>
  </div>
);

export const InteractPage: React.FC = () => (
  <div>
    <h2 className="text-xl font-semibold mb-4">互动记录</h2>
    <p className="text-gray-600">评论与回复、点赞与收藏、举报。</p>
  </div>
);

export const AssistantPage: React.FC = () => (
  <div>
    <h2 className="text-xl font-semibold mb-4">智能助手（RAG）</h2>
    <p className="text-gray-600">问答入口、引用标注与来源展示、幻觉初步检测（可选）。</p>
  </div>
);

export const AccountPage: React.FC = () => (
  <div>
    <h2 className="text-xl font-semibold mb-4">账户</h2>
    <p className="text-gray-600">注册/登录/注销/找回，角色与权限，安全设置（可选）。</p>
  </div>
);
