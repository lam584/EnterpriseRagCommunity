import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../services/aiProvidersAdminService', () => ({
  adminAddProviderModel: vi.fn(),
  adminDeleteProviderModel: vi.fn(),
  adminFetchUpstreamModels: vi.fn(),
  adminGetAiProvidersConfig: vi.fn(),
  adminListProviderModels: vi.fn(),
  adminPreviewUpstreamModels: vi.fn(),
  adminUpdateAiProvidersConfig: vi.fn(),
}));

import {
  adminAddProviderModel,
  adminFetchUpstreamModels,
  adminGetAiProvidersConfig,
  adminListProviderModels,
  adminPreviewUpstreamModels,
  adminUpdateAiProvidersConfig,
} from '../../../../services/aiProvidersAdminService';
import AiProvidersForm from './providers';

describe('AiProvidersForm', () => {
  const mockAdminAddProviderModel = vi.mocked(adminAddProviderModel);
  const mockAdminFetchUpstreamModels = vi.mocked(adminFetchUpstreamModels);
  const mockAdminGetAiProvidersConfig = vi.mocked(adminGetAiProvidersConfig);
  const mockAdminListProviderModels = vi.mocked(adminListProviderModels);
  const mockAdminPreviewUpstreamModels = vi.mocked(adminPreviewUpstreamModels);
  const mockAdminUpdateAiProvidersConfig = vi.mocked(adminUpdateAiProvidersConfig);

  beforeEach(() => {
    vi.resetAllMocks();
    mockAdminGetAiProvidersConfig.mockResolvedValue({
      activeProviderId: 'p1',
      providers: [
        {
          id: 'p1',
          name: 'Provider 1',
          type: 'OPENAI_COMPAT',
          baseUrl: 'http://example.test/v1',
          apiKey: '******',
          enabled: true,
        },
      ],
    });
    mockAdminListProviderModels.mockResolvedValue({ providerId: 'p1', models: [] });
    mockAdminFetchUpstreamModels.mockResolvedValue({ providerId: 'p1', models: [] });
    mockAdminPreviewUpstreamModels.mockResolvedValue({ providerId: 'p1', models: [] });
    mockAdminUpdateAiProvidersConfig.mockResolvedValue({
      activeProviderId: 'p1',
      providers: [
        {
          id: 'p1',
          name: 'Provider 1',
          type: 'OPENAI_COMPAT',
          baseUrl: 'http://example.test/v1',
          apiKey: '******',
          enabled: true,
        },
      ],
    });
    mockAdminAddProviderModel.mockResolvedValue({
      providerId: 'p1',
      models: [{ purpose: 'IMAGE_CHAT', modelName: 'text-only-model', enabled: true }],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it('warns and still adds unsupported-looking models into image chat when user confirms', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<AiProvidersForm />);

    await waitFor(() => expect(mockAdminListProviderModels).toHaveBeenCalledWith('p1'));

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getAllByRole('button', { name: '添加' })[1]);

    await waitFor(() => expect(mockAdminFetchUpstreamModels).toHaveBeenCalledWith('p1'));

    fireEvent.change(screen.getByPlaceholderText('例如 gpt-4o-mini'), { target: { value: 'text-only-model' } });
    expect(screen.getByText('该模型名未识别出视觉能力，可能不支持图片输入。继续添加时，仍会加入视觉模型列表。')).not.toBeNull();

    const addButtons = screen.getAllByRole('button', { name: '添加' });
    fireEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mockAdminAddProviderModel).toHaveBeenCalledWith('p1', 'IMAGE_CHAT', 'text-only-model'));
    expect(screen.queryByText(/已按能力归类为/)).toBeNull();
  });

  it('does not add unsupported-looking image chat models when user cancels confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<AiProvidersForm />);

    await waitFor(() => expect(mockAdminListProviderModels).toHaveBeenCalledWith('p1'));

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getAllByRole('button', { name: '添加' })[1]);

    await waitFor(() => expect(mockAdminFetchUpstreamModels).toHaveBeenCalledWith('p1'));

    fireEvent.change(screen.getByPlaceholderText('例如 gpt-4o-mini'), { target: { value: 'text-only-model' } });

    const addButtons = screen.getAllByRole('button', { name: '添加' });
    fireEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(1));
    expect(mockAdminAddProviderModel).not.toHaveBeenCalled();
  });
});