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
      models: [{ purpose: 'MULTIMODAL_CHAT', modelName: 'text-only-model', enabled: true }],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it('warns and still adds unsupported-looking models into multimodal chat when user confirms', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<AiProvidersForm />);

    await waitFor(() => expect(mockAdminListProviderModels).toHaveBeenCalledWith('p1'));

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getAllByRole('button', { name: '添加' })[0]);

    await waitFor(() => expect(mockAdminFetchUpstreamModels).toHaveBeenCalledWith('p1'));

    fireEvent.change(screen.getByPlaceholderText('例如 gpt-4o-mini'), { target: { value: 'text-only-model' } });
    expect(screen.getByText('该模型名未识别出显式视觉能力；仍可加入多模态模型列表，但建议优先选择原生多模态模型。')).not.toBeNull();

    const addButtons = screen.getAllByRole('button', { name: '添加' });
    fireEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mockAdminAddProviderModel).toHaveBeenCalledWith('p1', 'MULTIMODAL_CHAT', 'text-only-model'));
    expect(screen.queryByText(/已按能力归类为/)).toBeNull();
  });

  it('shows fetched upstream models in multimodal modal without applying an invalid default capability filter', async () => {
    mockAdminFetchUpstreamModels.mockResolvedValueOnce({
      providerId: 'p1',
      models: ['qwen3.5-35b-a3b'],
    });

    render(<AiProvidersForm />);

    await waitFor(() => expect(mockAdminListProviderModels).toHaveBeenCalledWith('p1'));

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getAllByRole('button', { name: '添加' })[0]);

    await waitFor(() => expect(mockAdminFetchUpstreamModels).toHaveBeenCalledWith('p1'));
    await waitFor(() => expect(screen.getByRole('button', { name: 'qwen3.5-35b-a3b' })).not.toBeNull());
  });

  it('does not add unsupported-looking multimodal chat models when user cancels confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<AiProvidersForm />);

    await waitFor(() => expect(mockAdminListProviderModels).toHaveBeenCalledWith('p1'));

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getAllByRole('button', { name: '添加' })[0]);

    await waitFor(() => expect(mockAdminFetchUpstreamModels).toHaveBeenCalledWith('p1'));

    fireEvent.change(screen.getByPlaceholderText('例如 gpt-4o-mini'), { target: { value: 'text-only-model' } });

    const addButtons = screen.getAllByRole('button', { name: '添加' });
    fireEvent.click(addButtons[addButtons.length - 1]);

    await waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(1));
    expect(mockAdminAddProviderModel).not.toHaveBeenCalled();
  });
});