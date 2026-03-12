package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextWindowDetailDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextWindowLogDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextWindowLogsServiceTest {

    @Test
    void listWindows_shouldMapItemsAndTrimQueryText() {
        ContextWindowsRepository windowsRepository = mock(ContextWindowsRepository.class);
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        ContextWindowLogsService service = new ContextWindowLogsService(windowsRepository, eventsRepository);

        ContextWindowsEntity w1 = new ContextWindowsEntity();
        w1.setId(1L);
        w1.setEventId(100L);
        w1.setPolicy(ContextWindowPolicy.TOPK);
        w1.setChunkIds(Map.of("items", List.of(1, 2)));
        w1.setCreatedAt(LocalDateTime.now());
        ContextWindowsEntity w2 = new ContextWindowsEntity();
        w2.setId(2L);
        w2.setEventId(101L);
        w2.setPolicy(ContextWindowPolicy.HYBRID);
        w2.setChunkIds(Map.of("ids", List.of(9)));
        w2.setCreatedAt(LocalDateTime.now());
        when(windowsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(w1, w2)));

        RetrievalEventsEntity ev1 = new RetrievalEventsEntity();
        ev1.setId(100L);
        ev1.setQueryText("  q1  ");
        when(eventsRepository.findAllById(anyIterable())).thenReturn(Arrays.asList(ev1, null));

        Page<ContextWindowLogDTO> out = service.listWindows(null, null, -1, 999);
        assertThat(out.getContent()).hasSize(2);
        assertThat(out.getContent().get(0).getItems()).isEqualTo(2);
        assertThat(out.getContent().get(0).getQueryText()).isEqualTo("q1");
        assertThat(out.getContent().get(1).getItems()).isEqualTo(1);
        assertThat(out.getContent().get(1).getQueryText()).isNull();
    }

    @Test
    void listWindows_shouldHandleNoEventIdBranch() {
        ContextWindowsRepository windowsRepository = mock(ContextWindowsRepository.class);
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        ContextWindowLogsService service = new ContextWindowLogsService(windowsRepository, eventsRepository);

        ContextWindowsEntity w = new ContextWindowsEntity();
        w.setId(9L);
        w.setEventId(null);
        w.setPolicy(ContextWindowPolicy.SLIDING);
        w.setChunkIds(Map.of("x", "y"));
        when(windowsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(w)));

        Page<ContextWindowLogDTO> out = service.listWindows(LocalDateTime.now().minusHours(1), LocalDateTime.now(), 0, 10);
        assertThat(out.getContent()).hasSize(1);
        assertThat(out.getContent().get(0).getItems()).isEqualTo(0);
    }

    @Test
    void getWindow_shouldThrowWhenNotFoundAndMapWhenFound() {
        ContextWindowsRepository windowsRepository = mock(ContextWindowsRepository.class);
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        ContextWindowLogsService service = new ContextWindowLogsService(windowsRepository, eventsRepository);

        when(windowsRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getWindow(99L)).isInstanceOf(IllegalArgumentException.class);

        ContextWindowsEntity w = new ContextWindowsEntity();
        w.setId(7L);
        w.setEventId(70L);
        w.setPolicy(ContextWindowPolicy.IMPORTANCE);
        w.setChunkIds(Map.of("items", List.of("a")));
        when(windowsRepository.findById(7L)).thenReturn(Optional.of(w));
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(70L);
        ev.setQueryText("  q2 ");
        when(eventsRepository.findById(70L)).thenReturn(Optional.of(ev));

        ContextWindowDetailDTO dto = service.getWindow(7L);
        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getQueryText()).isEqualTo("q2");
        assertThat(dto.getChunkIds()).containsKey("items");
    }

    @Test
    void getWindow_shouldCoverNullEventAndNonMapChunkIds() {
        ContextWindowsRepository windowsRepository = mock(ContextWindowsRepository.class);
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        ContextWindowLogsService service = new ContextWindowLogsService(windowsRepository, eventsRepository);

        ContextWindowsEntity w = new ContextWindowsEntity();
        w.setId(71L);
        w.setEventId(null);
        w.setPolicy(ContextWindowPolicy.TOPK);
        w.setChunkIds(null);
        when(windowsRepository.findById(71L)).thenReturn(Optional.of(w));

        ContextWindowDetailDTO dto = service.getWindow(71L);
        assertThat(dto.getId()).isEqualTo(71L);
        assertThat(dto.getQueryText()).isNull();
        assertThat(dto.getChunkIds()).isNull();
    }

    @Test
    void countItems_shouldCoverAllBranches() throws Exception {
        Method m = ContextWindowLogsService.class.getDeclaredMethod("countItems", Object.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, "x")).isEqualTo(0);
        assertThat(m.invoke(null, Map.of("items", "x"))).isEqualTo(0);
        assertThat(m.invoke(null, Map.of("items", List.of(1, 2, 3)))).isEqualTo(3);
        assertThat(m.invoke(null, Map.of("ids", "x"))).isEqualTo(0);
        assertThat(m.invoke(null, Map.of("ids", List.of(1, 2)))).isEqualTo(2);
    }

    @Test
    void listWindows_shouldSkipEventRowsWithNullId() {
        ContextWindowsRepository windowsRepository = mock(ContextWindowsRepository.class);
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        ContextWindowLogsService service = new ContextWindowLogsService(windowsRepository, eventsRepository);

        ContextWindowsEntity w = new ContextWindowsEntity();
        w.setId(3L);
        w.setEventId(103L);
        w.setPolicy(ContextWindowPolicy.TOPK);
        w.setChunkIds(Map.of("items", List.of(1)));
        when(windowsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(w)));

        RetrievalEventsEntity bad = new RetrievalEventsEntity();
        bad.setId(null);
        bad.setQueryText("x");
        when(eventsRepository.findAllById(anyIterable())).thenReturn(List.of(bad));

        Page<ContextWindowLogDTO> out = service.listWindows(null, null, 0, 10);
        assertThat(out.getContent()).hasSize(1);
        assertThat(out.getContent().get(0).getQueryText()).isNull();
    }
}
