import { describe, expect, it } from 'vitest';
import { hasProfileDraftChanges, isValidWebsite, validateProfileFields } from './profileValidationUtils';

describe('profileValidationUtils', () => {
  it('treats blank optional profile fields as unchanged', () => {
    expect(
      hasProfileDraftChanges(
        {
          username: ' Alice ',
          bio: undefined,
          avatarUrl: '',
          location: undefined,
          website: undefined,
        },
        {
          username: 'Alice',
          bio: '   ',
          avatarUrl: '',
          location: '',
          website: ' ',
        },
      ),
    ).toBe(false);
  });

  it('detects changes after trimming profile fields', () => {
    expect(
      hasProfileDraftChanges(
        {
          username: 'Alice',
          bio: 'hello',
          avatarUrl: 'https://example.com/a.png',
          location: 'Shanghai',
          website: 'https://example.com',
        },
        {
          username: ' Alice 2 ',
          bio: 'hello',
          avatarUrl: 'https://example.com/a.png',
          location: 'Shanghai',
          website: 'https://example.com',
        },
      ),
    ).toBe(true);
  });

  it('validates website and profile fields', () => {
    expect(isValidWebsite('https://example.com')).toBe(true);
    expect(isValidWebsite('ftp://example.com')).toBe(false);
    expect(
      validateProfileFields({
        username: 'Alice',
        bio: '',
        location: '',
        website: 'ftp://example.com',
      }),
    ).toBe('网站链接格式不正确（需要 http/https）');
  });
});
