import React, { useEffect, useRef, useState, useCallback, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import FormContainer from './forms/FormContainer';
import { getLazyForm, preloadForm } from './forms/index';

// 复用 NewsSystemLayout 的侧边菜单风格，做成二级菜单组件
type SubItem = { id: string; label: string };

const SubMenu: React.FC<{
  items: SubItem[];
  ariaLabel?: string;
  onChange?: (id: string) => void;
  defaultActiveId?: string;
  title?: string;
  /** 可选：受控模式，外部指定当前激活 id（用于与 URL 同步） */
  activeId?: string;
}>
  = ({ items, ariaLabel = '二级菜单', onChange, defaultActiveId, title, activeId }) => {
  const [active, setActive] = useState(defaultActiveId ?? items[0]?.id);
  const containerRef = useRef<HTMLDivElement>(null);
  const btnRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const [canScroll, setCanScroll] = useState<{ left: boolean; right: boolean }>({ left: false, right: false });
  // 新增：活动下划线指示器位置与宽度
  const [indicator, setIndicator] = useState<{ left: number; width: number }>({ left: 0, width: 0 });

  // 当 items 变化且当前 active 不存在时，重置为第一个
  useEffect(() => {
    if (!items.length) return;
    if (!active || !items.find(i => i.id === active)) {
      setActive(items[0].id);
    }
  }, [items, active]);

  // 与外部受控 activeId 同步（仅同步，不触发 onChange，避免循环）
  useEffect(() => {
    if (!activeId) return;
    if (!items.some(i => i.id === activeId)) return;
    if (activeId === active) return;
    setActive(activeId);
  }, [activeId, items, active]);

  // 注意：不要在 effect 里自动 onChange，否则会把“state 同步”也变成“用户交互”，触发 URL 写入循环

  const focusButton = (id?: string) => {
    if (!id) return;
    const el = btnRefs.current[id];
    el?.focus();
  };

  const updateScrollButtons = useCallback(() => {
    const nav = containerRef.current;
    if (!nav) return;
    const { scrollLeft, scrollWidth, clientWidth } = nav;
    setCanScroll({
      left: scrollLeft > 0,
      right: scrollLeft + clientWidth < scrollWidth - 1,
    });
  }, []);

  // 计算并更新活动指示器位置与宽度（基于可视位置，而非内容坐标）
  const updateIndicator = useCallback(() => {
    const nav = containerRef.current;
    if (!nav || !active) return;
    const btn = btnRefs.current[active];
    if (!btn) return;
    const btnRect = btn.getBoundingClientRect();
    const navRect = nav.getBoundingClientRect();
    const left = btnRect.left - navRect.left; // 以可视区域为参考，避免滚动影响
    const width = btnRect.width;
    setIndicator({ left, width });
  }, [active]);

  useEffect(() => {
    updateScrollButtons();
    updateIndicator();
  }, [active, items, updateScrollButtons, updateIndicator]);

  useEffect(() => {
    const nav = containerRef.current;
    if (!nav) return;
    const onResize = () => {
      updateScrollButtons();
      updateIndicator();
    };
    const onScroll = () => {
      updateScrollButtons();
      updateIndicator();
    };
    window.addEventListener('resize', onResize);
    nav.addEventListener('scroll', onScroll);
    const id = window.setTimeout(() => {
      updateScrollButtons();
      updateIndicator();
    }, 0);
    return () => {
      window.removeEventListener('resize', onResize);
      nav.removeEventListener('scroll', onScroll);
      window.clearTimeout(id);
    };
  }, [updateScrollButtons, updateIndicator]);

  // 激活项变化时，将其滚动到视口中间
  useEffect(() => {
    const nav = containerRef.current;
    const id = active;
    if (!nav || !id) return;
    const btn = btnRefs.current[id];
    if (!btn) return;
    const btnRect = btn.getBoundingClientRect();
    const navRect = nav.getBoundingClientRect();
    const currentLeft = btnRect.left - navRect.left + nav.scrollLeft;
    const target = currentLeft - (nav.clientWidth - btnRect.width) / 2;
    nav.scrollTo({ left: target, behavior: 'smooth' });
  }, [active]);

  const onKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>, idx: number) => {
    if (!items.length) return;
    const last = items.length - 1;
    let nextIdx = idx;
    switch (e.key) {
      case 'ArrowRight':
        e.preventDefault();
        nextIdx = idx === last ? 0 : idx + 1;
        break;
      case 'ArrowLeft':
        e.preventDefault();
        nextIdx = idx === 0 ? last : idx - 1;
        break;
      case 'Home':
        e.preventDefault();
        nextIdx = 0;
        break;
      case 'End':
        e.preventDefault();
        nextIdx = last;
        break;
      default:
        return;
    }
    const nextId = items[nextIdx]?.id;
    if (!nextId || nextId === active) {
      focusButton(nextId);
      return;
    }
    setActive(nextId);
    onChange?.(nextId);
    focusButton(nextId);
  };

  const scrollByAmount = (delta: number) => {
    const nav = containerRef.current;
    if (!nav) return;
    nav.scrollBy({ left: delta, behavior: 'smooth' });
  };

  return (
    <div className="relative group bg-white ">
      {title && (
        <h2 className="text-2xl font-semibold text-gray-900 px-4 py-2">{title}</h2>
      )}
      <div className="relative">
        {/* 滚动容器 */}
        <nav
          ref={containerRef}
          aria-label={ariaLabel}
          className="relative bg-white shadow-lg p-2 overflow-x-auto scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-transparent ring-gray-200"
        >
          {/* 活动项下划线指示器 */}
          <span
            aria-hidden
            className="pointer-events-none absolute bottom-1 h-0.5 rounded-full bg-blue-500 transition-all duration-300 ease-out"
            style={{ left: `${indicator.left}px`, width: `${indicator.width}px` }}
          />

          <ul role="tablist" className="flex items-center gap-2 min-w-max">
            {items.map((it, idx) => {
              const selected = it.id === active;
              return (
                <li key={it.id} className="flex-shrink-0">
                  <button
                    ref={(el) => { btnRefs.current[it.id] = el; }}
                    type="button"
                    role="tab"
                    aria-selected={selected}
                    aria-controls={`${it.id}-panel`}
                    tabIndex={selected ? 0 : -1}
                    onKeyDown={(e) => onKeyDown(e, idx)}
                    onClick={() => {
                      if (it.id === active) return;
                      setActive(it.id);
                      onChange?.(it.id);
                    }}
                    onMouseEnter={() => {
                      // Prefetch active form chunks on hover to make first click instant.
                      preloadForm(it.id);
                    }}
                    className={`px-4 py-2 rounded-none whitespace-nowrap transition-colors duration-200 border-0 focus:outline-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-0 ${
                      selected
                        ? 'bg-white text-blue-600 shadow-none font-bold text-[18px]'
                        : 'bg-transparent text-gray-500 hover:text-blue-700 font-normal'
                    }`}
                  >
                    <span>{it.label}</span>
                  </button>
                </li>
              );
            })}
          </ul>

        </nav>

        {/* 左右滚动按钮 */}
        {canScroll.left && (
          <button
            type="button"
            aria-label="向左滚动"
            onClick={() => scrollByAmount(-220)}
            className="absolute left-1 top-1/2 -translate-y-1/2 rounded-none bg-white/85 shadow hover:bg-white focus:outline-none focus:ring-2 p-1.5 opacity-0 group-hover:opacity-100 transition-opacity duration-200"
          >
            <svg viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5" aria-hidden>
              <path d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
          </button>
        )}
        {canScroll.right && (
          <button
            type="button"
            aria-label="向右滚动"
            onClick={() => scrollByAmount(220)}
            className="absolute right-1 top-1/2 -translate-y-1/2 rounded-none bg-white/85 shadow hover:bg-white focus:outline-none focus:ring-2 p-1.5 opacity-0 group-hover:opacity-100 transition-opacity duration-200"
          >
            <svg viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5" aria-hidden>
              <path d="m10 6-1.41 1.41L13.17 12l-4.58 4.59L10 18l6-6z" />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
};

// 新增：SectionCard 支持在白色卡片下方渲染一个灰色表单容器
type SectionCardProps = { form?: React.ReactNode; className?: string } & React.PropsWithChildren;

const SectionCard: React.FC<SectionCardProps> = ({ children, form, className = '' }) => (
  <div className={`w-full h-full min-h-0 flex flex-col bg-[rgb(221,221,221)] ${className}`}>
    <div className="rounded-lg shadow p-8 w-full flex-none bg-[rgb(221,221,221)]">
      <div className="text-gray-700">{children}</div>
    </div>
    <FormContainer>
      {form}
    </FormContainer>
  </div>
);

// 新增：可复用的 AdminSection，收敛重复的 active + form + SubMenu 逻辑
type AdminSectionProps = { title: string; items: SubItem[]; defaultActiveId?: string; className?: string };

const AdminSection: React.FC<AdminSectionProps> = ({ title, items, defaultActiveId, className }) => {
  const [searchParams, setSearchParams] = useSearchParams();

  const activeFromUrl = searchParams.get('active') ?? undefined;
  const normalizedDefault = defaultActiveId ?? items[0]?.id;
  const initialActive = (activeFromUrl && items.some((i) => i.id === activeFromUrl)) ? activeFromUrl : normalizedDefault;

  const [active, setActive] = useState<string | undefined>(initialActive);

  // Debug 开关：需要时在控制台输入 localStorage.setItem('debugAdminNav','1')
  const debug = typeof window !== 'undefined' && window.localStorage?.getItem('debugAdminNav') === '1';

  // 当 URL 参数变化时，同步到 state（允许外部跳转定位到某个子表单）
  useEffect(() => {
    if (!activeFromUrl) return;
    if (!items.some((i) => i.id === activeFromUrl)) return;

    // 避免同值反复 setState 造成额外渲染/副作用链
    if (activeFromUrl === active) return;

    if (debug) console.log('[admin][active] sync from url -> state', { activeFromUrl, prev: active });
    setActive(activeFromUrl);
  }, [activeFromUrl, items, active, debug]);

  const ActiveForm = getLazyForm(active);

  return (
    <SectionCard
      className={className}
      form={
        <div className="space-y-3">
          <Suspense
            key={active}
            fallback={
              <div className="bg-white rounded-lg shadow p-4 text-sm text-gray-600">
                正在加载模块…
              </div>
            }
          >
            {ActiveForm ? <ActiveForm /> : null}
          </Suspense>
        </div>
      }
    >
      {/* 二级菜单 */}
      <SubMenu
        title={title}
        items={items}
        activeId={active}
        onChange={(id) => {
          if (id === active) return;

          if (debug) console.log('[admin][active] menu change', { next: id, prev: active });

          setActive(id);
          setSearchParams((prev) => {
            const current = prev.get('active') ?? undefined;
            if (current === id) return prev;

            const next = new URLSearchParams(prev);
            next.set('active', id);
            return next;
          }, { replace: true });
        }}
        defaultActiveId={initialActive}
      />
    </SectionCard>
  );
};

export const ContentMgmtPage: React.FC = () => (
  <AdminSection
    title="内容管理"
    items={[
      { id: 'board-management', label: '版块管理' },
      { id: 'post', label: '帖子管理' },
      { id: 'comment', label: '评论管理' },
      { id: 'tags', label: '标签体系管理' },
    ]}
  />
);

export const ReviewCenterPage: React.FC = () => (
  <AdminSection
    title="审核中心"
    items={[
      { id: 'queue', label: '审核队列面板' },
      { id: 'rules', label: '规则过滤层' },
      { id: 'embed', label: '嵌入相似检测' },
      { id: 'llm', label: 'LLM 审核层' },
      { id: 'fallback', label: '置信回退机制' },
      { id: 'logs', label: '审核日志与追溯' },
      { id: 'risk-tags', label: '风险标签生成' },
    ]}
  />
);

export const SemanticBoostPage: React.FC = () => (
  <AdminSection
    title="语义增强"
    items={[
      { id: 'title-gen', label: '标题生成' },
      { id: 'multi-label', label: '多任务标签生成' },
      { id: 'summary', label: '帖子摘要' },
      { id: 'translate', label: '翻译' },
    ]}
  />
);

export const RetrievalRagPage: React.FC = () => (
  <AdminSection
    title="检索与 RAG"
    items={[
      { id: 'index', label: '向量索引构建' },
      { id: 'hybrid', label: 'Hybrid 检索配置' },
      { id: 'context', label: '动态上下文裁剪' },
      { id: 'citation', label: '引用与来源展示配置' },
    ]}
  />
);

export const MetricsMonitorPage: React.FC = () => (
  <AdminSection
    title="评估与监控"
    items={[
      { id: 'metrics', label: '指标采集层' },
      { id: 'abtest', label: '实验对比脚本' },
      { id: 'token', label: 'Token 成本统计' },
      { id: 'label-quality', label: '标签质量评估工具' },
      { id: 'cost', label: '审核成本分析' },
    ]}
  />
);

export const UsersRBACPage: React.FC = () => (
  <AdminSection
    title="用户与权限"
    items={[
      { id: 'user-role', label: '用户管理' },
      { id: 'roles', label: '角色管理' },
      { id: 'matrix', label: '权限管理' },
      { id: '2fa', label: '高权限操作 2FA 策略' },
    ]}
  />
);
