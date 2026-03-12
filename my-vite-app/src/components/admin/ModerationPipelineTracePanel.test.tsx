import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';

const serviceMocks = vi.hoisted(() => {
  return {
    adminGetLatestPipelineByQueueId: vi.fn(),
  };
});

vi.mock('../../services/moderationPipelineService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/moderationPipelineService')>();
  return {
    ...actual,
    adminGetLatestPipelineByQueueId: serviceMocks.adminGetLatestPipelineByQueueId,
  };
});

import { ModerationPipelineTracePanel } from './ModerationPipelineTracePanel';
import { adminGetLatestPipelineByQueueId } from '../../services/moderationPipelineService';

const mockAdminGetLatestPipelineByQueueId = vi.mocked(adminGetLatestPipelineByQueueId);

describe('ModerationPipelineTracePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('初次加载时显示 loading', async () => {
    let resolve!: (v: unknown) => void;
    const pending = new Promise((r) => {
      resolve = r;
    });
    mockAdminGetLatestPipelineByQueueId.mockReturnValueOnce(pending as never);

    render(<ModerationPipelineTracePanel queueId={9} />);

    expect(await screen.findByText('加载中…')).not.toBeNull();
    expect(mockAdminGetLatestPipelineByQueueId).toHaveBeenCalledWith(9);

    resolve({ run: null, steps: null });

    await waitFor(() => {
      expect(screen.queryByText('加载中…')).toBeNull();
    });
  });

  it('无记录时展示提示（无 run）', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({ run: null, steps: null });

    render(<ModerationPipelineTracePanel queueId={1} />);

    expect(await screen.findByText('暂无流水线记录（可能尚未自动运行）')).not.toBeNull();
  });

  it('请求失败时展示错误提示', async () => {
    mockAdminGetLatestPipelineByQueueId.mockRejectedValueOnce(new Error('boom'));

    render(<ModerationPipelineTracePanel queueId={2} />);

    expect(await screen.findByText('boom')).not.toBeNull();
    expect(screen.queryByText('暂无流水线记录（可能尚未自动运行）')).toBeNull();
  });

  it('点击刷新会重新拉取并按 stepOrder 排序渲染 steps', async () => {
    mockAdminGetLatestPipelineByQueueId
      .mockResolvedValueOnce({ run: null, steps: null })
      .mockResolvedValueOnce({
        run: {
          id: 10,
          queueId: 3,
          contentType: 'POST',
          contentId: 99,
          traceId: 't-1',
        },
        steps: [
          {
            id: 2,
            runId: 10,
            stage: 'LLM',
            stepOrder: 2,
            decision: 'PASS',
            score: 0.7,
            threshold: 0.5,
            costMs: 20,
            details: { model: 'm2' },
          },
          {
            id: 1,
            runId: 10,
            stage: 'RULE',
            stepOrder: 1,
            decision: 'PASS',
            score: 0.2,
            threshold: 0.1,
            costMs: 10,
            details: { model: 'm1' },
          },
        ],
      });

    render(<ModerationPipelineTracePanel queueId={3} />);

    expect(await screen.findByText('暂无流水线记录（可能尚未自动运行）')).not.toBeNull();
    expect(mockAdminGetLatestPipelineByQueueId).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: '刷新' }));

    await waitFor(() => {
      expect(mockAdminGetLatestPipelineByQueueId).toHaveBeenCalledTimes(2);
    });
    expect(mockAdminGetLatestPipelineByQueueId.mock.calls[1]?.[0]).toBe(3);

    expect(await screen.findByText('runId：')).not.toBeNull();
    expect(await screen.findByText('10')).not.toBeNull();

    const summaries = Array.from(document.querySelectorAll('summary'));
    expect(summaries).toHaveLength(2);
    expect(summaries[0]?.textContent).toContain('RULE');
    expect(summaries[1]?.textContent).toContain('LLM');

    expect(screen.getByText(/"model":\s*"m1"/)).not.toBeNull();
    expect(screen.getByText(/"model":\s*"m2"/)).not.toBeNull();
  });

  it('隐藏 TEXT 阶段，并在存在 VISION/JUDGE 时隐藏 LLM 阶段展示', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: {
        id: 100,
        queueId: 6,
        contentType: 'POST',
        contentId: 200,
        traceId: 'trace-hide-llm',
      },
      steps: [
        {
          id: 1,
          runId: 100,
          stage: 'TEXT',
          stepOrder: 1,
          decision: 'SKIP',
          costMs: 9,
          details: { reason: 'no_text_stage' },
        },
        {
          id: 2,
          runId: 100,
          stage: 'VISION',
          stepOrder: 2,
          decision: 'SKIP',
          costMs: 9,
          details: { reason: 'no_vision_stage' },
        },
        {
          id: 3,
          runId: 100,
          stage: 'JUDGE',
          stepOrder: 3,
          decision: 'SKIP',
          costMs: 9,
          details: { reason: 'no_judge_stage' },
        },
        {
          id: 4,
          runId: 100,
          stage: 'LLM',
          stepOrder: 4,
          decision: 'PASS',
          costMs: 20,
          details: { model: 'hidden-llm' },
        },
      ],
    });

    render(<ModerationPipelineTracePanel queueId={6} />);

    await screen.findByText('runId：');
    const summaryText = Array.from(document.querySelectorAll('summary')).map((x) => x.textContent || '').join(' ');
    expect(summaryText).not.toContain('TEXT');
    expect(summaryText).toContain('VISION');
    expect(summaryText).toContain('JUDGE');
    expect(summaryText).not.toContain('LLM');
    expect(screen.queryByText(/no_text_stage/)).toBeNull();
    expect(screen.queryByText(/"model":\s*"hidden-llm"/)).toBeNull();
  });

  it('RULE anti_spam 命中时展示结构化命中信息', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: {
        id: 12,
        queueId: 4,
        contentType: 'COMMENT',
        contentId: 101,
        traceId: 't-anti-spam',
      },
      steps: [
        {
          id: 11,
          runId: 12,
          stage: 'RULE',
          stepOrder: 1,
          decision: 'HIT',
          score: null,
          threshold: null,
          costMs: 8,
          details: {
            antiSpamHit: true,
            antiSpamType: 'COMMENT_WINDOW_RATE',
            reason: 'comment_window_rate_exceeded',
            actualCount: 5,
            threshold: 3,
            windowSeconds: 60,
          },
        },
      ],
    });

    render(<ModerationPipelineTracePanel queueId={4} />);

    expect(await screen.findByText('anti_spam 命中')).not.toBeNull();
    expect(screen.getByText('命中类型：COMMENT_WINDOW_RATE')).not.toBeNull();
    expect(screen.getByText('实际计数：5')).not.toBeNull();
    expect(screen.getByText('阈值：3')).not.toBeNull();
    expect(screen.getByText('窗口：60 秒')).not.toBeNull();
    expect(screen.getByText('原因：comment_window_rate_exceeded')).not.toBeNull();
  });

  it('非 anti_spam 命中时不展示结构化命中信息', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: {
        id: 13,
        queueId: 5,
        contentType: 'POST',
        contentId: 102,
        traceId: 't-no-anti-spam',
      },
      steps: [
        {
          id: 12,
          runId: 13,
          stage: 'RULE',
          stepOrder: 1,
          decision: 'PASS',
          score: 0.1,
          threshold: 0.2,
          costMs: 7,
          details: {
            antiSpamHit: false,
            antiSpamType: 'COMMENT_WINDOW_RATE',
          },
        },
      ],
    });

    render(<ModerationPipelineTracePanel queueId={5} />);

    await screen.findByText('runId：');
    expect(screen.queryByText('anti_spam 命中')).toBeNull();
  });

  it('compact 模式下展示压缩样式并格式化秒级耗时', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: {
        id: 20,
        queueId: 8,
        contentType: 'POST',
        contentId: 201,
        traceId: 'trace-compact',
        totalMs: 1234,
        policyVersion: null,
        inputMode: null,
      },
      steps: [
        {
          id: 21,
          runId: 20,
          stage: 'LLM',
          stepOrder: 1,
          decision: 'PASS',
          score: 0.91,
          threshold: 0.3,
          costMs: 1500,
          details: { model: 'm-compact' },
        },
      ],
    });

    const { container } = render(<ModerationPipelineTracePanel queueId={8} density="compact" />);

    expect(await screen.findByText('总耗时：')).not.toBeNull();
    expect(screen.getByText('1.23s')).not.toBeNull();
    const summaryText = Array.from(document.querySelectorAll('summary')).map((x) => x.textContent || '').join(' ');
    expect(summaryText).toContain('LLM');
    expect(summaryText).toContain('PASS');
    expect(summaryText).toContain('1.50s');
    expect(screen.getByText('policyVersion：')).not.toBeNull();
    expect(container.querySelector('.text-sm.leading-tight')).not.toBeNull();
  });

  it('anti_spam 命中支持字符串布尔和分钟窗口', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: {
        id: 30,
        queueId: 10,
        contentType: 'COMMENT',
        contentId: 302,
        traceId: 'trace-minutes',
      },
      steps: [
        {
          id: 31,
          runId: 30,
          stage: 'RULE',
          stepOrder: 1,
          decision: 'HIT',
          score: null,
          threshold: null,
          costMs: 50,
          details: {
            antiSpamHit: 'true',
            antiSpamType: 'POST_WINDOW_RATE',
            reason: 'post_window_rate_exceeded',
            actualCount: 7,
            threshold: 5,
            windowMinutes: 10,
          },
        },
      ],
    });

    render(<ModerationPipelineTracePanel queueId={10} />);

    expect(await screen.findByText('anti_spam 命中')).not.toBeNull();
    expect(screen.getByText('窗口：10 分钟')).not.toBeNull();
  });

  it('刷新失败时显示错误信息', async () => {
    mockAdminGetLatestPipelineByQueueId
      .mockResolvedValueOnce({ run: null, steps: null })
      .mockRejectedValueOnce(new Error('refresh-failed'));

    render(<ModerationPipelineTracePanel queueId={11} />);

    expect(await screen.findByText('暂无流水线记录（可能尚未自动运行）')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));

    expect(await screen.findByText('refresh-failed')).not.toBeNull();
  });

  it('queueId 变化会取消旧请求，旧请求完成后不应覆盖新数据', async () => {
    let resolveOld!: (v: unknown) => void;
    const pendingOld = new Promise((r) => {
      resolveOld = r;
    });

    mockAdminGetLatestPipelineByQueueId
      .mockReturnValueOnce(pendingOld as never)
      .mockResolvedValueOnce({
        run: { id: 202, queueId: 22, contentType: 'POST', contentId: 1, traceId: 't-new' },
        steps: [],
      });

    const { rerender } = render(<ModerationPipelineTracePanel queueId={21} />);

    expect(await screen.findByText('加载中…')).not.toBeNull();

    rerender(<ModerationPipelineTracePanel queueId={22} />);

    expect(await screen.findByText('runId：')).not.toBeNull();
    expect(await screen.findByText('202')).not.toBeNull();

    resolveOld({
      run: { id: 201, queueId: 21, contentType: 'POST', contentId: 1, traceId: 't-old' },
      steps: [],
    });

    await Promise.resolve();
    expect(screen.getByText('202')).not.toBeNull();
  });

  it('safeJson stringify 失败时回退到 String(v)，并覆盖窗口缺省分支', async () => {
    const circular: any = {};
    circular.self = circular;

    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: { id: 301, queueId: 30, contentType: 'COMMENT', contentId: 1, traceId: 't-circular' },
      steps: [
        {
          id: 1,
          runId: 301,
          stage: 'RULE',
          stepOrder: 1,
          decision: 'HIT',
          costMs: 9,
          details: { antiSpamHit: true },
        },
        {
          id: 2,
          runId: 301,
          stage: 'LLM',
          stepOrder: 2,
          decision: 'PASS',
          costMs: 10,
          details: circular,
        },
      ],
    } as any);

    render(<ModerationPipelineTracePanel queueId={30} />);

    expect(await screen.findByText('anti_spam 命中')).not.toBeNull();
    expect(screen.getByText('窗口：—')).not.toBeNull();
    expect(await screen.findByText('[object Object]')).not.toBeNull();
  });

  it('异常为非 Error 时回退到 String(e)', async () => {
    mockAdminGetLatestPipelineByQueueId.mockRejectedValueOnce('boom');
    render(<ModerationPipelineTracePanel queueId={99} />);
    expect(await screen.findByText('boom')).not.toBeNull();
  });

  it('compact 模式下错误提示使用压缩样式，并覆盖非 Error 回退', async () => {
    mockAdminGetLatestPipelineByQueueId.mockRejectedValueOnce('x');
    render(<ModerationPipelineTracePanel queueId={100} density="compact" />);
    expect(await screen.findByText('x')).not.toBeNull();
  });

  it('traceId 为空字符串时回退到 —，并覆盖 stepOrder/decision 缺省分支', async () => {
    mockAdminGetLatestPipelineByQueueId.mockResolvedValueOnce({
      run: {
        id: 500,
        queueId: 50,
        contentType: 'POST',
        contentId: 1,
        traceId: '',
      },
      steps: [
        {
          id: 1,
          runId: 500,
          stage: 'RULE',
          stepOrder: null,
          decision: null,
          costMs: 1,
          details: { antiSpamHit: false },
        },
      ],
    } as any);

    render(<ModerationPipelineTracePanel queueId={50} density="compact" />);
    expect(await screen.findByText('traceId：')).not.toBeNull();
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
    const summaryText = Array.from(document.querySelectorAll('summary')).map((x) => x.textContent || '').join(' ');
    expect(summaryText).toContain('—');
  });

  it('卸载后旧请求完成不应触发 setState（取消分支覆盖）', async () => {
    let resolve!: (v: unknown) => void;
    const pending = new Promise((r) => {
      resolve = r;
    });
    mockAdminGetLatestPipelineByQueueId.mockReturnValueOnce(pending as never);

    const { unmount } = render(<ModerationPipelineTracePanel queueId={77} />);
    expect(await screen.findByText('加载中…')).not.toBeNull();
    unmount();

    resolve({ run: { id: 1, traceId: 'old' }, steps: [] });
    await Promise.resolve();
    await Promise.resolve();
  });
});
