package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.ai.JsonEscapeSupport;

public final class RagSearchJsonSupport {

    private RagSearchJsonSupport() {
    }

    public static String buildPlainKnnSearchBody(int size, int numCandidates, float[] vec) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        RagSearchSupport.appendVector(sb, vec);
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    public static String buildKnnSearchBody(int size,
                                            int numCandidates,
                                            float[] vec,
                                            String queryText,
                                            String fieldsJson) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        RagSearchSupport.appendVector(sb, vec);
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        appendHighlightClause(sb, queryText, fieldsJson);
        sb.append('}');
        return sb.toString();
    }

    private static void appendHighlightClause(StringBuilder sb, String queryText, String fieldsJson) {
        sb.append(",\"highlight\":{");
        sb.append("\"pre_tags\":[\"<em>\"],\"post_tags\":[\"</em>\"],");
        sb.append("\"fields\":{").append(fieldsJson).append('}');
        appendHighlightQuery(sb, queryText, fieldsJson.contains("file_name") ? "[\"file_name^2\",\"content_text\"]" : "[\"content_text\"]");
        sb.append('}');
    }

    private static void appendHighlightQuery(StringBuilder sb, String queryText, String fieldArrayJson) {
        String q = queryText == null ? "" : queryText.trim();
        if (q.isBlank()) return;
        sb.append(",\"highlight_query\":{");
        sb.append("\"simple_query_string\":{");
        sb.append("\"query\":\"").append(JsonEscapeSupport.escapeJson(q)).append("\",");
        sb.append("\"fields\":").append(fieldArrayJson).append(",\"default_operator\":\"or\"}}");
    }
}
