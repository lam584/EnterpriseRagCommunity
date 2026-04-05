import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { toProfileDraft, toProfileUpdateRequest, useProfilePageState } from './profilePageShared';

describe('profilePageShared', () => {
  it('maps nullable profile fields into editable draft strings', () => {
    expect(
      toProfileDraft({
        username: 'Alice',
        bio: undefined,
        avatarUrl: undefined,
        location: undefined,
        website: undefined,
      }),
    ).toEqual({
      username: 'Alice',
      bio: '',
      avatarUrl: '',
      location: '',
      website: '',
    });
  });

  it('builds update payload with trimmed username and nullable optional fields', () => {
    expect(
      toProfileUpdateRequest({
        username: ' Alice ',
        bio: ' ',
        avatarUrl: ' https://example.com/a.png ',
        location: '',
        website: 'https://example.com',
      }),
    ).toEqual({
      username: 'Alice',
      bio: null,
      avatarUrl: 'https://example.com/a.png',
      location: null,
      website: 'https://example.com',
    });
  });

  it('resets editable draft state from a loaded profile', () => {
    const { result } = renderHook(() => useProfilePageState());

    act(() => {
      result.current.resetDraftFrom({
        username: 'Alice',
        bio: 'Hello',
        avatarUrl: 'https://example.com/a.png',
        location: 'Shanghai',
        website: undefined,
      });
    });

    expect(result.current.username).toBe('Alice');
    expect(result.current.bio).toBe('Hello');
    expect(result.current.avatarUrl).toBe('https://example.com/a.png');
    expect(result.current.location).toBe('Shanghai');
    expect(result.current.website).toBe('');
    expect(result.current.postsPageNo).toBe(1);
    expect(result.current.postsReloadTick).toBe(0);
  });
});
