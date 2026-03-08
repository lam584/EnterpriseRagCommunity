import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';

const navigate = vi.fn();

const serviceMocks = vi.hoisted(() => {
  return {
    adminListPipelineHistory: vi.fn(),
    adminGetPipelineByRunId: vi.fn(),
  };
});

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = (await importOriginal()) as typeof import('react-router-dom');
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

vi.mock('../../services/moderationPipelineService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/moderationPipelineService')>();
  return {
    ...actual,
    adminListPipelineHistory: serviceMocks.adminListPipelineHistory,
    adminGetPipelineByRunId: serviceMocks.adminGetPipelineByRunId,
  };
});

import { ModerationPipelineHistoryPanel } from './ModerationPipelineHistoryPanel';
import { renderWithRoute } from '../../testUtils/renderWithRoute';
import { adminGetPipelineByRunId, adminListPipelineHistory } from '../../services/moderationPipelineService';

const mockAdminListPipelineHistory = vi.mocked(adminListPipelineHistory);
const mockAdminGetPipelineByRunId = vi.mocked(adminGetPipelineByRunId);

describe('ModerationPipelineHistoryPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('autoLatest=true 且无 initialMode 时自动加载最新列表', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [],
      totalPages: 1,
      totalElements: 0,
    });

    renderWithRoute(<ModerationPipelineHistoryPanel autoLoadDetails={false} />);

    await waitFor(() => {
      expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(1);
    });
    expect(mockAdminListPipelineHistory).toHaveBeenCalledWith({ page: 1, pageSize: 20 });
  });

  it('autoLatest=false 且无 initialMode 时不自动加载', async () => {
    renderWithRoute(<ModerationPipelineHistoryPanel autoLatest={false} autoLoadDetails={false} />);

    await Promise.resolve();
    await Promise.resolve();

    expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(0);
    expect(screen.getByText('暂无记录。')).toBeTruthy();
  });

  it('分页按钮会触发重新加载并更新上一页/下一页可用状态', async () => {
    mockAdminListPipelineHistory
      .mockResolvedValueOnce({
        content: [{ id: 101, queueId: 9, contentType: 'POST', contentId: 1, createdAt: '2026-01-02T00:00:00Z' }],
        totalPages: 2,
        totalElements: 21,
      })
      .mockResolvedValueOnce({
        content: [{ id: 102, queueId: 9, contentType: 'POST', contentId: 2, createdAt: '2026-01-01T00:00:00Z' }],
        totalPages: 2,
        totalElements: 21,
      });

    renderWithRoute(
      <ModerationPipelineHistoryPanel
        autoLoadDetails={false}
        initialMode={{ kind: 'queue', queueId: 9 }}
      />,
    );

    await waitFor(() => {
      expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByRole('button', { name: '上一页' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: '下一页' }).hasAttribute('disabled')).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: '下一页' }));

    await waitFor(() => {
      expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(2);
    });

    expect(mockAdminListPipelineHistory.mock.calls[1]?.[0]).toMatchObject({ queueId: 9, page: 2, pageSize: 20 });
    expect(screen.getByRole('button', { name: '上一页' }).hasAttribute('disabled')).toBe(false);
  });

  it('content 模式会按 contentType/contentId 查询，并支持切换每页条数', async () => {
    mockAdminListPipelineHistory
      .mockResolvedValueOnce({
        content: [{ id: 601, contentType: 'COMMENT', contentId: 42, createdAt: '2026-01-02T00:00:00Z' }],
        totalPages: 1,
        totalElements: 1,
      })
      .mockResolvedValueOnce({
        content: [{ id: 602, contentType: 'COMMENT', contentId: 42, createdAt: '2026-01-03T00:00:00Z' }],
        totalPages: 1,
        totalElements: 1,
      });

    renderWithRoute(
      <ModerationPipelineHistoryPanel
        autoLoadDetails={false}
        initialMode={{ kind: 'content', contentType: 'COMMENT', contentId: 42 }}
      />,
    );

    await waitFor(() => {
      expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(1);
    });
    expect(mockAdminListPipelineHistory.mock.calls[0]?.[0]).toMatchObject({ contentType: 'COMMENT', contentId: 42, page: 1, pageSize: 20 });

    fireEvent.change(screen.getByDisplayValue('20'), { target: { value: '10' } });

    await waitFor(() => {
      expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(2);
    });
    expect(mockAdminListPipelineHistory.mock.calls[1]?.[0]).toMatchObject({ contentType: 'COMMENT', contentId: 42, page: 1, pageSize: 10 });
  });

  it('展开 run 时拉取 detail 并渲染 steps，且会缓存去重（展开两次只拉一次）', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [
        {
          id: 201,
          queueId: 3,
          contentType: 'COMMENT',
          contentId: 9,
          finalDecision: 'ALLOW',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      totalPages: 1,
      totalElements: 1,
    });

    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: {
        id: 201,
        queueId: 3,
        contentType: 'COMMENT',
        contentId: 9,
      },
      steps: [
        {
          id: 11,
          runId: 201,
          stage: 'LLM',
          stepOrder: 1,
          decision: 'PASS',
          score: 0.7,
          threshold: 0.5,
          costMs: 120,
          details: { foo: 'bar' },
          startedAt: '2026-01-01T00:00:00Z',
          endedAt: '2026-01-01T00:00:01Z',
        },
      ],
    });

    renderWithRoute(
      <ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 3 }} />,
    );

    expect(await screen.findByText('运行ID=201')).not.toBeNull();

    const runCard = screen.getByText('运行ID=201').closest('[role="button"]') as HTMLElement | null;
    expect(runCard).not.toBeNull();

    fireEvent.click(within(runCard as HTMLElement).getByRole('button', { name: '展开' }));

    await waitFor(() => {
      expect(mockAdminGetPipelineByRunId).toHaveBeenCalledTimes(1);
    });
    expect(mockAdminGetPipelineByRunId).toHaveBeenCalledWith(201);

    expect(await screen.findByText('LLM')).not.toBeNull();
    expect(await screen.findByText(/"foo":\s*"bar"/)).not.toBeNull();

    fireEvent.click(within(runCard as HTMLElement).getByRole('button', { name: '收起' }));
    fireEvent.click(within(runCard as HTMLElement).getByRole('button', { name: '展开' }));

    await waitFor(() => {
      expect(mockAdminGetPipelineByRunId).toHaveBeenCalledTimes(1);
    });
  });

  it('stageFilter 会过滤 steps 的展示', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ id: 301, queueId: 7, contentType: 'POST', contentId: 8, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    });

    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: { id: 301, queueId: 7, contentType: 'POST', contentId: 8 },
      steps: [
        { id: 21, runId: 301, stage: 'RULE', stepOrder: 1, decision: 'PASS', costMs: 10, details: { rule: true } },
        { id: 22, runId: 301, stage: 'LLM', stepOrder: 2, decision: 'PASS', costMs: 20, details: { model: 'x' } },
      ],
    });

    renderWithRoute(
      <ModerationPipelineHistoryPanel
        autoLoadDetails={false}
        initialMode={{ kind: 'queue', queueId: 7 }}
        stageFilter={['RULE']}
      />,
    );

    expect(await screen.findByText('运行ID=301')).not.toBeNull();
    const runCard = screen.getByText('运行ID=301').closest('[role="button"]') as HTMLElement | null;
    expect(runCard).not.toBeNull();

    fireEvent.click(within(runCard as HTMLElement).getByRole('button', { name: '展开' }));

    expect(await screen.findByText('RULE')).not.toBeNull();
    expect(screen.queryByText('LLM')).toBeNull();
  });

  it('在当前 stageFilter 下无 steps 时显示空提示', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ id: 701, queueId: 7, contentType: 'POST', contentId: 8, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    });

    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: { id: 701, queueId: 7, contentType: 'POST', contentId: 8 },
      steps: [{ id: 31, runId: 701, stage: 'LLM', stepOrder: 1, decision: 'PASS', costMs: 20, details: { model: 'x' } }],
    });

    renderWithRoute(
      <ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 7 }} stageFilter={['RULE']} />,
    );

    expect(await screen.findByText('运行ID=701')).not.toBeNull();
    const runCard = screen.getByText('运行ID=701').closest('[role="button"]') as HTMLElement | null;
    expect(runCard).not.toBeNull();
    fireEvent.click(within(runCard as HTMLElement).getByRole('button', { name: '展开' }));

    expect(await screen.findByText('该 run 在当前筛选 stage 下暂无 steps 记录。')).toBeTruthy();
  });

  it('支持键盘 Enter 展开/收起 run，并在 header 分页位展示 controls', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ id: 801, queueId: 1, contentType: 'POST', contentId: 1, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    });
    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: { id: 801 },
      steps: [{ id: 41, runId: 801, stage: 'RULE', stepOrder: 1, decision: 'PASS', details: { ok: true } }],
    });

    renderWithRoute(
      <ModerationPipelineHistoryPanel
        autoLoadDetails={false}
        initialMode={{ kind: 'queue', queueId: 1 }}
        paginationPlacement="header"
        showPaginationTitle={false}
      />,
    );

    const runCard = (await screen.findByText('运行ID=801')).closest('[role="button"]') as HTMLElement;
    fireEvent.keyDown(runCard, { key: 'Enter' });
    expect(await screen.findByText('RULE')).toBeTruthy();

    fireEvent.keyDown(runCard, { key: ' ' });
    await waitFor(() => {
      expect(screen.queryByText('RULE')).toBeNull();
    });

    expect(screen.getByRole('button', { name: '刷新' })).toBeTruthy();
  });

  it('展示错误提示：列表加载失败', async () => {
    mockAdminListPipelineHistory.mockRejectedValueOnce(new Error('boom'));

    renderWithRoute(<ModerationPipelineHistoryPanel autoLoadDetails={false} />);

    expect(await screen.findByText('boom')).not.toBeNull();
  });

  it('展示错误提示：详情加载失败', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ id: 401, queueId: 1, contentType: 'POST', contentId: 1, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    });
    mockAdminGetPipelineByRunId.mockRejectedValueOnce(new Error('detail fail'));

    renderWithRoute(
      <ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />,
    );

    expect(await screen.findByText('运行ID=401')).not.toBeNull();
    const runCard = screen.getByText('运行ID=401').closest('[role="button"]') as HTMLElement | null;
    expect(runCard).not.toBeNull();

    fireEvent.click(within(runCard as HTMLElement).getByRole('button', { name: '展开' }));

    expect(await screen.findByText('detail fail')).not.toBeNull();
  });

  it('点击 traceId 按钮会使用 URL 编码后的 traceId 进行导航', async () => {
    const traceId = 'a b&c/d?=x';

    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [
        {
          id: 501,
          queueId: 2,
          contentType: 'POST',
          contentId: 10,
          createdAt: '2026-01-01T00:00:00Z',
          traceId,
        },
      ],
      totalPages: 1,
      totalElements: 1,
    });

    renderWithRoute(
      <ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 2 }} />,
    );

    expect(await screen.findByText('运行ID=501')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '打开 trace 日志' }));

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(`/admin/review?active=logs&traceId=${encodeURIComponent(traceId)}`);
  });

  it('compact + nowrap 时覆盖分支：badge 分类、时间/耗时格式、JSON stringify 失败回退、header/pagination 分支', async () => {
    const circular: any = {};
    circular.self = circular;

    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [
        {
          id: 901,
          queueId: 1,
          contentType: 'POST',
          contentId: 1,
          finalDecision: 'HUMAN_REVIEW',
          totalMs: 10,
          createdAt: '2026-01-02T00:00:00Z',
          traceId: 't-901',
          status: 'DONE',
        },
        {
          id: 902,
          queueId: 1,
          contentType: 'POST',
          contentId: 2,
          finalDecision: 'DENY',
          totalMs: 2000,
          createdAt: '2026-01-01T00:00:00Z',
          errorMessage: 'err',
          traceId: null,
          status: null,
          llmThreshold: 0,
        },
      ],
      totalPages: 1,
      totalElements: 2,
    });

    mockAdminGetPipelineByRunId
      .mockResolvedValueOnce({
        run: 'bad-shape' as any,
        steps: [
          {
            id: 41,
            runId: 901,
            stage: 'LLM',
            stepOrder: 1,
            decision: 'ERROR',
            costMs: 1500,
            details: circular,
            startedAt: 'bad-date',
            endedAt: null,
          },
        ],
      } as any)
      .mockResolvedValueOnce({
        run: { llmThreshold: '0.55', llmModel: 'fromRun' } as any,
        steps: [
          {
            id: 42,
            runId: 902,
            stage: 'LLM',
            stepOrder: 1,
            decision: 'PASS',
            costMs: 10,
            details: { model: 'm1' },
            startedAt: '2026-01-01T00:00:00Z',
            endedAt: '2026-01-01T00:00:01Z',
          },
        ],
      } as any);

    renderWithRoute(
      <ModerationPipelineHistoryPanel
        autoLoadDetails={true}
        initialMode={{ kind: 'queue', queueId: 1 }}
        density="compact"
        stepDetailsFormat="nowrap"
        showPageSummary={false}
        showPaginationTitle={false}
        showRefreshButton={false}
        paginationPlacement="panel"
      />,
    );

    const run901 = (await screen.findByText('运行ID=901')).closest('[role="button"]') as HTMLElement;
    const run902 = (await screen.findByText('运行ID=902')).closest('[role="button"]') as HTMLElement;
    expect(run901).toBeTruthy();
    expect(run902).toBeTruthy();

    fireEvent.keyDown(run901, { key: 'Escape' });

    fireEvent.click(within(run901).getByRole('button', { name: '展开' }));

    await waitFor(() => {
      expect(mockAdminGetPipelineByRunId).toHaveBeenCalled();
    });

    expect(await screen.findByText('LLM')).toBeTruthy();
    expect(await screen.findByText('1.50s')).toBeTruthy();
    expect(await screen.findByText((content) => content.includes('bad-date'))).toBeTruthy();
    expect(await screen.findByText('[object Object]')).toBeTruthy();
    expect(screen.queryByText('分页')).toBeNull();
    expect(screen.queryByText('刷新')).toBeNull();
    expect(screen.queryByText('页码')).toBeNull();

    fireEvent.click(within(run902).getByRole('button', { name: '展开' }));
    expect(await screen.findByText(/阈值：0/)).toBeTruthy();
  });

  it('列表/详情异常为非 Error 时回退到 String(e)', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ id: 1001, queueId: 1, contentType: 'POST', contentId: 1, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    });
    mockAdminGetPipelineByRunId.mockRejectedValueOnce('detail fail');

    renderWithRoute(
      <ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />,
    );

    const run = (await screen.findByText('运行ID=1001')).closest('[role="button"]') as HTMLElement;
    fireEvent.click(within(run).getByRole('button', { name: '展开' }));

    expect(await screen.findByText('detail fail')).toBeTruthy();
  });


  it('从 LLM step.details 的候选路径解析模型/阈值，并在 stepOrder 相同时时间排序取最后一个 LLM step', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [
        {
          id: 1101,
          queueId: 1,
          contentType: 'POST',
          contentId: 1,
          finalDecision: 'PASS',
          createdAt: '2026-01-01T00:00:00Z',
          llmModel: null,
          llmThreshold: null,
        },
      ],
      totalPages: 1,
      totalElements: 1,
    });

    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: { id: 1101 },
      steps: [
        {
          id: 51,
          runId: 1101,
          stage: 'LLM',
          stepOrder: 1,
          decision: 'PASS',
          costMs: 10,
          details: { llm: { model: 'm1', threshold: 0.11 } },
          endedAt: '2026-01-01T00:00:01Z',
        },
        {
          id: 52,
          runId: 1101,
          stage: 'LLM',
          stepOrder: 1,
          decision: 'PASS',
          costMs: 12,
          details: { llm_model: 'm2', llm_threshold: 0.22 },
          endedAt: '2026-01-01T00:00:02Z',
        },
      ],
    } as any);

    renderWithRoute(<ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />);

    const run = (await screen.findByText('运行ID=1101')).closest('[role="button"]') as HTMLElement;
    fireEvent.click(within(run).getByRole('button', { name: '展开' }));

    await waitFor(() => {
      expect(screen.getByText('模型：m2')).toBeTruthy();
    });
    expect(screen.getByText('阈值：0.22')).toBeTruthy();
  });
  it('runId 字段可作为运行ID 渲染与加载详情', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ runId: 2001, queueId: 1, contentType: 'POST', contentId: 1, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    } as any);

    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: { runId: 2001 } as any,
      steps: [{ id: 501, runId: 2001, stage: 'RULE', decision: 'PASS', details: { ok: true } }],
    } as any);

    renderWithRoute(
      <ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />,
    );

    const run = (await screen.findByText('运行ID=2001')).closest('[role="button"]') as HTMLElement;
    fireEvent.click(within(run).getByRole('button', { name: '展开' }));

    await waitFor(() => {
      expect(mockAdminGetPipelineByRunId).toHaveBeenCalledWith(2001);
    });
    expect(await screen.findByText('RULE')).toBeTruthy();
  });

  it('渲染带标题的 header，并在 compact density 下展示 header 区域', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [],
      totalPages: 1,
      totalElements: 0,
    });

    renderWithRoute(
      <ModerationPipelineHistoryPanel
        title="历史记录"
        density="compact"
        autoLoadDetails={false}
        initialMode={{ kind: 'queue', queueId: 1 }}
      />,
    );

    expect(await screen.findByText('历史记录')).toBeTruthy();
    expect(screen.getByRole('button', { name: '刷新' })).toBeTruthy();
  });

  it('列表加载异常为非 Error 时回退到 String(e)', async () => {
    mockAdminListPipelineHistory.mockRejectedValueOnce('list-bad');

    renderWithRoute(<ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />);

    expect(await screen.findByText('list-bad')).toBeTruthy();
  });

  it('从 detail.run 解析模型/阈值，并覆盖 steps 字段缺省回退分支', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [
        {
          id: 1201,
          queueId: 1,
          contentType: 'POST',
          contentId: 1,
          createdAt: '2026-01-01T00:00:00Z',
          llmModel: null,
          llmThreshold: null,
          traceId: null,
          status: null,
        },
      ],
      totalPages: 1,
      totalElements: 1,
    });

    mockAdminGetPipelineByRunId.mockResolvedValueOnce({
      run: { id: 1201, llmModel: ' fromDetail ', llmThreshold: '0.33' } as any,
      steps: [
        { id: 201, runId: 1201, stage: null, decision: null, stepOrder: null, startedAt: null, endedAt: null, details: { ok: true } },
        { id: 202, runId: 1201, stage: 'LLM', decision: 'PASS', stepOrder: 1, startedAt: '2026-01-01T00:00:01Z', endedAt: null, details: { model: 'm1' } },
        { id: 203, runId: 1201, stage: 'LLM', decision: 'PASS', stepOrder: 1, startedAt: '2026-01-01T00:00:02Z', endedAt: null, details: { model: 'm2' } },
        { id: 204, runId: 1201, stage: 'RULE', decision: '', stepOrder: 2, startedAt: null, endedAt: '2026-01-01T00:00:03Z', details: null },
      ],
    } as any);

    renderWithRoute(<ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />);

    const run = (await screen.findByText('运行ID=1201')).closest('[role="button"]') as HTMLElement;
    expect(run).toBeTruthy();

    fireEvent.click(within(run).getByRole('button', { name: '展开' }));

    await waitFor(() => {
      expect(screen.getByText('模型：fromDetail')).toBeTruthy();
    });
    expect(screen.getByText('阈值：0.33')).toBeTruthy();

    expect((await screen.findAllByText('—')).length).toBeGreaterThan(0);
  });

  it('run 缺少合法运行ID 时不渲染该卡片', async () => {
    mockAdminListPipelineHistory.mockResolvedValueOnce({
      content: [{ id: 'bad' as any, createdAt: '2026-01-01T00:00:00Z' }],
      totalPages: 1,
      totalElements: 1,
    });

    renderWithRoute(<ModerationPipelineHistoryPanel autoLoadDetails={false} initialMode={{ kind: 'queue', queueId: 1 }} />);

    await waitFor(() => {
      expect(mockAdminListPipelineHistory).toHaveBeenCalledTimes(1);
    });
    expect(screen.queryByText(/运行ID=/)).toBeNull();
  });
});
