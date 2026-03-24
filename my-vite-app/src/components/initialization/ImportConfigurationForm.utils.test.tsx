import { afterEach, describe, expect, it, vi } from 'vitest';
import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import {
  HELP_FALLBACK_TEXT,
  VisibilityToggleButton,
  getConfigInputType,
  getConfigInputValue,
  getConfigLabel,
  getHelpText,
  getReadOnlyInputClass,
  isSensitiveConfigKey,
} from './ImportConfigurationForm.utils';

describe('ImportConfigurationForm.utils', () => {
  afterEach(() => cleanup());

  it('detects sensitive keys (KEY/PASSWORD)', () => {
    expect(isSensitiveConfigKey('APP_AI_API_KEY')).toBe(true);
    expect(isSensitiveConfigKey('APP_MAIL_PASSWORD')).toBe(true);
    expect(isSensitiveConfigKey('APP_MAIL_HOST')).toBe(false);
  });

  it('returns config label or falls back to key', () => {
    expect(getConfigLabel('APP_AI_API_KEY')).toMatch(/AI API 密钥/);
    expect(getConfigLabel('APP_SITE_COPYRIGHT')).toBe('版权所有文案');
    expect(getConfigLabel('UNKNOWN_KEY')).toBe('UNKNOWN_KEY');
  });

  it('returns help text or falls back to default help', () => {
    expect(getHelpText('APP_AI_API_KEY')).toMatch(/AI 服务的 API 密钥/);
    expect(getHelpText('APP_SITE_COPYRIGHT')).toMatch(/版权文案/);
    expect(getHelpText('UNKNOWN_KEY')).toBe(HELP_FALLBACK_TEXT);
  });

  it('computes input type based on sensitivity and visibility', () => {
    expect(getConfigInputType('APP_AI_API_KEY', false)).toBe('password');
    expect(getConfigInputType('APP_AI_API_KEY', true)).toBe('text');
    expect(getConfigInputType('APP_MAIL_HOST', false)).toBe('text');
  });

  it('computes input value based on visibility and encrypted fallback', () => {
    const encryptedValues = { APP_AI_API_KEY: 'ENC' };
    const configs = { APP_AI_API_KEY: 'RAW', APP_MAIL_HOST: 'H' };

    expect(getConfigInputValue('APP_AI_API_KEY', true, encryptedValues, configs)).toBe('ENC');
    expect(getConfigInputValue('APP_AI_API_KEY', true, {}, configs)).toBe('RAW');
    expect(getConfigInputValue('APP_AI_API_KEY', false, encryptedValues, configs)).toBe('RAW');
    expect(getConfigInputValue('APP_MAIL_HOST', true, {}, configs)).toBe('H');
  });

  it('computes readOnly class based on visibility', () => {
    expect(getReadOnlyInputClass(true)).toBe('bg-gray-50 text-gray-500');
    expect(getReadOnlyInputClass(false)).toBeUndefined();
    expect(getReadOnlyInputClass(undefined)).toBeUndefined();
  });

  it('renders no toggle button for non-sensitive keys', () => {
    const onToggle = vi.fn();
    const { container } = render(<VisibilityToggleButton fieldKey="APP_MAIL_HOST" visible={false} onToggle={onToggle} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders toggle button for sensitive keys and calls handler', () => {
    const onToggle = vi.fn();
    const { container, rerender } = render(
      <VisibilityToggleButton fieldKey="APP_AI_API_KEY" visible={false} onToggle={onToggle} />
    );

    const btn = screen.getByRole('button');
    fireEvent.click(btn);
    expect(onToggle).toHaveBeenCalledTimes(1);
    expect(container.querySelector('svg.lucide-eye')).not.toBeNull();

    rerender(<VisibilityToggleButton fieldKey="APP_AI_API_KEY" visible={true} onToggle={onToggle} />);
    expect(container.querySelector('svg.lucide-eye-off')).not.toBeNull();
  });
});
