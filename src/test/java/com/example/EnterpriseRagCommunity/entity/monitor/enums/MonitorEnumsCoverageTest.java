package com.example.EnterpriseRagCommunity.entity.monitor.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorEnumsCoverageTest {

    @Test
    void enumConstants_shouldBeReachableByValuesAndValueOf() {
        assertEnumRoundTrip(AiProvider.values());
        assertEnumRoundTrip(CostScope.values());
        assertEnumRoundTrip(FileAssetExtractionStatus.values());
        assertEnumRoundTrip(FileAssetStatus.values());
        assertEnumRoundTrip(MetricType.values());
        assertEnumRoundTrip(NotificationStatus.values());
        assertEnumRoundTrip(NotificationType.values());
        assertEnumRoundTrip(RagEvalRunStatus.values());
        assertEnumRoundTrip(RagEvalRunType.values());
        assertEnumRoundTrip(StorageProvider.values());
        assertEnumRoundTrip(SystemEventLevel.values());
        assertEnumRoundTrip(Visibility.values());
    }

    @Test
    void fromNullableString_shouldHandleNullBlankValidAndInvalid() {
        assertNull(MonitorEnumParser.fromNullableString(null, "OPENAI"));
        assertNull(MonitorEnumParser.fromNullableString(AiProvider.class, null));
        assertNull(MonitorEnumParser.fromNullableString(AiProvider.class, "   "));
        assertEquals(AiProvider.OPENAI, MonitorEnumParser.fromNullableString(AiProvider.class, "openai"));
        assertEquals(Visibility.PUBLIC, MonitorEnumParser.fromNullableString(Visibility.class, " public "));
        assertNull(MonitorEnumParser.fromNullableString(StorageProvider.class, "not_exists"));
    }

    @Test
    void fromNullableString_shouldSupportAllEnums() {
        assertEquals(AiProvider.AZURE, MonitorEnumParser.fromNullableString(AiProvider.class, "azure"));
        assertEquals(CostScope.MODERATION, MonitorEnumParser.fromNullableString(CostScope.class, "moderation"));
        assertEquals(FileAssetExtractionStatus.READY,
                MonitorEnumParser.fromNullableString(FileAssetExtractionStatus.class, "ready"));
        assertEquals(FileAssetStatus.READY, MonitorEnumParser.fromNullableString(FileAssetStatus.class, "ready"));
        assertEquals(MetricType.COUNTER, MonitorEnumParser.fromNullableString(MetricType.class, "counter"));
        assertEquals(NotificationStatus.UNREAD,
                MonitorEnumParser.fromNullableString(NotificationStatus.class, "unread"));
        assertEquals(NotificationType.WARNING, MonitorEnumParser.fromNullableString(NotificationType.class, "warning"));
        assertEquals(RagEvalRunStatus.FAILED, MonitorEnumParser.fromNullableString(RagEvalRunStatus.class, "failed"));
        assertEquals(RagEvalRunType.OFFLINE, MonitorEnumParser.fromNullableString(RagEvalRunType.class, "offline"));
        assertEquals(StorageProvider.S3, MonitorEnumParser.fromNullableString(StorageProvider.class, "s3"));
        assertEquals(SystemEventLevel.INFO, MonitorEnumParser.fromNullableString(SystemEventLevel.class, "info"));
        assertEquals(Visibility.TEAM, MonitorEnumParser.fromNullableString(Visibility.class, "team"));
    }

    private <E extends Enum<E>> void assertEnumRoundTrip(E[] values) {
        assertNotNull(values);
        assertTrue(values.length > 0);
        Class<?> enumClass = values[0].getDeclaringClass();
        for (E value : values) {
            @SuppressWarnings("unchecked")
            Class<E> typedClass = (Class<E>) enumClass;
            assertEquals(value, Enum.valueOf(typedClass, value.name()));
        }
    }
}
