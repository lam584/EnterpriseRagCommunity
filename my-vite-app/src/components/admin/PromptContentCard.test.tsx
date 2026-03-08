import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import PromptContentCard from './PromptContentCard';

describe('PromptContentCard', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders missing state when draft is null', () => {
    render(<PromptContentCard title="t" draft={null} editing={false} />);
    expect(screen.getByText('未找到对应提示词内容')).not.toBeNull();
  });

  it('renders read-only badge when not editing', () => {
    render(
      <PromptContentCard
        title="t"
        draft={{ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' }}
        editing={false}
        showName
      />,
    );
    expect(screen.getByText('只读')).not.toBeNull();
    const input = screen.getByDisplayValue('n') as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it('calls onChange when editing and fields change', () => {
    const onChange = vi.fn();
    render(
      <PromptContentCard
        title="t"
        draft={{ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' }}
        editing
        onChange={onChange}
        showName
      />,
    );

    const nameInput = screen.getAllByDisplayValue('n')[0] as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: 'n2' } });
    expect(onChange).toHaveBeenCalledWith({ name: 'n2', systemPrompt: 's', userPromptTemplate: 'u' });
  });

  it('renders hint and respects show flags', () => {
    render(
      <PromptContentCard
        title="t"
        hint="h"
        draft={{ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' }}
        editing={false}
        showName={false}
        showSystemPrompt={false}
        showUserPromptTemplate={true}
      />,
    );

    expect(screen.getByText('h')).not.toBeNull();
    expect(screen.queryByDisplayValue('n')).toBeNull();
    expect(screen.queryByDisplayValue('s')).toBeNull();
    expect(screen.getByDisplayValue('u')).not.toBeNull();
  });

  it('disables fields when editing but onChange is missing', () => {
    render(<PromptContentCard title="t" draft={{ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' }} editing />);

    const sys = screen.getByDisplayValue('s') as HTMLTextAreaElement;
    const user = screen.getByDisplayValue('u') as HTMLTextAreaElement;
    expect(sys.disabled).toBe(true);
    expect(user.disabled).toBe(true);
    expect(screen.queryByText('只读')).toBeNull();
  });

  it('calls onChange for systemPrompt and userPromptTemplate updates', () => {
    const onChange = vi.fn();
    render(
      <PromptContentCard
        title="t"
        draft={{ name: 'n', systemPrompt: 's', userPromptTemplate: 'u' }}
        editing
        onChange={onChange}
        showName={false}
      />,
    );

    const sys = screen.getByDisplayValue('s') as HTMLTextAreaElement;
    fireEvent.change(sys, { target: { value: 's2' } });
    expect(onChange).toHaveBeenCalledWith({ name: 'n', systemPrompt: 's2', userPromptTemplate: 'u' });

    const user = screen.getByDisplayValue('u') as HTMLTextAreaElement;
    fireEvent.change(user, { target: { value: 'u2' } });
    expect(onChange).toHaveBeenCalledWith({ name: 'n', systemPrompt: 's', userPromptTemplate: 'u2' });
  });
});
