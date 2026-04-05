package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class ModerationLabelTaxonomySupport {

    private ModerationLabelTaxonomySupport() {
    }

    static Map<String, Object> buildRiskTagTaxonomy(List<RiskTagItem> items) {
        Map<String, Object> labelTax = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            return labelTax;
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        List<Map<String, Object>> labelMap = new ArrayList<>();
        for (RiskTagItem it : items) {
            if (it == null) {
                continue;
            }
            String name = it.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            allowed.add(name);
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("slug", it.slug());
            mapped.put("name", name);
            labelMap.add(mapped);
        }
        if (allowed.isEmpty() && labelMap.isEmpty()) {
            return labelTax;
        }

        labelTax.put("taxonomy_id", "risk_tags");
        if (!allowed.isEmpty()) {
            labelTax.put("allowed_labels", new ArrayList<>(allowed));
        }
        if (!labelMap.isEmpty()) {
            labelTax.put("label_map", labelMap);
        }
        return labelTax;
    }
}
