import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderIndexStatus } from './indexSyncStatusView';

describe('indexSyncStatusView', () => {
  it('renders loading state when status is missing', () => {
    render(<>{renderIndexStatus(undefined)}</>);
    expect(screen.getByText('加载中')).not.toBeNull();
  });

  it('renders synced state without detail button', () => {
    render(
      <>{renderIndexStatus({ indexed: true, reason: '已同步', docCount: 3, status: 'READY', detail: null, indexName: 'idx' })}</>
    );
    expect(screen.getByText('已同步')).not.toBeNull();
    expect(screen.queryByRole('button', { name: '查看详情' })).toBeNull();
  });

  it('renders failed state and shows alert detail', () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    render(
      <>{renderIndexStatus({ indexed: false, reason: '失败', docCount: 2, status: 'ERROR', detail: 'boom', indexName: 'idx' })}</>
    );
    fireEvent.click(screen.getByRole('button', { name: '查看详情' }));
    expect(alertSpy).toHaveBeenCalledWith('状态: ERROR\n原因: 失败\n详情: boom\n索引: idx\n文档数: 2');
    alertSpy.mockRestore();
  });
});
