package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaCompressContextResultDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QaHistoryServicePrivateHelpersBranchTest {

    @Test
    void summarizeSessionForAudit_should_handle_null() throws Exception {
        Method m = QaHistoryService.class.getDeclaredMethod("summarizeSessionForAudit", com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) m.invoke(null, new Object[]{null});
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void summarizeCompressResultForAudit_should_handle_null_and_null_summary() throws Exception {
        Method m = QaHistoryService.class.getDeclaredMethod("summarizeCompressResultForAudit", QaCompressContextResultDTO.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> out0 = (Map<String, Object>) m.invoke(null, new Object[]{null});
        assertNotNull(out0);
        assertTrue(out0.isEmpty());

        QaCompressContextResultDTO dto = new QaCompressContextResultDTO();
        dto.setSessionId(10L);
        dto.setKeptLast(0);
        dto.setSummaryMessageId(null);
        dto.setCompressedDeletedCount(0);
        dto.setSummary(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> out1 = (Map<String, Object>) m.invoke(null, dto);
        assertEquals(0, out1.get("summaryLen"));
    }

    @Test
    void buildSummaryFromEntities_should_cover_branches() throws Exception {
        Method m = QaHistoryService.class.getDeclaredMethod("buildSummaryFromEntities", List.class, int.class, int.class);
        m.setAccessible(true);

        assertNull(m.invoke(null, null, 10, 50));
        assertNull(m.invoke(null, List.of(), 10, 50));

        QaMessagesEntity sys = new QaMessagesEntity();
        sys.setRole(MessageRole.SYSTEM);
        sys.setContent("s");
        sys.setCreatedAt(LocalDateTime.now());

        QaMessagesEntity blank = new QaMessagesEntity();
        blank.setRole(MessageRole.USER);
        blank.setContent("   ");
        blank.setCreatedAt(LocalDateTime.now());

        QaMessagesEntity nullText = new QaMessagesEntity();
        nullText.setRole(MessageRole.USER);
        nullText.setContent(null);
        nullText.setCreatedAt(LocalDateTime.now());

        QaMessagesEntity assistantLong = new QaMessagesEntity();
        assistantLong.setRole(MessageRole.ASSISTANT);
        assistantLong.setContent("0123456789");
        assistantLong.setCreatedAt(LocalDateTime.now());

        QaMessagesEntity roleNull = new QaMessagesEntity();
        roleNull.setRole(null);
        roleNull.setContent("x");
        roleNull.setCreatedAt(LocalDateTime.now());

        String s = (String) m.invoke(null, List.of(sys, blank, nullText, assistantLong, roleNull), 5, 50);
        assertNotNull(s);
        assertTrue(s.length() <= 50);
    }
}

