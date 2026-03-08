import React, { useEffect } from 'react';

export type DetailDialogTab = {
  id: string;
  label: React.ReactNode;
  disabled?: boolean;
};

export interface DetailDialogProps {
  open: boolean;
  onClose: () => void;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  hintText?: React.ReactNode;
  headerActions?: React.ReactNode;
  tabs?: DetailDialogTab[];
  activeTabId?: string;
  onTabChange?: (tabId: string) => void;
  variant?: 'center' | 'drawerRight';
  containerClassName?: string;
  bodyClassName?: string;
  lockBodyScroll?: boolean;
  children: React.ReactNode;
}

const defaultHint = 'Esc / 点击遮罩 关闭';

const tabBtnClass = (active: boolean) =>
  `rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${active ? 'bg-white text-gray-900 shadow-sm ring-1 ring-black/5' : 'text-gray-600 hover:text-gray-900'}`;

const DetailDialog: React.FC<DetailDialogProps> = ({
  open,
  onClose,
  title,
  subtitle,
  hintText,
  headerActions,
  tabs,
  activeTabId,
  onTabChange,
  variant = 'center',
  containerClassName,
  bodyClassName,
  lockBodyScroll = true,
  children,
}) => {
  useEffect(() => {
    if (!open) return;
    const prevOverflow = document.body.style.overflow;
    if (lockBodyScroll) document.body.style.overflow = 'hidden';
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      if (lockBodyScroll) document.body.style.overflow = prevOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [lockBodyScroll, onClose, open]);

  if (!open) return null;

  const containerBase =
    variant === 'drawerRight'
      ? `h-full w-full max-w-2xl bg-white shadow-xl flex flex-col ${containerClassName ?? ''}`
      : `w-full max-w-5xl max-h-[calc(100vh-3rem)] flex flex-col overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-black/10 animate-in zoom-in-95 duration-200 ${
          containerClassName ?? ''
        }`;

  const bodyBase =
    bodyClassName ??
    (variant === 'drawerRight' ? 'flex-1 overflow-auto p-5 space-y-4' : 'flex-1 overflow-y-auto p-5 space-y-4');

  const showTabs = Boolean(tabs?.length) && Boolean(activeTabId) && typeof onTabChange === 'function';

  return (
    <div
      className={
        variant === 'drawerRight'
          ? 'fixed inset-0 z-50 flex items-stretch justify-end bg-black/30'
          : 'fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 sm:p-6 animate-in fade-in duration-200'
      }
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className={containerBase} onMouseDown={(e) => e.stopPropagation()}>
        <div className="sticky top-0 z-10 border-b bg-white/90 backdrop-blur">
          <div className="flex items-start justify-between gap-3 px-5 pt-4 pb-3">
            <div className="min-w-0">
              <div className="truncate text-base font-semibold text-gray-900">{title}</div>
              {subtitle ? <div className="mt-1 text-xs text-gray-500 truncate">{subtitle}</div> : null}
              {hintText === null ? null : (
                <div className={`${subtitle ? 'mt-0.5' : 'mt-1'} text-xs text-gray-500`}>{hintText ?? defaultHint}</div>
              )}
            </div>
            <div className="flex items-center gap-2">
              {headerActions}
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-700 shadow-sm hover:bg-gray-50 active:bg-gray-100"
                onClick={onClose}
              >
                <svg viewBox="0 0 20 20" fill="currentColor" className="h-4 w-4 text-gray-500" aria-hidden="true">
                  <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                </svg>
                关闭
              </button>
            </div>
          </div>
          {showTabs ? (
            <div className="px-5 pb-4">
              <div className="inline-flex rounded-lg bg-gray-100 p-1">
                {tabs!.map((t) => {
                  const active = t.id === activeTabId;
                  const disabled = Boolean(t.disabled);
                  return (
                    <button
                      key={t.id}
                      type="button"
                      className={`${tabBtnClass(active)} ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
                      onClick={() => {
                        if (disabled) return;
                        onTabChange!(t.id);
                      }}
                      disabled={disabled}
                    >
                      {t.label}
                    </button>
                  );
                })}
              </div>
            </div>
          ) : null}
        </div>
        <div className={bodyBase}>{children}</div>
      </div>
    </div>
  );
};

export default DetailDialog;

