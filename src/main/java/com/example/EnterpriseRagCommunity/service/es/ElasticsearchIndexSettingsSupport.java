package com.example.EnterpriseRagCommunity.service.es;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ElasticsearchIndexSettingsSupport {

    private ElasticsearchIndexSettingsSupport() {
    }

    public static Map<String, Object> buildBasicIndexSettings(boolean ikEnabled) {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("number_of_shards", 1);
        index.put("number_of_replicas", 0);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("index", index);

        if (ikEnabled) {
            Map<String, Object> analysis = new LinkedHashMap<>();
            Map<String, Object> analyzer = new LinkedHashMap<>();
            analyzer.put("ik_max_word", Map.of("type", "ik_max_word"));
            analyzer.put("ik_smart", Map.of("type", "ik_smart"));
            analysis.put("analyzer", analyzer);
            settings.put("analysis", analysis);
        }

        return settings;
    }
}
