import { describe, expect, it } from 'vitest';
import { createAiComposeChannelRouterState, routeAiComposeDelta } from './aiComposeChannelRouter';

describe('aiComposeChannelRouter', () => {
  it('routes <post> content into post buffer', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<post>hello</post>');
    expect(s.post).toBe('hello');
    expect(s.chat).toBe('');
    expect(s.hasPost).toBe(true);
  });

  it('supports cross-chunk open/close tags', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<po');
    routeAiComposeDelta(s, 'st>hello');
    routeAiComposeDelta(s, '</po');
    routeAiComposeDelta(s, 'st>');
    expect(s.post).toBe('hello');
    expect(s.chat).toBe('');
    expect(s.hasPost).toBe(true);
  });

  it('supports mixed-case tags across chunks', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<Po');
    routeAiComposeDelta(s, 'St>hello');
    routeAiComposeDelta(s, '</pO');
    routeAiComposeDelta(s, 'sT>');
    expect(s.post).toBe('hello');
    expect(s.chat).toBe('');
    expect(s.hasPost).toBe(true);
  });

  it('routes mixed chat and post', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<chat>Q1?</chat>');
    routeAiComposeDelta(s, '<post>BODY</post>');
    expect(s.chat).toBe('Q1?');
    expect(s.post).toBe('BODY');
  });

  it('treats text outside tags as chat', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, 'hi ');
    routeAiComposeDelta(s, 'there');
    expect(s.chat).toBe('hi there');
    expect(s.post).toBe('');
    expect(s.hasPost).toBe(false);
  });

  it('supports escaped tags', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '&lt;post&gt;A&lt;/post&gt;');
    expect(s.post).toBe('A');
    expect(s.chat).toBe('');
  });

  it('supports full-width tags', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, 'hi＜post＞A＜/post＞bye');
    expect(s.chat).toBe('hibye');
    expect(s.post).toBe('A');
  });

  it('treats unknown tags as plain text', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<nope>hi</nope>');
    expect(s.chat).toBe('<nope>hi</nope>');
    expect(s.post).toBe('');
  });

  it('treats non-tag ampersands as plain text', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, 'a&x');
    expect(s.chat).toBe('a&x');
  });

  it('returns no changes for empty delta', () => {
    const s = createAiComposeChannelRouterState();
    const res = routeAiComposeDelta(s, '');
    expect(res).toEqual({ postChanged: false, chatChanged: false });
    expect(s.chat).toBe('');
    expect(s.post).toBe('');
  });

  it('skips close tags when searching for an open tag', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '</post><post>A</post>');
    expect(s.chat).toBe('</post>');
    expect(s.post).toBe('A');
  });

  it('ignores mismatched close tags inside a mode', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<post>A</chat>B</post>');
    expect(s.chat).toBe('');
    expect(s.post).toBe('A</chat>B');
  });

  it('supports whitespace around tag names', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<  post >A</ post >');
    expect(s.chat).toBe('');
    expect(s.post).toBe('A');
  });

  it('keeps possible prefixes for incomplete tags', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '<post');
    expect(s.chat).toBe('');
    expect(s.post).toBe('');
    expect(s.remainder).toBe('<post');
    routeAiComposeDelta(s, '>A</post>');
    expect(s.post).toBe('A');
  });

  it('keeps possible prefixes for incomplete escaped tags', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '&lt;post');
    expect(s.remainder).toBe('&lt;post');
    routeAiComposeDelta(s, '&gt;A&lt;/post&gt;');
    expect(s.post).toBe('A');
  });

  it('treats tag shells without names as plain text', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '< >hi</ >');
    expect(s.chat).toBe('< >hi</ >');
    expect(s.post).toBe('');
  });

  it('keeps possible prefixes for lone tag starters', () => {
    const s = createAiComposeChannelRouterState();
    routeAiComposeDelta(s, '&lt;');
    expect(s.chat).toBe('');
    expect(s.post).toBe('');
    expect(s.remainder).toBe('&lt;');

    routeAiComposeDelta(s, '<');
    expect(s.chat).toBe('&lt;');
    expect(s.remainder).toBe('<');

    routeAiComposeDelta(s, '＜');
    expect(s.chat).toBe('&lt;<');
    expect(s.remainder).toBe('＜');
  });

  it('returns no changes for undefined delta', () => {
    const s = createAiComposeChannelRouterState();
    const res = routeAiComposeDelta(s, undefined as any);
    expect(res).toEqual({ postChanged: false, chatChanged: false });
    expect(s.chat).toBe('');
    expect(s.post).toBe('');
    expect(s.remainder).toBe('');
  });
});
