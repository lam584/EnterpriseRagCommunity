type Props = {
  editing: boolean;
  loading: boolean;
  saving: boolean;
  canSave: boolean;
  hasUnsavedChanges: boolean;
  onStartEditing: () => void;
  onCancel: () => void;
  onSave: () => void;
};

export default function SemanticEditActionBar({
  editing,
  loading,
  saving,
  canSave,
  hasUnsavedChanges,
  onStartEditing,
  onCancel,
  onSave,
}: Props) {
  if (!editing) {
    return (
      <button
        type="button"
        className="rounded border px-3 py-1.5 text-sm"
        onClick={onStartEditing}
        disabled={loading || saving}
      >
        编辑
      </button>
    );
  }

  return (
    <>
      <button
        type="button"
        className="rounded border px-3 py-1.5 text-sm"
        onClick={onCancel}
        disabled={saving || loading}
      >
        取消
      </button>
      <button
        type="button"
        className="rounded bg-blue-600 text-white px-3 py-1.5 text-sm disabled:bg-blue-300"
        onClick={onSave}
        disabled={!canSave || !hasUnsavedChanges}
      >
        {saving ? '保存中...' : '保存'}
      </button>
    </>
  );
}
