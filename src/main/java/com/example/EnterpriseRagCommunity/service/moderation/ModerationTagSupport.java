package com.example.EnterpriseRagCommunity.service.moderation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ModerationTagSupport {

    private ModerationTagSupport() {
    }

    public static List<String> mergeTags(List<String> a, List<String> b) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (a != null) set.addAll(a);
        if (b != null) set.addAll(b);
        return set.isEmpty() ? null : new ArrayList<>(set);
    }
}
