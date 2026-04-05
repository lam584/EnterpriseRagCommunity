package com.example.EnterpriseRagCommunity.service.retrieval;

final class ExceptionSummaryUtils {

    private ExceptionSummaryUtils() {
    }

    static String summarizeException(Throwable ex) {
        if (ex == null) return null;
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        String out = (msg == null || msg.isBlank()) ? type : (type + ": " + msg);
        out = out.replaceAll("\\s+", " ").trim();
        if (out.length() > 800) out = out.substring(0, 800) + "...";
        return out;
    }
}
