import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SemanticEditActionBar from './SemanticEditActionBar';

describe('SemanticEditActionBar', () => {
  it('shows edit button when not editing', () => {
    const onStartEditing = vi.fn();

    render(
      <SemanticEditActionBar
        editing={false}
        loading={false}
        saving={false}
        canSave={true}
        hasUnsavedChanges={true}
        onStartEditing={onStartEditing}
        onCancel={vi.fn()}
        onSave={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    expect(onStartEditing).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: '取消' })).toBeNull();
  });

  it('shows cancel and save states when editing', () => {
    const onCancel = vi.fn();

    render(
      <SemanticEditActionBar
        editing={true}
        loading={false}
        saving={false}
        canSave={true}
        hasUnsavedChanges={true}
        onStartEditing={vi.fn()}
        onCancel={onCancel}
        onSave={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: '保存' }).hasAttribute('disabled')).toBe(false);
  });

  it('disables save when save conditions are not met', () => {
    const view = render(
      <SemanticEditActionBar
        editing={true}
        loading={false}
        saving={false}
        canSave={false}
        hasUnsavedChanges={false}
        onStartEditing={vi.fn()}
        onCancel={vi.fn()}
        onSave={vi.fn()}
      />,
    );

    expect(within(view.container).getByRole('button', { name: '保存' }).hasAttribute('disabled')).toBe(true);
  });
});
