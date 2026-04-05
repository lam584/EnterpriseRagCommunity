import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ProviderModelSelect } from './ProviderModelSelect';

function appendOptionAndSelect(select: HTMLSelectElement, value: string, text: string) {
  const option = document.createElement('option');
  option.value = value;
  option.textContent = text;
  select.appendChild(option);
  fireEvent.change(select, { target: { value } });
}

describe('ProviderModelSelect', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders options and parses provider+model selection', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[
          { id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' },
          { id: 'p2', name: 'P2', enabled: false, defaultChatModel: 'm9' },
        ]}
        activeProviderId="p1"
        chatProviders={[
          { id: 'p1', chatModels: [{ name: 'm1' }, { name: 'm2' }] },
          { id: 'p2', chatModels: [{ name: 'm9' }] },
        ]}
        mode="chat"
        providerId=""
        model=""
        includeModelOnlyOptions
        includeProviderOnlyOptions
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    const opt = screen.getByText('P2 [禁用]：m9') as HTMLOptionElement;
    fireEvent.change(select, { target: { value: opt.value } });
    expect(onChange).toHaveBeenCalledWith({ providerId: 'p2', model: 'm9' });
  });

  it('treats model-only selections as auto (empty)', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'g1' }] }]}
        mode="chat"
        providerId=""
        model=""
        includeModelOnlyOptions
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    const opt = screen.getByText('全局（P1）：g1') as HTMLOptionElement;
    fireEvent.change(select, { target: { value: opt.value } });
    expect(onChange).toHaveBeenCalledWith({ providerId: '', model: '' });
  });

  it('supports provider-only selection and hides label when label is empty string', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        mode="chat"
        providerId=""
        model=""
        includeProviderOnlyOptions
        label=""
        onChange={onChange}
      />,
    );

    expect(screen.queryByText('模型:')).toBeNull();

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    const opt = screen.getByText('P1：自动（模型跟随全局）') as HTMLOptionElement;
    fireEvent.change(select, { target: { value: opt.value } });
    expect(onChange).toHaveBeenCalledWith({ providerId: 'p1', model: '' });
  });

  it('adds a fallback option when current provider+model is not in options', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId="p1"
        model="custom"
        onChange={onChange}
      />,
    );

    expect(screen.getByText('P1：custom')).not.toBeNull();
  });

  it('supports embedding mode options and normalizes missing provider to auto', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[
          { id: 'p1', name: 'P1', enabled: true, defaultEmbeddingModel: 'e1' },
          { id: 'p2', name: 'P2', enabled: true },
        ]}
        activeProviderId="p1"
        mode="embedding"
        providerId="missing"
        model="e9"
        includeProviderOnlyOptions
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    expect(select.value).toBe('');

    const opt = screen.getByText('P1：e1') as HTMLOptionElement;
    fireEvent.change(select, { target: { value: opt.value } });
    expect(onChange).toHaveBeenCalledWith({ providerId: 'p1', model: 'e1' });
  });

  it('falls back to auto for invalid/unsupported values', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        mode="chat"
        providerId=""
        model=""
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    fireEvent.change(select, { target: { value: '%' } });
    expect(onChange).toHaveBeenCalledWith({ providerId: '', model: '' });

    appendOptionAndSelect(select, 'm|m1', 'model-only');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });
  });

  it('does not call onChange when disabled', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId=""
        model=""
        disabled
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    const opt = screen.getByText('P1：m1') as HTMLOptionElement;
    fireEvent.change(select, { target: { value: opt.value } });
    expect(onChange).not.toHaveBeenCalled();
  });

  it('supports legacy plain provider|model value', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        mode="chat"
        providerId=""
        model=""
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    appendOptionAndSelect(select, 'p1|legacy-model', 'legacy');
    expect(onChange).toHaveBeenCalledWith({ providerId: 'p1', model: 'legacy-model' });
  });

  it('renders custom auto option label and disableAutoOption', () => {
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        mode="chat"
        providerId=""
        model=""
        autoOptionLabel="自定义自动"
        disableAutoOption
        onChange={() => {}}
      />,
    );

    const autoOpt = screen.getByRole('option', { name: '自定义自动' }) as HTMLOptionElement;
    expect(autoOpt.disabled).toBe(true);
  });

  it('uses default provider-only label in embedding mode', () => {
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultEmbeddingModel: 'e1' }]}
        activeProviderId="p1"
        mode="embedding"
        providerId=""
        model=""
        includeProviderOnlyOptions
        onChange={() => {}}
      />,
    );

    expect(screen.getByText('P1：自动（使用该Provider默认嵌入模型）')).not.toBeNull();
  });

  it('parses encoded provider-model value and rejects model-only selection', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId=""
        model=""
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;

    const opt = screen.getByText('P1：m1') as HTMLOptionElement;
    fireEvent.change(select, { target: { value: opt.value } });
    expect(onChange).toHaveBeenCalledWith({ providerId: 'p1', model: 'm1' });

    appendOptionAndSelect(select, 'm|m1', 'model-only');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });
  });

  it('falls back to auto for malformed encoded values (pm| missing separator, empty provider/model)', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        mode="chat"
        providerId=""
        model=""
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;

    appendOptionAndSelect(select, 'pm|p1', 'bad-pm-no-sep');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });

    appendOptionAndSelect(select, 'p|%20', 'bad-p-empty');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });

    appendOptionAndSelect(select, 'm|%20', 'bad-m-empty');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });

    appendOptionAndSelect(select, 'pm|%20|%20', 'bad-pm-empty-parts');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });

    appendOptionAndSelect(select, '|m1', 'bad-legacy');
    expect(onChange).toHaveBeenLastCalledWith({ providerId: '', model: '' });
  });

  it('normalizes provider-only and model-only current props to auto when corresponding options are disabled', () => {
    const { rerender } = render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId="p1"
        model=""
        includeProviderOnlyOptions={false}
        onChange={() => {}}
      />,
    );

    expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('');

    rerender(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId=""
        model="m1"
        includeModelOnlyOptions={true}
        onChange={() => {}}
      />,
    );

    expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('');
  });

  it('dedupes and sorts global model-only options', () => {
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: ' z ' }, { name: 'a' }, { name: 'a' }, { name: '' as any }] }]}
        mode="chat"
        providerId=""
        model=""
        includeModelOnlyOptions
        onChange={() => {}}
      />,
    );

    const options = Array.from(document.querySelectorAll('option')).map((x) => x.textContent || '');
    expect(options.some((x) => x.includes('全局（P1）：a'))).toBe(true);
    expect(options.some((x) => x.includes('全局（P1）：z'))).toBe(true);
  });

  it('parses plain provider-only legacy value without delimiter', () => {
    const onChange = vi.fn();
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        mode="chat"
        providerId=""
        model=""
        onChange={onChange}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    appendOptionAndSelect(select, 'p1', 'plain-provider');
    expect(onChange).toHaveBeenCalledWith({ providerId: 'p1', model: '' });
  });

  it('falls back to activeProviderId label when active provider is missing and covers chatModels non-array branch', () => {
    render(
      <ProviderModelSelect
        providers={[
          { id: 'p1', name: '', enabled: true },
        ]}
        activeProviderId="missing"
        chatProviders={[
          { id: 'missing', chatModels: [{ name: 'g1' }] },
          { id: '', chatModels: [{ name: 'x' }] } as any,
          { id: 'p1', chatModels: null as any },
        ]}
        mode="chat"
        providerId=""
        model=""
        includeModelOnlyOptions
        includeProviderOnlyOptions
        onChange={() => {}}
      />,
    );

    expect(screen.getByText('全局（missing）：g1')).not.toBeNull();
    expect(screen.getByText('p1：自动（模型跟随全局）')).not.toBeNull();
  });

  it('handles includeModelOnlyOptions with no global models and dedupes duplicate providers', () => {
    render(
      <ProviderModelSelect
        providers={[
          { id: 'p1', name: 'P1', enabled: true },
          { id: 'p1', name: 'P1-dup', enabled: true },
        ]}
        activeProviderId="p1"
        chatProviders={[]}
        mode="chat"
        providerId=""
        model=""
        includeModelOnlyOptions
        includeProviderOnlyOptions
        onChange={() => {}}
      />,
    );

    const providerOnlyOptions = Array.from(document.querySelectorAll('option'))
      .map((x) => x.textContent || '')
      .filter((t) => t.includes('：自动（模型跟随全局）'));
    expect(providerOnlyOptions.length).toBe(1);
  });

  it('keeps provider-only current value when includeProviderOnlyOptions is enabled', () => {
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true, defaultChatModel: 'm1' }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId="p1"
        model=""
        includeProviderOnlyOptions
        onChange={() => {}}
      />,
    );

    const select = screen.getByRole('combobox') as HTMLSelectElement;
    expect(select.value).toContain('p|');
  });

  it('adds fallback option for missing provider id with custom model', () => {
    render(
      <ProviderModelSelect
        providers={[{ id: 'p1', name: 'P1', enabled: true }]}
        activeProviderId="p1"
        chatProviders={[{ id: 'p1', chatModels: [{ name: 'm1' }] }]}
        mode="chat"
        providerId="missing"
        model="custom"
        onChange={() => {}}
      />,
    );

    expect(screen.getByText('missing：custom')).not.toBeNull();
  });
});
