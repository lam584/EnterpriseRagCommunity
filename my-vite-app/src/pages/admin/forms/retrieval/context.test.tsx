import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ContextClipForm from './context';

const {
  useAccessMock,
  adminGetContextClipConfigMock,
  adminUpdateContextClipConfigMock,
  adminTestContextClipMock,
  adminListContextWindowsMock,
  adminGetContextWindowMock,
} = vi.hoisted(() => ({
  useAccessMock: vi.fn(),
  adminGetContextClipConfigMock: vi.fn(),
  adminUpdateContextClipConfigMock: vi.fn(),
  adminTestContextClipMock: vi.fn(),
  adminListContextWindowsMock: vi.fn(),
  adminGetContextWindowMock: vi.fn(),
}));

vi.mock('../../../../contexts/AccessContext', () => ({
  useAccess: useAccessMock,
}));

vi.mock('../../../../services/retrievalContextService', () => ({
  adminGetContextClipConfig: adminGetContextClipConfigMock,
  adminUpdateContextClipConfig: adminUpdateContextClipConfigMock,
  adminTestContextClip: adminTestContextClipMock,
  adminListContextWindows: adminListContextWindowsMock,
  adminGetContextWindow: adminGetContextWindowMock,
}));

describe('ContextClipForm', () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.resetAllMocks();
    useAccessMock.mockReturnValue({
      loading: false,
      hasPerm: () => true,
    });
    adminGetContextClipConfigMock.mockResolvedValue({
      enabled: true,
      policy: 'IMPORTANCE',
      contextTokenBudget: 3333,
      maxContextTokens: 6666,
      reserveAnswerTokens: 1111,
      alpha: 1.2,
      beta: 2.3,
      gamma: 3.4,
      ablationMode: 'NONE',
      crossSourceDedup: false,
      dedupByPostId: true,
      dedupByTitle: true,
      dedupByContentHash: false,
    });
    adminUpdateContextClipConfigMock.mockResolvedValue({
      enabled: true,
      policy: 'IMPORTANCE',
      contextTokenBudget: 3456,
      maxContextTokens: 6666,
      reserveAnswerTokens: 1111,
      alpha: 1.2,
      beta: 2.3,
      gamma: 3.4,
      ablationMode: 'REL_ONLY',
      crossSourceDedup: true,
    });
    adminTestContextClipMock.mockResolvedValue({
      budgetTokens: 1000,
      usedTokens: 100,
      itemsSelected: 1,
      itemsDropped: 0,
      contextPrompt: 'prompt',
      config: {
        policy: 'IMPORTANCE',
        contextTokenBudget: 3456,
        maxContextTokens: 6666,
        reserveAnswerTokens: 1111,
        alpha: 1.2,
        beta: 2.3,
        gamma: 3.4,
        ablationMode: 'REL_ONLY',
        crossSourceDedup: true,
      },
      selected: [],
      dropped: [],
    });
    adminListContextWindowsMock.mockResolvedValue({
      content: [],
      totalElements: 0,
    });
    adminGetContextWindowMock.mockResolvedValue({ id: 1 });
  });

  it('渲染并展示新增字段的加载值', async () => {
    render(<ContextClipForm />);
    await waitFor(() => expect(adminGetContextClipConfigMock).toHaveBeenCalledTimes(1));

    const modeSelect = screen
      .getAllByRole('combobox')
      .find((el) => (el as HTMLSelectElement).value === 'NONE') as HTMLSelectElement | undefined;
    expect(modeSelect).not.toBeNull();
    expect(screen.getByDisplayValue('3333')).not.toBeNull();
    expect(screen.getByDisplayValue('6666')).not.toBeNull();
    expect(screen.getByDisplayValue('1111')).not.toBeNull();
    expect(screen.getByText('去重：postId')).not.toBeNull();
    const crossSourceCheckbox = screen.getByLabelText('跨来源内容去重（crossSourceDedup）') as HTMLInputElement;
    expect(crossSourceCheckbox.checked).toBe(false);
  });

  it('保存时提交新增字段映射', async () => {
    render(<ContextClipForm />);
    await waitFor(() => expect(adminGetContextClipConfigMock).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getAllByRole('button', { name: '编辑' })[0] as HTMLButtonElement);

    const budgetInput = screen.getByText('上下文预算（contextTokenBudget）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(budgetInput, { target: { value: '3456' } });

    const modeSelect = screen
      .getAllByRole('combobox')
      .find((el) => (el as HTMLSelectElement).value === 'NONE') as HTMLSelectElement;
    fireEvent.change(modeSelect, { target: { value: 'REL_ONLY' } });

    const crossSourceCheckbox = screen.getByLabelText('跨来源内容去重（crossSourceDedup）') as HTMLInputElement;
    fireEvent.click(crossSourceCheckbox);

    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(adminUpdateContextClipConfigMock).toHaveBeenCalledTimes(1));
    expect(adminUpdateContextClipConfigMock).toHaveBeenCalledWith(
      expect.objectContaining({
        contextTokenBudget: 3456,
        ablationMode: 'REL_ONLY',
        crossSourceDedup: true,
      }),
    );
  });

  it('保存时非法输入归一化且保留边界值', async () => {
    render(<ContextClipForm />);
    await waitFor(() => expect(adminGetContextClipConfigMock).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getAllByRole('button', { name: '编辑' })[0] as HTMLButtonElement);

    const budgetInput = screen.getByText('上下文预算（contextTokenBudget）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(budgetInput, { target: { value: 'abc' } });
    const maxContextTokensInput = screen.getByText('最大全局预算（maxContextTokens）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(maxContextTokensInput, { target: { value: '1e309' } });
    const reserveInput = screen.getByText('预留回答 tokens').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(reserveInput, { target: { value: '0' } });
    const maxItemsInput = screen.getByText('最大条数').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(maxItemsInput, { target: { value: '0' } });
    const alphaInput = screen.getByText('α（相关性权重）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(alphaInput, { target: { value: '0' } });
    const gammaInput = screen.getByText('γ（冗余惩罚权重）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(gammaInput, { target: { value: '-1' } });

    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(adminUpdateContextClipConfigMock).toHaveBeenCalledTimes(1));
    expect(adminUpdateContextClipConfigMock).toHaveBeenCalledWith(
      expect.objectContaining({
        contextTokenBudget: null,
        maxContextTokens: null,
        reserveAnswerTokens: 0,
        maxItems: 0,
        alpha: 0,
        gamma: -1,
      }),
    );
  });

  it('测试预览提交映射并回显新增字段', async () => {
    render(<ContextClipForm />);
    await waitFor(() => expect(adminGetContextClipConfigMock).toHaveBeenCalledTimes(1));

    const queryInput = screen.getAllByText('Query')[0]?.parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(queryInput, { target: { value: '如何调参' } });
    const boardInput = screen.getByText('boardId（可选）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(boardInput, { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: '运行测试' }));

    await waitFor(() => expect(adminTestContextClipMock).toHaveBeenCalledTimes(1));
    expect(adminTestContextClipMock).toHaveBeenCalledWith(
      expect.objectContaining({
        queryText: '如何调参',
        boardId: 12,
        useSavedConfig: false,
        config: expect.objectContaining({
          contextTokenBudget: 3333,
          ablationMode: 'NONE',
          crossSourceDedup: false,
        }),
      }),
    );

    expect(await screen.findByText('ablationMode=REL_ONLY')).not.toBeNull();
    expect(screen.getByText('crossSourceDedup=true')).not.toBeNull();
  });

  it('测试预览处理边界与非法 boardId 且验证 payload', async () => {
    render(<ContextClipForm />);
    await waitFor(() => expect(adminGetContextClipConfigMock).toHaveBeenCalledTimes(1));

    const queryInput = screen.getAllByText('Query')[0]?.parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(queryInput, { target: { value: 'payload-check' } });
    const boardInput = screen.getByText('boardId（可选）').parentElement?.querySelector('input') as HTMLInputElement;
    fireEvent.change(boardInput, { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: '运行测试' }));

    await waitFor(() => expect(adminTestContextClipMock).toHaveBeenCalledTimes(1));
    expect(adminTestContextClipMock).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        boardId: 0,
        useSavedConfig: false,
        config: expect.objectContaining({ contextTokenBudget: 3333 }),
      }),
    );

    const useSavedCheckbox = screen.getByLabelText('使用已保存配置（忽略当前编辑）') as HTMLInputElement;
    fireEvent.click(useSavedCheckbox);
    fireEvent.change(boardInput, { target: { value: 'abc' } });
    fireEvent.click(screen.getByRole('button', { name: '运行测试' }));

    await waitFor(() => expect(adminTestContextClipMock).toHaveBeenCalledTimes(2));
    expect(adminTestContextClipMock).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        boardId: null,
        useSavedConfig: true,
        config: null,
      }),
    );
  });
});
