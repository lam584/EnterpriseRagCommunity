import React from 'react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, within, waitFor } from '@testing-library/react';
import ImportConfigurationForm from './ImportConfigurationForm';
import type { CheckEnvResponse } from '../../services/setupService';

const navigate = vi.fn();

const toastMocks = vi.hoisted(() => {
  return {
    success: vi.fn(),
    error: vi.fn(),
  };
});

const setupServiceMocks = vi.hoisted(() => {
  return {
    generateTotpKey: vi.fn(),
    saveSetupConfig: vi.fn(),
    testEsConnection: vi.fn(),
    initIndices: vi.fn(),
    completeSetup: vi.fn(),
    checkEnvFile: vi.fn(async (): Promise<CheckEnvResponse> => ({ exists: false })),
    checkIndicesStatus: vi.fn(),
    encryptValue: vi.fn(),
  };
});

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = (await importOriginal()) as any;
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

vi.mock('react-hot-toast', () => {
  return {
    default: {
      success: toastMocks.success,
      error: toastMocks.error,
    },
  };
});

vi.mock('../../services/setupService', () => {
  return {
    generateTotpKey: setupServiceMocks.generateTotpKey,
    saveSetupConfig: setupServiceMocks.saveSetupConfig,
    testEsConnection: setupServiceMocks.testEsConnection,
    initIndices: setupServiceMocks.initIndices,
    completeSetup: setupServiceMocks.completeSetup,
    checkEnvFile: setupServiceMocks.checkEnvFile,
    checkIndicesStatus: setupServiceMocks.checkIndicesStatus,
    encryptValue: setupServiceMocks.encryptValue,
  };
});

const getInputByKey = (key: string) => {
  const span = screen.getByText(`(${key})`);
  const label = span.closest('label');
  if (!label) throw new Error(`Label not found for key: ${key}`);
  const fieldRoot = label.parentElement?.parentElement as HTMLElement | null;
  if (!fieldRoot) throw new Error(`Field root not found for key: ${key}`);
  const input = fieldRoot.querySelector('input') as HTMLInputElement | null;
  if (!input) throw new Error(`Input not found for key: ${key}`);
  return input;
};

const getHelpButtonByKey = (key: string) => {
  const span = screen.getByText(`(${key})`);
  const label = span.closest('label');
  if (!label) throw new Error(`Label not found for key: ${key}`);
  const header = label.parentElement as HTMLElement | null;
  if (!header) throw new Error(`Field header not found for key: ${key}`);
  const btn = header.querySelector('button') as HTMLButtonElement | null;
  if (!btn) throw new Error(`Help button not found for key: ${key}`);
  return btn;
};

const getVisibilityButtonByKey = (key: string) => {
  const span = screen.getByText(`(${key})`);
  const label = span.closest('label');
  if (!label) throw new Error(`Label not found for key: ${key}`);
  const fieldRoot = label.parentElement?.parentElement as HTMLElement | null;
  if (!fieldRoot) throw new Error(`Field root not found for key: ${key}`);
  const icon = fieldRoot.querySelector('svg.lucide-eye, svg.lucide-eye-off') as SVGElement | null;
  const btn = icon?.closest('button') as HTMLButtonElement | null;
  if (!btn) throw new Error(`Visibility button not found for key: ${key}`);
  return btn;
};

const goToEsStep = async () => {
  render(<ImportConfigurationForm />);
  fireEvent.click(screen.getByText('下一步'));
  await screen.findByText('ES 初始化');
};

const fillAdminAccountForm = () => {
  fireEvent.change(screen.getByPlaceholderText('请输入管理员邮箱'), { target: { value: 'a@b.com' } });
  fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: 'u' } });
  fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: 'p' } });
  fireEvent.change(screen.getByPlaceholderText('请再次输入密码'), { target: { value: 'p' } });
};

const renderAndGoToAdminAccountStep = async () => {
  render(<ImportConfigurationForm />);
  fireEvent.click(screen.getByText('跳过'));
  await screen.findByText('创建管理员账户');
};

const renderAndFillAdminAccountStep = async (values?: {
  email?: string;
  username?: string;
  password?: string;
  confirmPassword?: string;
}) => {
  await renderAndGoToAdminAccountStep();
  fireEvent.change(screen.getByPlaceholderText('请输入管理员邮箱'), { target: { value: values?.email ?? 'a@b.com' } });
  fireEvent.change(screen.getByPlaceholderText('请输入用户名'), { target: { value: values?.username ?? 'u' } });
  fireEvent.change(screen.getByPlaceholderText('请输入密码'), { target: { value: values?.password ?? 'p' } });
  fireEvent.change(screen.getByPlaceholderText('请再次输入密码'), { target: { value: values?.confirmPassword ?? values?.password ?? 'p' } });
};

describe('ImportConfigurationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupServiceMocks.checkEnvFile.mockResolvedValue({ exists: false });
  });

  afterEach(() => {
    cleanup();
  });

  it('validates required fields on final submit', async () => {
    render(<ImportConfigurationForm />);
    fireEvent.click(screen.getByText('跳过'));
    await screen.findByText('创建管理员账户');

    fireEvent.click(screen.getByText('完成设置'));
    await screen.findByText('请填写所有必填项');
  });

  it('renders step1 and does not show env modal when env does not exist', async () => {
    setupServiceMocks.checkEnvFile.mockResolvedValue({ exists: false });
    render(<ImportConfigurationForm />);

    await screen.findByText('系统初始化向导');
    await screen.findByText('密钥配置');
    expect(screen.queryByText('检测到配置文件')).toBeNull();
  });

  it('opens and closes help modal', async () => {
    render(<ImportConfigurationForm />);
    await screen.findByText('系统初始化向导');

    fireEvent.click(getHelpButtonByKey('APP_AI_API_KEY'));
    await screen.findByText('配置说明: APP_AI_API_KEY');
    await screen.findByText(/AI 服务的 API 密钥/);

    fireEvent.click(screen.getByText('关闭'));
    expect(screen.queryByText('配置说明: APP_AI_API_KEY')).toBeNull();
  });

  it('generates TOTP master key and writes it into input', async () => {
    setupServiceMocks.generateTotpKey.mockResolvedValue('GEN_KEY');
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('生成'));
    expect(setupServiceMocks.generateTotpKey).toHaveBeenCalledTimes(1);

    await waitFor(() => {
      expect(getInputByKey('APP_TOTP_MASTER_KEY').value).toBe('GEN_KEY');
    });
  });

  it('shows error when generating key fails', async () => {
    setupServiceMocks.generateTotpKey.mockRejectedValue(new Error('boom'));
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('生成'));
    await screen.findByText('生成密钥失败');
  });

  it('toggles sensitive field visibility with encryption success', async () => {
    setupServiceMocks.encryptValue.mockResolvedValue('ENC');
    render(<ImportConfigurationForm />);

    const input = getInputByKey('APP_AI_API_KEY');
    fireEvent.change(input, { target: { value: 'RAW' } });

    fireEvent.click(getVisibilityButtonByKey('APP_AI_API_KEY'));
    expect(setupServiceMocks.encryptValue).toHaveBeenCalledWith('RAW');

    await waitFor(() => {
      const inputAfter = getInputByKey('APP_AI_API_KEY');
      expect(inputAfter.readOnly).toBe(true);
      expect(inputAfter.value).toBe('ENC');
    });

    fireEvent.click(getVisibilityButtonByKey('APP_AI_API_KEY'));

    await waitFor(() => {
      const inputHidden = getInputByKey('APP_AI_API_KEY');
      expect(inputHidden.readOnly).toBe(false);
      expect(inputHidden.value).toBe('RAW');
    });

    fireEvent.click(getVisibilityButtonByKey('APP_AI_API_KEY'));
    expect(setupServiceMocks.encryptValue).toHaveBeenCalledTimes(1);

    await waitFor(() => {
      const inputAfter2 = getInputByKey('APP_AI_API_KEY');
      expect(inputAfter2.readOnly).toBe(true);
      expect(inputAfter2.value).toBe('ENC');
    });
  });

  it('toggles PASSWORD field visibility and encrypts it', async () => {
    setupServiceMocks.encryptValue.mockResolvedValue('ENC_PWD');
    render(<ImportConfigurationForm />);

    const input = getInputByKey('APP_MAIL_PASSWORD');
    fireEvent.change(input, { target: { value: 'RAW_PWD' } });

    fireEvent.click(getVisibilityButtonByKey('APP_MAIL_PASSWORD'));
    expect(setupServiceMocks.encryptValue).toHaveBeenCalledWith('RAW_PWD');

    await waitFor(() => {
      const inputAfter = getInputByKey('APP_MAIL_PASSWORD');
      expect(inputAfter.readOnly).toBe(true);
      expect(inputAfter.value).toBe('ENC_PWD');
    });
  });

  it('shows sensitive field without encrypt when value is empty', async () => {
    render(<ImportConfigurationForm />);

    const input = getInputByKey('APP_AI_API_KEY');
    expect(input.value).toBe('');

    fireEvent.click(getVisibilityButtonByKey('APP_AI_API_KEY'));
    expect(setupServiceMocks.encryptValue).not.toHaveBeenCalled();

    await waitFor(() => {
      const inputAfter = getInputByKey('APP_AI_API_KEY');
      expect(inputAfter.readOnly).toBe(true);
      expect(inputAfter.value).toBe('');
    });
  });

  it('does not toggle visibility when encryption fails', async () => {
    setupServiceMocks.encryptValue.mockRejectedValue(new Error('boom'));
    render(<ImportConfigurationForm />);

    const input = getInputByKey('APP_AI_API_KEY');
    fireEvent.change(input, { target: { value: 'RAW' } });

    fireEvent.click(getVisibilityButtonByKey('APP_AI_API_KEY'));
    expect(setupServiceMocks.encryptValue).toHaveBeenCalledWith('RAW');
    await waitFor(() => {
      expect(toastMocks.error).toHaveBeenCalledWith('加密失败');
    });

    const inputAfter = getInputByKey('APP_AI_API_KEY');
    expect(inputAfter.readOnly).toBe(false);
    expect(inputAfter.value).toBe('RAW');
  });

  it('validates password mismatch', async () => {
    await renderAndFillAdminAccountStep({ password: 'p1', confirmPassword: 'p2' });

    fireEvent.click(screen.getByText('完成设置'));
    await screen.findByText('密码不匹配');
  });

  it('shows env file modal and can cancel without importing', async () => {
    setupServiceMocks.checkEnvFile.mockResolvedValue({ exists: true });
    render(<ImportConfigurationForm />);

    await screen.findByText('检测到配置文件');
    const dialog = screen.getByText('检测到配置文件').closest('div');
    if (!dialog) throw new Error('env file dialog not found');
    fireEvent.click(within(dialog).getByRole('button', { name: '取消' }));

    expect(screen.queryByText('检测到配置文件')).toBeNull();
    expect(toastMocks.success).not.toHaveBeenCalledWith('配置导入成功');
  });

  it('shows env file modal and requires local file import', async () => {
    setupServiceMocks.checkEnvFile.mockResolvedValue({ exists: true });
    render(<ImportConfigurationForm />);

    await screen.findByText('检测到配置文件');
    const dialog = screen.getByText('检测到配置文件').closest('div');
    if (!dialog) throw new Error('env file dialog not found');
    fireEvent.click(within(dialog).getByRole('button', { name: '导入配置' }));

    expect(screen.queryByText('检测到配置文件')).toBeNull();
    expect(toastMocks.success).not.toHaveBeenCalledWith('配置导入成功');
  });

  it('imports config via file input (FileReader)', async () => {
    const originalFileReader = globalThis.FileReader;
    class MockFileReader {
      public onload: ((evt: any) => void) | null = null;
      readAsText(_file: any) {
        this.onload?.({ target: { result: 'APP_AI_API_KEY=K2' } });
      }
    }
    globalThis.FileReader = MockFileReader as any;

    try {
      render(<ImportConfigurationForm />);
      await screen.findByText('系统初始化向导');

      const inputEl = document.getElementById('config-upload') as HTMLInputElement | null;
      if (!inputEl) throw new Error('config-upload input not found');

      fireEvent.change(inputEl, { target: { files: [new File(['x'], 'a.env', { type: 'text/plain' })] } });

      expect(toastMocks.success).toHaveBeenCalledWith('配置导入成功');
      expect(getInputByKey('APP_AI_API_KEY').value).toBe('K2');
    } finally {
      globalThis.FileReader = originalFileReader;
    }
  });

  it('ignores file import when no file is selected', async () => {
    const originalFileReader = globalThis.FileReader;
    const readerCtor = vi.fn();
    globalThis.FileReader = readerCtor as any;

    try {
      render(<ImportConfigurationForm />);
      await screen.findByText('系统初始化向导');

      const inputEl = document.getElementById('config-upload') as HTMLInputElement | null;
      if (!inputEl) throw new Error('config-upload input not found');

      fireEvent.change(inputEl, { target: { files: [] } });
      expect(readerCtor).not.toHaveBeenCalled();
    } finally {
      globalThis.FileReader = originalFileReader;
    }
  });

  it('auto tests ES connection on entering step2 when config is already imported', async () => {
    const originalFileReader = globalThis.FileReader;
    class MockFileReader {
      public onload: ((evt: any) => void) | null = null;
      readAsText(_file: any) {
        this.onload?.({ target: { result: 'APP_ES_API_KEY=ESK' } });
      }
    }
    globalThis.FileReader = MockFileReader as any;

    setupServiceMocks.testEsConnection.mockResolvedValue({ success: true });
    setupServiceMocks.checkIndicesStatus.mockResolvedValue({
      ad_violation_samples_v1: '未创建',
      rag_post_chunks_v1_comments: '未创建',
      rag_post_chunks_v1: '未创建',
    });

    try {
      render(<ImportConfigurationForm />);
      await screen.findByText('系统初始化向导');

      const inputEl = document.getElementById('config-upload') as HTMLInputElement | null;
      if (!inputEl) throw new Error('config-upload input not found');
      fireEvent.change(inputEl, { target: { files: [new File(['x'], 'a.env', { type: 'text/plain' })] } });

      fireEvent.click(screen.getByText('下一步'));
      await screen.findByText('ES 初始化');

      await waitFor(() => {
        expect(setupServiceMocks.testEsConnection).toHaveBeenCalledTimes(1);
      });

      await screen.findByText('已连接');
      expect(toastMocks.success).toHaveBeenCalledWith('连接成功！');
    } finally {
      globalThis.FileReader = originalFileReader;
    }
  });

  it('tests ES connection successfully and displays indices status', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: true });
    setupServiceMocks.checkIndicesStatus.mockResolvedValue({
      ad_violation_samples_v1: '已创建',
      rag_post_chunks_v1_comments: '未创建',
      rag_post_chunks_v1: '已创建',
    });
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');

    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));

    await screen.findByText('已连接');
    expect(toastMocks.success).toHaveBeenCalledWith('连接成功！');

    const created = await screen.findAllByText('状态：已创建');
    expect(created).toHaveLength(2);
    const notCreated = await screen.findAllByText('状态：未创建');
    expect(notCreated).toHaveLength(1);
  });

  it('skips testEsConnection on create when already connected', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: true });
    setupServiceMocks.checkIndicesStatus.mockResolvedValue({
      ad_violation_samples_v1: '未创建',
      rag_post_chunks_v1_comments: '未创建',
      rag_post_chunks_v1: '未创建',
    });
    setupServiceMocks.initIndices.mockResolvedValue(undefined);
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));
    await screen.findByText('已连接');
    expect(setupServiceMocks.testEsConnection).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText('创建'));
    await screen.findByText('创建管理员账户');
    expect(setupServiceMocks.initIndices).toHaveBeenCalledTimes(1);
    expect(setupServiceMocks.testEsConnection).toHaveBeenCalledTimes(1);
  });

  it('shows error and stays on step2 when create fails to connect', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: false, message: '401 Unauthorized' });
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('创建'));

    await screen.findByText('认证失败：请检查 ES API Key 或密码是否正确');
    screen.getByText('ES 初始化');
    expect(setupServiceMocks.initIndices).not.toHaveBeenCalled();
  });

  it('disables create button when ES uri is empty', async () => {
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');

    fireEvent.change(getInputByKey('spring.elasticsearch.uris'), { target: { value: '' } });

    const createBtn = screen.getByRole('button', { name: '创建' });
    expect((createBtn as HTMLButtonElement).disabled).toBe(true);
    expect((createBtn as HTMLButtonElement).className).toContain('bg-gray-400');
  });

  it('shows friendly error message for ES auth failure', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: false, message: '401 Unauthorized' });
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));

    await screen.findByText('认证失败：请检查 ES API Key 或密码是否正确');
  });

  it('shows friendly error for security_exception message', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: false, message: 'security_exception: invalid api key' });
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));

    await screen.findByText('认证失败：请检查 ES API Key 或密码是否正确');
  });

  it('shows friendly error message for ES connection refused', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: false, message: 'Connection refused' });
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));

    await screen.findByText('连接失败：无法连接到 ES 服务器，请检查地址配置');
  });

  it('falls back to unknown error for empty error message', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: false, message: '' });
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));

    await screen.findByText('未知错误');
  });

  it('shows raw error when ES test throws non-friendly message', async () => {
    setupServiceMocks.testEsConnection.mockRejectedValue(new Error('custom es error'));
    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('测试连接'));

    await screen.findByText('custom es error');
  });

  it('creates indices with selected indices and proceeds to step3', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: true });
    setupServiceMocks.initIndices.mockResolvedValue(undefined);
    await goToEsStep();
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });

    const toUncheck = screen.getByRole('checkbox', { name: 'rag_post_chunks_v1' });
    fireEvent.click(toUncheck);

    fireEvent.click(screen.getByText('创建'));
    await screen.findByText('创建管理员账户');

    expect(setupServiceMocks.initIndices).toHaveBeenCalledTimes(1);
    const arg = setupServiceMocks.initIndices.mock.calls[0]?.[0] as string[];
    expect(arg).toEqual(['ad_violation_samples_v1', 'rag_post_chunks_v1_comments', 'rag_file_assets_v1']);
    expect(toastMocks.success).toHaveBeenCalledWith('索引初始化成功');
  });

  it('re-checks an index and includes it again in initIndices args', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: true });
    setupServiceMocks.initIndices.mockResolvedValue(undefined);
    await goToEsStep();
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });

    const cb = screen.getByRole('checkbox', { name: 'rag_post_chunks_v1' });
    fireEvent.click(cb);
    fireEvent.click(cb);

    fireEvent.click(screen.getByText('创建'));
    await screen.findByText('创建管理员账户');

    const arg = setupServiceMocks.initIndices.mock.calls[0]?.[0] as string[];
    expect(arg).toEqual(['ad_violation_samples_v1', 'rag_post_chunks_v1_comments', 'rag_file_assets_v1', 'rag_post_chunks_v1']);
  });

  it('shows friendly error and toast when final submit fails', async () => {
    setupServiceMocks.saveSetupConfig.mockRejectedValue({ message: '401 Unauthorized' });
    await renderAndFillAdminAccountStep();

    fireEvent.click(screen.getByText('完成设置'));
    await screen.findByText('认证失败：请检查 ES API Key 或密码是否正确');
    expect(toastMocks.error).toHaveBeenCalledWith('认证失败：请检查 ES API Key 或密码是否正确');
  });

  it('submits setup, initializes indices, and navigates to login', async () => {
    setupServiceMocks.saveSetupConfig.mockResolvedValue(undefined);
    setupServiceMocks.initIndices.mockResolvedValue(undefined);
    setupServiceMocks.completeSetup.mockResolvedValue(undefined);

    await renderAndFillAdminAccountStep();

    fireEvent.click(screen.getByText('完成设置'));

    await screen.findByText('正在处理中，请稍候...');
    await screen.findByText('系统初始化向导');

    expect(setupServiceMocks.saveSetupConfig).toHaveBeenCalledTimes(1);
    expect(setupServiceMocks.initIndices).toHaveBeenCalledTimes(1);
    expect(setupServiceMocks.completeSetup).toHaveBeenCalledWith({ email: 'a@b.com', username: 'u', password: 'p' });
    expect(navigate).toHaveBeenCalledWith('/login', { state: { setupJustCompleted: true } });
  });

  it('skips initIndices on final submit when indices are already created', async () => {
    setupServiceMocks.testEsConnection.mockResolvedValue({ success: true });
    setupServiceMocks.initIndices.mockResolvedValue(undefined);
    setupServiceMocks.saveSetupConfig.mockResolvedValue(undefined);
    setupServiceMocks.completeSetup.mockResolvedValue(undefined);

    render(<ImportConfigurationForm />);

    fireEvent.click(screen.getByText('下一步'));
    await screen.findByText('ES 初始化');
    fireEvent.change(getInputByKey('APP_ES_API_KEY'), { target: { value: 'ESK' } });
    fireEvent.click(screen.getByText('创建'));
    await screen.findByText('创建管理员账户');

    fillAdminAccountForm();
    fireEvent.click(screen.getByText('完成设置'));

    await waitFor(() => {
      expect(setupServiceMocks.completeSetup).toHaveBeenCalledTimes(1);
    });
    expect(setupServiceMocks.initIndices).toHaveBeenCalledTimes(1);
  });

  it('handles checkEnvFile rejection by logging error and continuing', async () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    setupServiceMocks.checkEnvFile.mockRejectedValue(new Error('env read failed'));

    render(<ImportConfigurationForm />);
    await screen.findByText('系统初始化向导');

    expect(screen.queryByText('检测到配置文件')).toBeNull();
    expect(errSpy).toHaveBeenCalled();
  });
});
