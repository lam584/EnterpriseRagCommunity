import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../services/aiProvidersAdminService', () => ({
  adminGetAiProvidersConfig: vi.fn(),
}));

vi.mock('../../../../services/aiChatOptionsService', () => ({
  getAiChatOptions: vi.fn(),
}));

vi.mock('../../../../services/tokenMetricsAdminService', () => ({
  adminGetTokenMetrics: vi.fn(),
}));

vi.mock('../../../../services/llmLoadtestAdminService', () => ({
  adminGetLlmLoadTestStatus: vi.fn(),
  adminGetLlmLoadTestExportUrl: vi.fn(),
  adminListLlmLoadTestHistory: vi.fn(),
  adminStartLlmLoadTest: vi.fn(),
  adminStopLlmLoadTest: vi.fn(),
  adminUpsertLlmLoadTestHistory: vi.fn(),
}));

vi.mock('../../../../components/admin/ProviderModelSelect', () => ({
  ProviderModelSelect: () => <div data-testid="provider-model-select" />,
}));

import { adminGetAiProvidersConfig } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions } from '../../../../services/aiChatOptionsService';
import { adminListLlmLoadTestHistory } from '../../../../services/llmLoadtestAdminService';
import LlmLoadTestPanel from './llm-loadtest';

describe('LlmLoadTestPanel', () => {
  const mockAdminGetAiProvidersConfig = vi.mocked(adminGetAiProvidersConfig);
  const mockGetAiChatOptions = vi.mocked(getAiChatOptions);
  const mockAdminListLlmLoadTestHistory = vi.mocked(adminListLlmLoadTestHistory);

  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    mockAdminGetAiProvidersConfig.mockResolvedValue(
      { providers: [], activeProviderId: '' } as unknown as Awaited<ReturnType<typeof adminGetAiProvidersConfig>>,
    );
    mockGetAiChatOptions.mockResolvedValue(
      { providers: [] } as unknown as Awaited<ReturnType<typeof getAiChatOptions>>,
    );
    mockAdminListLlmLoadTestHistory.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  it('renders core sections without real network', async () => {
    render(<LlmLoadTestPanel />);
    expect(screen.getByText('统计与导出')).not.toBeNull();
    expect(screen.getByText('运行结束后展示关键指标与导出')).not.toBeNull();
    expect(screen.getByTestId('provider-model-select')).not.toBeNull();

    await waitFor(() => {
      expect(mockAdminGetAiProvidersConfig).toHaveBeenCalledTimes(1);
      expect(mockGetAiChatOptions).toHaveBeenCalledTimes(1);
      expect(mockAdminListLlmLoadTestHistory).toHaveBeenCalledTimes(1);
    });
  });

  it('shows empty history when expanded and no stored runs', async () => {
    render(<LlmLoadTestPanel />);
    await waitFor(() => expect(mockAdminListLlmLoadTestHistory).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByText('回归对比（数据库保存）'));
    expect(await screen.findByText('暂无历史记录')).not.toBeNull();
  });
});
