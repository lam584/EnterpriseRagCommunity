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
});
