package com.example.EnterpriseRagCommunity.service.retrieval;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class HybridRerankDocumentSupport {

    private HybridRerankDocumentSupport() {
    }

    public static <T> int collectDocsWithinBudget(List<T> documents,
                                                  List<T> selectedDocs,
                                                  List<String> docTexts,
                                                  Function<T, String> textBuilder,
                                                  Function<String, String> truncator,
                                                  ToIntFunction<String> tokenCounter,
                                                  int budgetLeft,
                                                  int queryTokens) {
        int remaining = budgetLeft;
        if (documents == null) {
            return remaining;
        }
        for (T doc : documents) {
            if (doc == null) continue;
            String text = truncator.apply(textBuilder.apply(doc));
            int tokens = tokenCounter.applyAsInt(text);
            if (tokens <= 0) continue;
            int cost = tokens + Math.max(0, queryTokens);
            if (remaining - cost < 200) break;
            remaining -= cost;
            selectedDocs.add(doc);
            docTexts.add(text);
        }
        return remaining;
    }
}
