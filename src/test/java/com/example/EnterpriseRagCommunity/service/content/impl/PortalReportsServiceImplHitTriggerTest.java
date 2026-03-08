package com.example.EnterpriseRagCommunity.service.content.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalReportsServiceImplHitTriggerTest {

    @Test
    void hitTrigger_shouldReturnFalseWhenLevelIsNull() {
        boolean hit = invokeHitTrigger(null, 1, 1, 1, 1);
        assertFalse(hit);
    }

    @Test
    void hitTrigger_shouldReturnFalseWhenLevelIsEmpty() {
        boolean hit = invokeHitTrigger(Map.of(), 1, 1, 1, 1);
        assertFalse(hit);
    }

    @Test
    void hitTrigger_shouldTreatInvalidMinsAsMaxValue() {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", 0);
        level.put("unique_reporters_min", -1);
        level.put("velocity_min_per_window", 0);

        boolean hit = invokeHitTrigger(level, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1);

        assertFalse(hit);
    }

    @Test
    void hitTrigger_shouldAllowNumericMinsAsStrings() {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", "1");
        level.put("unique_reporters_min", "2");
        level.put("velocity_min_per_window", "3");

        boolean hit = invokeHitTrigger(level, 1, 1, 1, 0);
        assertTrue(hit);
    }

    @Test
    void hitTrigger_shouldFallbackToDefaultWhenIntParseFails() {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", "abc");

        boolean hit = invokeHitTrigger(level, 999, 999, 999, 0.99);
        assertFalse(hit);
    }

    @Test
    void hitTrigger_shouldHitByTotalReportsMin() {
        Map<String, Object> level = level(
                5,
                100,
                100,
                0.99
        );

        boolean hit = invokeHitTrigger(level, 5, 1, 1, 0.1);

        assertTrue(hit);
    }

    @Test
    void hitTrigger_shouldHitByUniqueReportersMin() {
        Map<String, Object> level = level(
                100,
                3,
                100,
                0.99
        );

        boolean hit = invokeHitTrigger(level, 1, 3, 1, 0.1);

        assertTrue(hit);
    }

    @Test
    void hitTrigger_shouldHitByVelocityMinPerWindow() {
        Map<String, Object> level = level(
                100,
                100,
                4,
                0.99
        );

        boolean hit = invokeHitTrigger(level, 1, 1, 4, 0.1);

        assertTrue(hit);
    }

    @Test
    void hitTrigger_shouldHitByTrustMin() {
        Map<String, Object> level = level(
                100,
                100,
                100,
                0.70
        );

        boolean hit = invokeHitTrigger(level, 1, 1, 1, 0.70);

        assertTrue(hit);
    }

    @Test
    void hitTrigger_shouldReturnFalseWhenAllThresholdsNotMet() {
        Map<String, Object> level = level(
                10,
                10,
                10,
                0.90
        );

        boolean hit = invokeHitTrigger(level, 9, 9, 9, 0.89);

        assertFalse(hit);
    }

    @Test
    void hitTrigger_shouldUseOrSemanticsWhenOnlyOneThresholdMatches() {
        Map<String, Object> level = level(
                100,
                100,
                100,
                0.80
        );

        boolean hit = invokeHitTrigger(level, 1, 1, 1, 0.80);

        assertTrue(hit);
    }

    @Test
    void hitTrigger_shouldNotHitWhenTrustMinIsInvalidString() {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", 100);
        level.put("unique_reporters_min", 100);
        level.put("velocity_min_per_window", 100);
        level.put("trust_min", "not-a-number");

        boolean hit = invokeHitTrigger(level, 1, 1, 1, 0.95);

        assertFalse(hit);
    }

    @Test
    void hitTrigger_shouldNotHitWhenTrustMinIsMissing() {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", 100);
        level.put("unique_reporters_min", 100);
        level.put("velocity_min_per_window", 100);

        boolean hit = invokeHitTrigger(level, 1, 1, 1, 0.95);

        assertFalse(hit);
    }

    private static Map<String, Object> level(int totalMin, int uniqueMin, int velocityMin, double trustMin) {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", totalMin);
        level.put("unique_reporters_min", uniqueMin);
        level.put("velocity_min_per_window", velocityMin);
        level.put("trust_min", trustMin);
        return level;
    }

    private static boolean invokeHitTrigger(Map<String, Object> level,
                                            long totalReports,
                                            long uniqueReporters,
                                            long velocityPerWindow,
                                            double reporterTrustAgg) {
        Boolean hit = ReflectionTestUtils.invokeMethod(
                PortalReportsServiceImpl.class,
                "hitTrigger",
                level,
                totalReports,
                uniqueReporters,
                velocityPerWindow,
                reporterTrustAgg
        );
        return Boolean.TRUE.equals(hit);
    }
}
