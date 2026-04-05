type EditToggleButtonProps = {
  editing: boolean;
  loading?: boolean;
  saving?: boolean;
  onEdit: () => void;
  onCancel: () => void;
};

export function EditToggleButton({ editing, loading = false, saving = false, onEdit, onCancel }: EditToggleButtonProps) {
  return (
    <button
      type="button"
      onClick={editing ? onCancel : onEdit}
      disabled={loading || saving}
      className={`px-3 py-1.5 rounded-md border border-gray-300 hover:bg-gray-50 disabled:opacity-60 text-sm ${
        editing ? 'bg-gray-50' : 'bg-white'
      }`}
    >
      {editing ? '取消编辑' : '编辑'}
    </button>
  );
}
