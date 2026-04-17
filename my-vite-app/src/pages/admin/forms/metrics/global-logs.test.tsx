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
  adminGetAccessLogEsIndexStatus: vi.fn(),
  adminListAccessLogs: vi.fn(),
}));

vi.mock('../../../../services/logRetentionService', () => ({
  adminGetLogRetentionConfig: vi.fn(),
  adminUpdateLogRetentionConfig: vi.fn(),
}));

vi.mock('../../../../components/common/DetailDialog', () => ({
  default: ({ open, children }: { open: boolean; children?: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

import { adminGetAccessLogDetail, adminGetAccessLogEsIndexStatus, adminListAccessLogs } from '../../../../services/accessLogService';
import { adminGetLogRetentionConfig, adminUpdateLogRetentionConfig } from '../../../../services/logRetentionService';
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
  const mockAdminGetAccessLogDetail = vi.mocked(adminGetAccessLogDetail);
  const mockAdminGetAccessLogEsIndexStatus = vi.mocked(adminGetAccessLogEsIndexStatus);
  const mockAdminGetLogRetentionConfig = vi.mocked(adminGetLogRetentionConfig);
  const mockAdminUpdateLogRetentionConfig = vi.mocked(adminUpdateLogRetentionConfig);

  beforeEach(() => {
    vi.resetAllMocks();
    mockAdminGetLogRetentionConfig.mockResolvedValue({
      enabled: false,
      keepDays: 90,
      mode: 'ARCHIVE_TABLE',
      maxPerRun: 5000,
      auditLogsEnabled: true,
      accessLogsEnabled: true,
      purgeArchivedEnabled: false,
      purgeArchivedKeepDays: 365,
    });
    mockAdminUpdateLogRetentionConfig.mockResolvedValue({
      enabled: false,
      keepDays: 90,
      mode: 'ARCHIVE_TABLE',
      maxPerRun: 5000,
      auditLogsEnabled: true,
      accessLogsEnabled: true,
      purgeArchivedEnabled: false,
      purgeArchivedKeepDays: 365,
    });
    mockAdminGetAccessLogEsIndexStatus.mockResolvedValue({
      indexName: 'access-logs-v1',
      collectionName: 'access-logs-v1',
      sinkMode: 'DUAL',
      esSinkEnabled: true,
      consumerEnabled: true,
      exists: true,
      available: true,
      health: 'green',
      status: 'open',
      docsCount: 123,
      storeSize: '1mb',
      availabilityMessage: null,
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

  it('updates extended retention options and submits complete payload', async () => {
    mockAdminListAccessLogs.mockResolvedValue(
      buildAccessPage({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 15,
        number: 0,
        last: true,
      })
    );

    mockAdminUpdateLogRetentionConfig.mockResolvedValue({
      enabled: false,
      keepDays: 90,
      mode: 'ARCHIVE_TABLE',
      maxPerRun: 8000,
      auditLogsEnabled: true,
      accessLogsEnabled: true,
      purgeArchivedEnabled: false,
      purgeArchivedKeepDays: 365,
    });

    render(
      <MemoryRouter initialEntries={['/admin/metrics?active=global-logs&glTab=access&page=1&pageSize=15']}>
        <GlobalLogsForm />
      </MemoryRouter>
    );

    const maxPerRunInput = await screen.findByDisplayValue('5000');
    fireEvent.change(maxPerRunInput, { target: { value: '8000' } });

    await waitFor(() => {
      expect(mockAdminUpdateLogRetentionConfig).toHaveBeenCalledWith({
        enabled: false,
        keepDays: 90,
        mode: 'ARCHIVE_TABLE',
        maxPerRun: 8000,
        auditLogsEnabled: true,
        accessLogsEnabled: true,
        purgeArchivedEnabled: false,
        purgeArchivedKeepDays: 365,
      });
    });
  });

  it('renders access-log ES index status panel', async () => {
    mockAdminListAccessLogs.mockResolvedValue(
      buildAccessPage({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 15,
        number: 0,
        last: true,
      })
    );

    render(
      <MemoryRouter initialEntries={['/admin/metrics?active=global-logs&glTab=access&page=1&pageSize=15']}>
        <GlobalLogsForm />
      </MemoryRouter>
    );

    await screen.findByText('日志索引状态（ES）');
    const indexCells = await screen.findAllByText('access-logs-v1');
    expect(indexCells.length).toBeGreaterThanOrEqual(2);
    await screen.findByText('123');
    await screen.findByText('存在');
    await screen.findByText('可用');
  });

  it('uses requestId to load access-log detail in KAFKA mode', async () => {
    mockAdminGetAccessLogEsIndexStatus.mockResolvedValue({
      indexName: 'access-logs-v1',
      collectionName: 'access-logs-v1',
      sinkMode: 'KAFKA',
      esSinkEnabled: true,
      consumerEnabled: true,
      exists: true,
      available: true,
      health: 'green',
      status: 'open',
      docsCount: 1,
      storeSize: '1mb',
      availabilityMessage: null,
    });
    mockAdminListAccessLogs.mockResolvedValue(
      buildAccessPage({
        content: [
          {
            id: 999,
            createdAt: '2026-03-09T11:41:00Z',
            username: 'admin',
            method: 'GET',
            path: '/api/admin/access-logs/999',
            clientIp: '127.0.0.1',
            serverIp: '127.0.0.1',
            statusCode: 200,
            requestId: 'req-kafka-1',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        size: 15,
        number: 0,
        last: true,
      })
    );
    mockAdminGetAccessLogDetail.mockResolvedValue({
      id: 999,
      createdAt: '2026-03-09T11:41:00Z',
      requestId: 'req-kafka-1',
      details: {},
    } as AccessLogDTO);

    render(
      <MemoryRouter initialEntries={['/admin/metrics?active=global-logs&glTab=access&page=1&pageSize=15']}>
        <GlobalLogsForm />
      </MemoryRouter>
    );

    const detailButton = await screen.findByRole('button', { name: '详情' });
    fireEvent.click(detailButton);

    await waitFor(() => {
      expect(mockAdminGetAccessLogDetail).toHaveBeenCalledWith('req-kafka-1');
    });
  });
});