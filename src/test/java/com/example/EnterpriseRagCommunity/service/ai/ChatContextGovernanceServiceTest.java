package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.AiChatContextEventsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.AiChatContextEventsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatContextGovernanceServiceTest {

    @Test
    void apply_disabled_returnsShortCircuitResult() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(false);
        cfg.setLogEnabled(true);
        cfg.setLogSampleRate(1.0);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(ChatMessage.system("S"), ChatMessage.user("Q"));

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertNotNull(out);
        assertEquals("disabled", out.getReason());
        assertFalse(out.isChanged());
        assertEquals(out.getBeforeTokens(), out.getAfterTokens());
        assertEquals(out.getBeforeChars(), out.getAfterChars());
        assertEquals(2, out.getMessages().size());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_enabled_noCompressionNoClip_returnsNoChangeReason() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(ChatMessage.system("S"), ChatMessage.assistant("aaaaaaaa"), ChatMessage.user("Q"));

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("nochange", out.getReason());
        assertFalse(out.isChanged());
        assertEquals(input.size(), out.getMessages().size());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_compressOnly_setsCompressReason() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(true);
        cfg.setCompressionTriggerTokens(1);
        cfg.setCompressionKeepLastMessages(1);
        cfg.setCompressionPerMessageSnippetChars(50);
        cfg.setCompressionMaxChars(1000);
        cfg.setKeepLastMessages(0);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("compress", out.getReason());
        assertTrue(out.isChanged());
        assertEquals(4, input.size());
        assertEquals(4, out.getMessages().size());
        assertEquals("system", out.getMessages().get(0).role());
        assertEquals("system", out.getMessages().get(1).role());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_compressionEnabled_butBelowTrigger_doesNotCompress() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(true);
        cfg.setCompressionTriggerTokens(10_000);
        cfg.setCompressionKeepLastMessages(1);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(ChatMessage.system("S"), ChatMessage.assistant("aaaaaaaa"), ChatMessage.user("Q"));

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("nochange", out.getReason());
        assertFalse(out.isChanged());
        assertEquals(input.size(), out.getMessages().size());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_compressionTriggered_butKeepLastCoversAll_historyNotCompressed() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(true);
        cfg.setCompressionTriggerTokens(1);
        cfg.setCompressionKeepLastMessages(99);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(ChatMessage.system("S"), ChatMessage.assistant("aaaaaaaa"), ChatMessage.user("Q"));

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("nochange", out.getReason());
        assertFalse(out.isChanged());
        assertEquals(input.size(), out.getMessages().size());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_clipOnly_setsClipReason() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setKeepLastMessages(1);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("clip", out.getReason());
        assertTrue(out.isChanged());
        assertEquals(3, out.getMessages().size());
        assertEquals("system", out.getMessages().get(0).role());
        assertEquals("assistant", out.getMessages().get(1).role());
        assertEquals("user", out.getMessages().get(2).role());
        assertNotNull(out.getDetail());
        Object meta = out.getDetail().get("meta");
        assertTrue(meta instanceof Map<?, ?>);
        Object dropped = ((Map<?, ?>) meta).get("droppedByKeepLast");
        assertTrue(dropped instanceof List<?>);
        assertEquals(1, ((List<?>) dropped).size());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_perMessageMaxTokens_truncatesNonSystemNonTail_messages() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setPerMessageMaxTokens(1);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.user("tail_should_not_truncate")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("clip", out.getReason());
        assertEquals("aaaa", out.getMessages().get(1).content());
        assertEquals("tail_should_not_truncate", out.getMessages().get(2).content());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_perMessageMaxTokens_truncatesTextParts_butKeepsImageParts() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setPerMessageMaxTokens(1);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<Map<String, Object>> parts = List.of(
                Map.of("type", "text", "text", "aaaaaaaa"),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.invalid/x.png"))
        );

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                new ChatMessage("assistant", parts),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("clip", out.getReason());
        Object c = out.getMessages().get(1).content();
        assertTrue(c instanceof List<?>);
        List<?> outParts = (List<?>) c;
        assertEquals(2, outParts.size());
        assertTrue(outParts.get(0) instanceof Map<?, ?>);
        assertEquals("text", String.valueOf(((Map<?, ?>) outParts.get(0)).get("type")));
        assertEquals("aaaa", String.valueOf(((Map<?, ?>) outParts.get(0)).get("text")));
        assertTrue(outParts.get(1) instanceof Map<?, ?>);
        assertEquals("image_url", String.valueOf(((Map<?, ?>) outParts.get(1)).get("type")));
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_budgetTokens_dropsOldMessages_untilUnderBudget() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setMaxPromptTokens(3);
        cfg.setReserveAnswerTokens(0);
        cfg.setAllowDropRagContext(true);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("clip", out.getReason());
        assertTrue(out.getMessages().size() < input.size());
        assertEquals("user", out.getMessages().get(out.getMessages().size() - 1).role());
        Object meta = out.getDetail().get("meta");
        assertTrue(meta instanceof Map<?, ?>);
        assertTrue(((Map<?, ?>) meta).get("droppedByBudgetTokens") instanceof List<?>);
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_budgetTokens_cannotDropSystem_whenDisallowed_stopsLoop() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setMaxPromptTokens(1);
        cfg.setReserveAnswerTokens(0);
        cfg.setAllowDropRagContext(false);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(ChatMessage.system("aaaaaaaa"), ChatMessage.user("Q"));

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("nochange", out.getReason());
        assertFalse(out.isChanged());
        assertEquals(2, out.getMessages().size());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_maxPromptChars_truncatesFromEnd_untilWithinLimit() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setMaxPromptChars(5);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("abcdefghij"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("clip", out.getReason());
        assertEquals("abcd", out.getMessages().get(1).content());
        Object meta = out.getDetail().get("meta");
        assertTrue(meta instanceof Map<?, ?>);
        assertTrue(((Map<?, ?>) meta).get("truncatedToMaxChars") instanceof List<?>);
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void apply_changed_andLogEnabled_withNullSampleRate_savesEvent() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setKeepLastMessages(1);
        cfg.setLogEnabled(true);
        cfg.setLogSampleRate(null);
        when(configService.getConfigOrDefault()).thenReturn(cfg);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(11L, 22L, 33L, input);

        assertTrue(out.isChanged());
        assertEquals("clip", out.getReason());

        ArgumentCaptor<AiChatContextEventsEntity> cap = ArgumentCaptor.forClass(AiChatContextEventsEntity.class);
        verify(repo).save(cap.capture());
        AiChatContextEventsEntity e = cap.getValue();
        assertEquals(11L, e.getUserId());
        assertEquals(22L, e.getSessionId());
        assertEquals(33L, e.getQuestionMessageId());
        assertEquals("CHAT", e.getKind());
        assertEquals("clip", e.getReason());
        assertNotNull(e.getBeforeTokens());
        assertNotNull(e.getAfterTokens());
        assertNotNull(e.getBeforeChars());
        assertNotNull(e.getAfterChars());
        assertNotNull(e.getLatencyMs());
        assertNotNull(e.getDetailJson());
        assertTrue(e.getDetailJson().containsKey("afterTokens"));
        assertNotNull(e.getCreatedAt());
    }

    @Test
    void apply_changed_andLogEnabled_withSampleRateGreaterThanOne_clampsAndSaves() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setKeepLastMessages(1);
        cfg.setLogEnabled(true);
        cfg.setLogSampleRate(2.0);
        when(configService.getConfigOrDefault()).thenReturn(cfg);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(11L, 22L, 33L, input);

        assertEquals("clip", out.getReason());
        verify(repo).save(any());
    }

    @Test
    void apply_changed_andLogEnabled_withZeroSampleRate_doesNotSave() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setKeepLastMessages(1);
        cfg.setLogEnabled(true);
        cfg.setLogSampleRate(0.0);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(11L, 22L, 33L, input);

        assertEquals("clip", out.getReason());
        verify(repo, never()).save(any());
    }

    @Test
    void apply_changed_butLogDisabled_doesNotSave() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(false);
        cfg.setKeepLastMessages(1);
        cfg.setLogEnabled(null);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(11L, 22L, 33L, input);

        assertEquals("clip", out.getReason());
        verify(repo, never()).save(any());
    }

    @Test
    void apply_compressAndClip_setsCompressClipReason() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repo = mock(AiChatContextEventsRepository.class);
        ChatContextGovernanceService svc = new ChatContextGovernanceService(configService, repo);

        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setEnabled(true);
        cfg.setCompressionEnabled(true);
        cfg.setCompressionTriggerTokens(1);
        cfg.setCompressionKeepLastMessages(1);
        cfg.setCompressionPerMessageSnippetChars(50);
        cfg.setCompressionMaxChars(1000);
        cfg.setKeepLastMessages(1);
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        List<ChatMessage> input = List.of(
                ChatMessage.system("S"),
                ChatMessage.assistant("aaaaaaaa"),
                ChatMessage.assistant("bbbbbbbb"),
                ChatMessage.user("Q")
        );

        ChatContextGovernanceService.ApplyResult out = svc.apply(1L, 2L, 3L, input);

        assertEquals("compress+clip", out.getReason());
        assertTrue(out.isChanged());
        assertEquals(3, out.getMessages().size());
        assertEquals("system", out.getMessages().get(0).role());
        assertEquals("assistant", out.getMessages().get(1).role());
        assertEquals("user", out.getMessages().get(2).role());
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void buildSummary_skipsSystemAndBlank_andTruncates() throws Exception {
        List<ChatMessage> msgs = List.of(
                ChatMessage.system("should_skip"),
                new ChatMessage("user", " \n "),
                ChatMessage.user("line1\nline2\rline3"),
                ChatMessage.assistant("0123456789abcdef"),
                new ChatMessage("tool", "xyz")
        );

        String s = invokeBuildSummary(msgs, 5, 60);

        assertNotNull(s);
        assertTrue(s.contains("以下为较早历史消息摘要"));
        assertTrue(s.contains("- U: "));
        assertTrue(s.contains("- A: "));
        assertTrue(s.contains("- A: ") || s.contains("- tool: "));
        assertFalse(s.contains("should_skip"));
        assertTrue(s.length() <= 60);
        assertNotEquals("", s.trim());
    }

    @Test
    void buildSummary_withNullOrEmpty_returnsNull() throws Exception {
        assertNull(invokeBuildSummary(null, 5, 60));
        assertNull(invokeBuildSummary(List.of(), 5, 60));
    }

    @Test
    void countLeadingSystemMessages_stopsOnNullOrNonSystem() throws Exception {
        int n1 = invokePrivateStatic("countLeadingSystemMessages", new Class[]{List.class}, new Object[]{List.of(
                ChatMessage.system("s1"),
                ChatMessage.system("s2"),
                ChatMessage.user("u")
        )});
        assertEquals(2, n1);

        int n2 = invokePrivateStatic("countLeadingSystemMessages", new Class[]{List.class}, new Object[]{Arrays.asList(
                ChatMessage.system("s1"),
                null,
                ChatMessage.system("s2")
        )});
        assertEquals(1, n2);
    }

    @Test
    void findOldestDroppableIndex_respectsSystemDropPolicyAndTail() throws Exception {
        int idxAllowDropSystem = invokePrivateStatic("findOldestDroppableIndex",
                new Class[]{List.class, int.class, boolean.class, boolean.class},
                new Object[]{List.of(ChatMessage.system("s"), ChatMessage.assistant("a"), ChatMessage.user("u")), 0, true, true});
        assertEquals(0, idxAllowDropSystem);

        int idxDisallowDropSystem = invokePrivateStatic("findOldestDroppableIndex",
                new Class[]{List.class, int.class, boolean.class, boolean.class},
                new Object[]{List.of(ChatMessage.system("s"), ChatMessage.assistant("a"), ChatMessage.user("u")), 0, false, true});
        assertEquals(1, idxDisallowDropSystem);

        int idxNoDroppable = invokePrivateStatic("findOldestDroppableIndex",
                new Class[]{List.class, int.class, boolean.class, boolean.class},
                new Object[]{List.of(ChatMessage.system("s"), ChatMessage.user("u")), 0, false, true});
        assertEquals(-1, idxNoDroppable);
    }

    @Test
    void truncateMessage_handlesStringListAndFallbackBranches() throws Exception {
        ChatMessage nullMsg = invokePrivateStatic("truncateMessage", new Class[]{ChatMessage.class, int.class}, new Object[]{null, 2});
        assertNull(nullMsg);

        ChatMessage unchangedString = invokePrivateStatic("truncateMessage", new Class[]{ChatMessage.class, int.class}, new Object[]{ChatMessage.assistant("aa"), 10});
        assertEquals("aa", unchangedString.content());

        ChatMessage truncatedString = invokePrivateStatic("truncateMessage", new Class[]{ChatMessage.class, int.class}, new Object[]{ChatMessage.assistant("aaaaaaaa"), 1});
        assertEquals("aaaa", truncatedString.content());

        ChatMessage unknownContent = invokePrivateStatic("truncateMessage", new Class[]{ChatMessage.class, int.class}, new Object[]{new ChatMessage("assistant", 12345), 1});
        assertEquals(12345, unknownContent.content());

        ChatMessage emptyParts = invokePrivateStatic("truncateMessage", new Class[]{ChatMessage.class, int.class}, new Object[]{new ChatMessage("assistant", List.of("x")), 1});
        assertEquals(List.of("x"), emptyParts.content());

        List<Map<String, Object>> parts = List.of(
                Map.of("type", "text", "text", "aaaaaaaa"),
                Map.of("type", "tool_result", "text", "bbbb"),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.invalid/img.png"))
        );
        ChatMessage truncatedParts = invokePrivateStatic("truncateMessage", new Class[]{ChatMessage.class, int.class}, new Object[]{new ChatMessage("assistant", parts), 1});
        Object c = truncatedParts.content();
        assertTrue(c instanceof List<?>);
        List<?> out = (List<?>) c;
        assertEquals("aaaa", String.valueOf(((Map<?, ?>) out.get(0)).get("text")));
        assertEquals("tool_result", String.valueOf(((Map<?, ?>) out.get(1)).get("type")));
        assertEquals("image_url", String.valueOf(((Map<?, ?>) out.get(2)).get("type")));
    }

    @Test
    void truncateMessageByCharsFromEnd_handlesNegativeAndListBranches() throws Exception {
        ChatMessage unchanged = invokePrivateStatic("truncateMessageByCharsFromEnd", new Class[]{ChatMessage.class, int.class}, new Object[]{ChatMessage.assistant("abcd"), 10});
        assertEquals("abcd", unchanged.content());

        ChatMessage truncated = invokePrivateStatic("truncateMessageByCharsFromEnd", new Class[]{ChatMessage.class, int.class}, new Object[]{ChatMessage.assistant("abcdef"), -1});
        assertEquals("", truncated.content());

        List<Map<String, Object>> unchangedParts = List.of(
                Map.of("type", "text", "text", "abc"),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.invalid/img.png"))
        );
        ChatMessage unchangedList = invokePrivateStatic("truncateMessageByCharsFromEnd", new Class[]{ChatMessage.class, int.class}, new Object[]{new ChatMessage("assistant", unchangedParts), 5});
        assertEquals(unchangedParts, unchangedList.content());

        List<Map<String, Object>> longParts = List.of(
                Map.of("type", "text", "text", "abcdef"),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.invalid/img.png"))
        );
        ChatMessage truncatedList = invokePrivateStatic("truncateMessageByCharsFromEnd", new Class[]{ChatMessage.class, int.class}, new Object[]{new ChatMessage("assistant", longParts), 3});
        Object content = truncatedList.content();
        assertTrue(content instanceof List<?>);
        assertEquals("abc", String.valueOf(((Map<?, ?>) ((List<?>) content).get(0)).get("text")));
    }

    @Test
    void extractText_andApproxHelpers_coverFallbackBranches() throws Exception {
        String textFromNull = invokePrivateStatic("extractText", new Class[]{ChatMessage.class}, new Object[]{null});
        assertNull(textFromNull);

        String textFromUnknown = invokePrivateStatic("extractText", new Class[]{ChatMessage.class}, new Object[]{new ChatMessage("assistant", 123)});
        assertEquals("123", textFromUnknown);

        String emptyFromListWithoutText = invokePrivateStatic("extractText", new Class[]{ChatMessage.class}, new Object[]{new ChatMessage("assistant", List.of(Map.of("type", "image_url")))});
        assertEquals("", emptyFromListWithoutText);

        int charsNull = invokePrivateStatic("approxCharsOfMessages", new Class[]{List.class}, new Object[]{null});
        assertEquals(0, charsNull);

        int tokensNull = invokePrivateStatic("approxTokensOfMessages", new Class[]{List.class, int.class}, new Object[]{null, 1000});
        assertEquals(0, tokensNull);

        int tokensMixed = invokePrivateStatic("approxTokensOfMessages", new Class[]{List.class, int.class}, new Object[]{List.of(
                ChatMessage.assistant("abcd"),
                new ChatMessage("assistant", List.of(Map.of("type", "image_url"), Map.of("type", "text", "text", "abcd"))),
                new ChatMessage("assistant", 999)
        ), 1000});
        assertTrue(tokensMixed >= 1002);
    }

    @Test
    void truncateByApproxTokens_andApproxTokens_coverEdgeCases() throws Exception {
        int tokensNull = invokePrivateStatic("approxTokens", new Class[]{String.class}, new Object[]{null});
        assertEquals(0, tokensNull);

        int tokensEmpty = invokePrivateStatic("approxTokens", new Class[]{String.class}, new Object[]{""});
        assertEquals(0, tokensEmpty);

        String nullInput = invokePrivateStatic("truncateByApproxTokens", new Class[]{String.class, int.class}, new Object[]{null, 1});
        assertEquals("", nullInput);

        String nonPositive = invokePrivateStatic("truncateByApproxTokens", new Class[]{String.class, int.class}, new Object[]{"abcdef", 0});
        assertEquals("", nonPositive);

        String truncated = invokePrivateStatic("truncateByApproxTokens", new Class[]{String.class, int.class}, new Object[]{"abcdefgh", 1});
        assertEquals("abcd", truncated);
    }

    @Test
    void getOps_getMeta_andSafeLen_coverNullAndExistingBranches() throws Exception {
        List<String> opsFromNull = invokePrivateStatic("getOps", new Class[]{Map.class}, new Object[]{null});
        assertNotNull(opsFromNull);
        assertTrue(opsFromNull.isEmpty());

        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        List<String> ops1 = invokePrivateStatic("getOps", new Class[]{Map.class}, new Object[]{detail});
        ops1.add("x");
        List<String> ops2 = invokePrivateStatic("getOps", new Class[]{Map.class}, new Object[]{detail});
        assertSame(ops1, ops2);

        Map<String, Object> metaFromNull = invokePrivateStatic("getMeta", new Class[]{Map.class}, new Object[]{null});
        assertNotNull(metaFromNull);
        assertTrue(metaFromNull.isEmpty());

        Map<String, Object> meta1 = invokePrivateStatic("getMeta", new Class[]{Map.class}, new Object[]{detail});
        meta1.put("k", "v");
        Map<String, Object> meta2 = invokePrivateStatic("getMeta", new Class[]{Map.class}, new Object[]{detail});
        assertSame(meta1, meta2);

        int nullLen = invokePrivateStatic("safeLen", new Class[]{String.class}, new Object[]{null});
        int nonNullLen = invokePrivateStatic("safeLen", new Class[]{String.class}, new Object[]{"abc"});
        assertEquals(0, nullLen);
        assertEquals(3, nonNullLen);
    }

    @Test
    void dropMeta_withNullAndLongText_coversSnippetBranches() throws Exception {
        Map<String, Object> nullMeta = invokePrivateStatic("dropMeta", new Class[]{ChatMessage.class}, new Object[]{null});
        assertEquals(0, nullMeta.get("chars"));

        String longText = "a".repeat(200);
        Map<String, Object> meta = invokePrivateStatic("dropMeta", new Class[]{ChatMessage.class}, new Object[]{ChatMessage.assistant(longText)});
        assertEquals(200, meta.get("chars"));
        assertTrue(String.valueOf(meta.get("snippet")).length() <= 120);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokePrivateStatic(String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method m = ChatContextGovernanceService.class.getDeclaredMethod(methodName, parameterTypes);
        m.setAccessible(true);
        return (T) m.invoke(null, args);
    }

    private static String invokeBuildSummary(List<ChatMessage> msgs, int snippetChars, int maxChars) throws Exception {
        Method m = ChatContextGovernanceService.class.getDeclaredMethod("buildSummary", List.class, int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, msgs, snippetChars, maxChars);
    }
}
