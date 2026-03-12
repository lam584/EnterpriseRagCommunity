import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import type { SpringPage } from '../../../../types/page';
import type { AccessLogDTO } from '../../../../services/accessLogService';

vi.mock('../../../../services/auditLogService', () => ({
  adminExportAuditLogsCsv: vi.fn(),
  adminGetAuditLogDetail: vi.fn(),
  adminListAuditLogs: vi.fn(),
}));

vi.mock('../../../../services/accessLogService', () => ({
  adminExportAccessLogsCsv: vi.fn(),
  adminGetAccessLogDetail: vi.fn(),
  adminListAccessLogs: vi.fn(),
}));

vi.mock('../../../../services/logRetentionService', () => ({
  adminGetLogRetentionConfig: vi.fn(),
  adminUpdateLogRetentionConfig: vi.fn(),
}));

vi.mock('../../../../components/common/DetailDialog', () => ({
  default: ({ open, children }: { open: boolean; children?: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

import { adminListAccessLogs } from '../../../../services/accessLogService';
import { adminGetLogRetentionConfig } from '../../../../services/logRetentionService';
import GlobalLogsForm from './global-logs';

function buildAccessPage(overrides: Partial<SpringPage<AccessLogDTO>> = {}): SpringPage<AccessLogDTO> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 15,
    number: 0,
    ...overrides,
  };
}

describe('GlobalLogsForm', () => {
  const mockAdminListAccessLogs = vi.mocked(adminListAccessLogs);
  const mockAdminGetLogRetentionConfig = vi.mocked(adminGetLogRetentionConfig);

  beforeEach(() => {
    vi.resetAllMocks();
    mockAdminGetLogRetentionConfig.mockResolvedValue({
      enabled: false,
      keepDays: 90,
      mode: 'ARCHIVE_TABLE',
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('can advance to the next access-log page even when backend totalPages metadata is wrong', async () => {
    const secondPage = buildAccessPage({
      content: [
        {
          id: 16,
          createdAt: '2026-03-09T11:41:00Z',
          username: 'admin',
          method: 'GET',
          path: '/api/admin/access-logs/16',
          clientIp: '127.0.0.1',
          serverIp: '127.0.0.1',
          statusCode: 200,
          requestId: 'req-16',
        },
      ],
      totalElements: 0,
      totalPages: 0,
      size: 15,
      number: 1,
      last: true,
    });

    mockAdminListAccessLogs
      .mockResolvedValueOnce(
        buildAccessPage({
          content: Array.from({ length: 15 }, (_, index) => ({
            id: index + 1,
            createdAt: `2026-03-09T11:40:${String(index).padStart(2, '0')}Z`,
            username: 'admin',
            method: 'GET',
            path: `/api/admin/access-logs/${index + 1}`,
            clientIp: '127.0.0.1',
            serverIp: '127.0.0.1',
            statusCode: 200,
            requestId: `req-${index + 1}`,
          })),
          totalElements: 0,
          totalPages: 0,
          size: 15,
          number: 0,
          last: false,
        })
      )
      .mockResolvedValueOnce(secondPage)
      .mockResolvedValue(secondPage);

    render(
      <MemoryRouter initialEntries={['/admin/metrics?active=global-logs&glTab=access&page=1&pageSize=15']}>
        <GlobalLogsForm />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockAdminListAccessLogs).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 1,
          pageSize: 15,
        })
      );
    });

    const nextButton = screen.getByRole('button', { name: '下一页' });
    expect(nextButton.hasAttribute('disabled')).toBe(false);

    fireEvent.click(nextButton);

    await waitFor(() => {
      expect(mockAdminListAccessLogs.mock.calls.some(([query]) => query?.page === 2 && query?.pageSize === 15)).toBe(true);
    });

    await waitFor(() => {
      expect(screen.getByText('第 2 页 / 共 2 页（16 条）')).not.toBeNull();
    });
  });
});