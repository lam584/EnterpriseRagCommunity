import { describe, expect, it } from 'vitest';
import {
  normAssistantValue,
  pickAssistantModel,
  pickAssistantProviderId,
} from './assistantOptionsUtils';

describe('assistantOptionsUtils', () => {
  it('prefers stored provider, then active provider, then first provider', () => {
    const providers = [
      { id: 'p1', chatModels: [] },
      { id: 'p2', chatModels: [] },
    ] as any;

    expect(pickAssistantProviderId({ activeProviderId: 'p2' } as any, providers, 'p1')).toBe('p1');
    expect(pickAssistantProviderId({ activeProviderId: 'p2' } as any, providers, 'missing')).toBe('p2');
    expect(pickAssistantProviderId({ activeProviderId: 'missing' } as any, providers, '')).toBe('p1');
  });

  it('picks stored model, then provider default, then flagged default, then first model', () => {
    const provider = {
      defaultChatModel: 'm2',
      chatModels: [
        { name: 'm1' },
        { name: 'm2', isDefault: true },
        { name: 'm3' },
      ],
    } as any;

    expect(pickAssistantModel(provider, 'm3')).toBe('m3');
    expect(pickAssistantModel(provider, 'missing')).toBe('m2');
    expect(pickAssistantModel({ defaultChatModel: '', chatModels: [{ name: 'm1' }, { name: 'm2', isDefault: true }] } as any, '')).toBe('m2');
    expect(pickAssistantModel({ defaultChatModel: '', chatModels: [{ name: 'm1' }] } as any, '')).toBe('m1');
  });

  it('normalizes only string values', () => {
    expect(normAssistantValue('  x  ')).toBe('x');
    expect(normAssistantValue(1)).toBe('');
  });
});
