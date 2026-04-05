package com.example.EnterpriseRagCommunity.service.content;

public final class ContentPagingSupport {

    private ContentPagingSupport() {
    }

    public static SliceWindow sliceWindow(int safePage, int safePageSize, int totalSize) {
        int offset = (safePage - 1) * safePageSize;
        if (offset >= totalSize) {
            return new SliceWindow(offset, offset, false);
        }
        int end = Math.min(totalSize, offset + safePageSize);
        return new SliceWindow(offset, end, end < totalSize);
    }

    public record SliceWindow(int offset, int end, boolean hasMore) {
    }
}
