import type {RiskTagDTO} from '../../../../services/riskTagService';

type RiskEditorModalProps = {
    open: boolean;
    saving: boolean;
    loading: boolean;
    error: string | null;
    selected: string[];
    query: string;
    options: RiskTagDTO[];
    newName: string;
    onClose: () => void;
    onCancel: () => void;
    onSave: () => void;
    onQueryChange: (value: string) => void;
    onRemoveSelected: (slug: string) => void;
    onAddOption: (slug: string) => void;
    onNewNameChange: (value: string) => void;
    onCreateAndSelect: () => void;
};

export function RiskEditorModal(props: RiskEditorModalProps) {
    const {
        open,
        saving,
        loading,
        error,
        selected,
        query,
        options,
        newName,
        onClose,
        onCancel,
        onSave,
        onQueryChange,
        onRemoveSelected,
        onAddOption,
        onNewNameChange,
        onCreateAndSelect,
    } = props;

    if (!open) return null;

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-lg shadow-lg w-full max-w-2xl max-h-[85vh] overflow-auto">
                <div className="flex items-center justify-between px-4 py-3 border-b">
                    <div className="font-semibold">编辑风险标签</div>
                    <button
                        type="button"
                        className="rounded border px-3 py-1 hover:bg-gray-50 disabled:opacity-60"
                        onClick={onClose}
                        disabled={saving}
                    >
                        关闭
                    </button>
                </div>

                <div className="p-4 space-y-4">
                    {error ? <div className="text-sm text-red-700">{error}</div> : null}

                    <div className="space-y-2">
                        <div className="text-sm font-medium text-gray-700">已选择</div>
                        {selected.length ? (
                            <div className="flex flex-wrap gap-2">
                                {selected.map((t) => (
                                    <span
                                        key={t}
                                        className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-amber-200 bg-amber-50 text-sm text-amber-900"
                                        title={t}
                                    >
                    <span>{t}</span>
                    <button
                        type="button"
                        className="text-amber-800 hover:text-amber-950"
                        onClick={() => onRemoveSelected(t)}
                        disabled={saving}
                    >
                      ×
                    </button>
                  </span>
                                ))}
                            </div>
                        ) : (
                            <div className="text-sm text-gray-500">（未选择）</div>
                        )}
                    </div>

                    <div className="space-y-2">
                        <div className="text-sm font-medium text-gray-700">搜索已有风险标签</div>
                        <input
                            className="w-full rounded border px-3 py-2 border-gray-300"
                            placeholder="输入关键字搜索 name/slug"
                            value={query}
                            onChange={(e) => onQueryChange(e.target.value)}
                            disabled={saving}
                        />
                        {loading ? <div className="text-sm text-gray-500">加载中...</div> : null}
                        {options.length ? (
                            <div className="max-h-[260px] overflow-auto border rounded">
                                {options.map((t) => (
                                    <button
                                        key={t.id}
                                        type="button"
                                        className="w-full text-left px-3 py-2 hover:bg-gray-50 disabled:opacity-60"
                                        onClick={() => onAddOption(t.slug)}
                                        disabled={saving}
                                        title={t.slug}
                                    >
                                        <div className="flex items-center justify-between gap-3">
                                            <span className="text-sm text-gray-900">{t.name}</span>
                                            <span className="text-xs text-gray-500">{t.slug}</span>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        ) : (
                            <div className="text-sm text-gray-500">（无匹配结果）</div>
                        )}
                    </div>

                    <div className="space-y-2">
                        <div className="text-sm font-medium text-gray-700">快速新增</div>
                        <div className="flex gap-2">
                            <input
                                className="flex-1 rounded border px-3 py-2 border-gray-300"
                                placeholder="输入新风险标签名称（将自动生成 slug）"
                                value={newName}
                                onChange={(e) => onNewNameChange(e.target.value)}
                                disabled={saving}
                            />
                            <button
                                type="button"
                                className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                                onClick={onCreateAndSelect}
                                disabled={saving || !newName.trim()}
                            >
                                新增并选中
                            </button>
                        </div>
                    </div>

                    <div className="flex justify-end gap-2">
                        <button
                            type="button"
                            className="rounded border px-4 py-2 hover:bg-gray-50 disabled:opacity-60"
                            onClick={onCancel}
                            disabled={saving}
                        >
                            取消
                        </button>
                        <button
                            type="button"
                            className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
                            onClick={onSave}
                            disabled={saving}
                        >
                            {saving ? '保存中...' : '保存'}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

type TextPreviewModalProps = {
    open: boolean;
    title: string;
    text: string;
    onClose: () => void;
};

export function TextPreviewModal(props: TextPreviewModalProps) {
    const {open, title, text, onClose} = props;
    if (!open) return null;

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-lg shadow-lg w-full max-w-3xl max-h-[85vh] overflow-auto">
                <div className="flex items-center justify-between px-4 py-3 border-b">
                    <div className="font-semibold">{title || '详情'}</div>
                    <button
                        type="button"
                        className="rounded border px-3 py-1 hover:bg-gray-50"
                        onClick={onClose}
                    >
                        关闭
                    </button>
                </div>
                <div className="p-4">
          <pre className="whitespace-pre-wrap text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[70vh]">
            {text || '—'}
          </pre>
                </div>
            </div>
        </div>
    );
}
