package com.example.EnterpriseRagCommunity.service.retrieval;

public final class RagChunkingSupport {

    private RagChunkingSupport() {
    }

    public static ChunkingParams resolve(Integer chunkMaxChars, Integer chunkOverlapChars) {
        int maxChars = chunkMaxChars == null || chunkMaxChars < 200 ? 1200 : Math.min(8000, chunkMaxChars);
        int overlap = chunkOverlapChars == null || chunkOverlapChars < 0 ? 120 : Math.min(maxChars - 1, chunkOverlapChars);
        return new ChunkingParams(maxChars, overlap);
    }

    public record ChunkingParams(int maxChars, int overlap) {
    }
}
