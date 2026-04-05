package com.example.EnterpriseRagCommunity.service.retrieval;

public final class RagPostSearchJsonSupport {

    private RagPostSearchJsonSupport() {
    }

    public static String buildKnnSearchBody(int size, int numCandidates, Long boardId, float[] vec) {
        return buildKnnSearchBody(size, numCandidates, boardId, vec, false);
    }

    public static String buildKnnSearchBody(int size, int numCandidates, Long boardId, float[] vec, boolean includeHighlight) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"query\":{\"bool\":{\"filter\":[");
        if (boardId != null) {
            sb.append("{\"term\":{\"board_id\":").append(boardId).append("}}");
        }
        sb.append("]}}");
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        RagSearchSupport.appendVector(sb, vec);
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        if (includeHighlight) {
            appendHighlightClause(sb);
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendHighlightClause(StringBuilder sb) {
        sb.append(",\"highlight\":{");
        sb.append("\"pre_tags\":[\"<em>\"],\"post_tags\":[\"</em>\"],");
        sb.append("\"fields\":{");
        sb.append("\"content_text\":{\"number_of_fragments\":1,\"fragment_size\":220},");
        sb.append("\"title\":{\"number_of_fragments\":1,\"fragment_size\":120}");
        sb.append("}}");
    }
}
