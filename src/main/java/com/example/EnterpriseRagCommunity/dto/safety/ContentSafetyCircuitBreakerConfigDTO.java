package com.example.EnterpriseRagCommunity.dto.safety;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ContentSafetyCircuitBreakerConfigDTO {
    private Boolean enabled;
    private String mode;
    private String message;
    private Scope scope;
    private DependencyIsolation dependencyIsolation;
    private AutoTrigger autoTrigger;

    @Data
    public static class Scope {
        private Boolean all;
        private List<Long> userIds;
        private List<Long> postIds;
        private List<String> entrypoints;

        public static Scope defaultAll() {
            Scope s = new Scope();
            s.setAll(true);
            s.setUserIds(new ArrayList<>());
            s.setPostIds(new ArrayList<>());
            s.setEntrypoints(new ArrayList<>());
            return s;
        }
    }

    @Data
    public static class DependencyIsolation {
        private Boolean mysql;
        private Boolean elasticsearch;

        public static DependencyIsolation defaults() {
            DependencyIsolation d = new DependencyIsolation();
            d.setMysql(false);
            d.setElasticsearch(false);
            return d;
        }
    }

    @Data
    public static class AutoTrigger {
        private Boolean enabled;
        private Integer windowSeconds;
        private Integer thresholdCount;
        private Double minConfidence;
        private List<String> verdicts;
        private String triggerMode;
        private Integer coolDownSeconds;
        private Integer autoRecoverSeconds;

        public static AutoTrigger defaults() {
            AutoTrigger a = new AutoTrigger();
            a.setEnabled(false);
            a.setWindowSeconds(60);
            a.setThresholdCount(10);
            a.setMinConfidence(0.90);
            a.setVerdicts(List.of("REJECT", "REVIEW"));
            a.setTriggerMode("S1");
            a.setCoolDownSeconds(300);
            a.setAutoRecoverSeconds(0);
            return a;
        }
    }
}
