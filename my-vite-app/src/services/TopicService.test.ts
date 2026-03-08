import { beforeEach, describe, expect, it, vi } from 'vitest';

const { axiosMock } = vi.hoisted(() => {
  return {
    axiosMock: {
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
    },
  };
});

vi.mock('axios', () => {
  return { default: axiosMock };
});

import { createTopic, deleteTopic, fetchCategories, updateTopic } from './TopicService';

describe('TopicService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('fetchCategories/createTopic/updateTopic return res.data', async () => {
    axiosMock.get.mockResolvedValueOnce({ data: [{ id: 1, name: 'n' }] });
    await expect(fetchCategories()).resolves.toMatchObject([{ id: 1, name: 'n' }]);

    axiosMock.post.mockResolvedValueOnce({ data: { id: 2, name: 'n2' } });
    await expect(createTopic({ name: 'n2' })).resolves.toMatchObject({ id: 2, name: 'n2' });

    axiosMock.put.mockResolvedValueOnce({ data: { id: 2, name: 'n3' } });
    await expect(updateTopic(2, { name: 'n3' })).resolves.toMatchObject({ id: 2, name: 'n3' });
  });

  it('deleteTopic calls delete', async () => {
    axiosMock.delete.mockResolvedValueOnce({ data: {} });
    await expect(deleteTopic(9)).resolves.toBeUndefined();
    expect(axiosMock.delete).toHaveBeenCalledWith('/api/topics/9');
  });

  it('propagates axios errors', async () => {
    axiosMock.get.mockRejectedValueOnce(new Error('boom'));
    await expect(fetchCategories()).rejects.toThrow('boom');

    axiosMock.delete.mockRejectedValueOnce(new Error('boom2'));
    await expect(deleteTopic(1)).rejects.toThrow('boom2');
  });
});

