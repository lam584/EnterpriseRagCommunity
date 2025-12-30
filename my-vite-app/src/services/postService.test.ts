// import { describe, expect, it, vi } from 'vitest';
// import { searchPosts } from './postService';
//
// describe('postService.searchPosts', () => {
//   it('omits status when status=ALL by default (backward compatible)', async () => {
//     const fetchMock = vi.fn(async () => ({
//       ok: true,
//       json: async () => ({ content: [] }),
//     }));
//
//     vi.stubGlobal('fetch', fetchMock as unknown as typeof fetch);
//
//     try {
//       await searchPosts({ status: 'ALL', page: 1, pageSize: 20 });
//
//       expect(fetchMock).toHaveBeenCalledTimes(1);
//       const url = String(fetchMock.mock.calls[0]?.[0]);
//       expect(url).not.toContain('status=ALL');
//     } finally {
//       vi.unstubAllGlobals();
//     }
//   });
//
//   it('keeps status=ALL when preserveAllStatus=true (admin pages)', async () => {
//     const fetchMock = vi.fn(async () => ({
//       ok: true,
//       json: async () => ({ content: [] }),
//     }));
//
//     vi.stubGlobal('fetch', fetchMock as unknown as typeof fetch);
//
//     try {
//       await searchPosts(
//         { status: 'ALL', page: 1, pageSize: 20 },
//         { preserveAllStatus: true },
//       );
//
//       expect(fetchMock).toHaveBeenCalledTimes(1);
//       const url = String(fetchMock.mock.calls[0]?.[0]);
//       expect(url).toContain('status=ALL');
//     } finally {
//       vi.unstubAllGlobals();
//     }
//   });
// });
