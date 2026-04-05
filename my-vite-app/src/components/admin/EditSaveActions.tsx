type EditSaveActionsProps = {
  editing: boolean;
  loading?: boolean;
  canWrite?: boolean;
  hasUnsavedChanges?: boolean;
  onEdit: () => void;
  onCancel: () => void;
  onSave: () => void;
};

const btnPrimaryClass =
  'inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';
const btnSecondaryClass =
  'inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200';

export function EditSaveActions({
  editing,
  loading = false,
  canWrite = true,
  hasUnsavedChanges = false,
  onEdit,
  onCancel,
  onSave,
}: EditSaveActionsProps) {
  if (!editing) {
    return (
      <button type="button" className={btnSecondaryClass} onClick={onEdit} disabled={loading || !canWrite}>
        编辑
      </button>
    );
  }

  return (
    <>
      <button type="button" className={btnSecondaryClass} onClick={onCancel} disabled={loading}>
        取消
      </button>
      <button
        type="button"
        className={btnPrimaryClass}
        onClick={onSave}
        disabled={loading || !canWrite || !hasUnsavedChanges}
      >
        保存
      </button>
    </>
  );
}
