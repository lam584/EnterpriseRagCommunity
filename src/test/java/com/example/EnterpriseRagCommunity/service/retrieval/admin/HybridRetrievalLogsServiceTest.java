package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalEventLogDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalHitLogDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRetrievalLogsServiceTest {

    @Test
    void listEvents_shouldClampPageSizeAndTrimFields() {
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository hitsRepository = mock(RetrievalHitsRepository.class);
        HybridRetrievalLogsService service = new HybridRetrievalLogsService(eventsRepository, hitsRepository);

        RetrievalEventsEntity e = new RetrievalEventsEntity();
        e.setId(1L);
        e.setUserId(9L);
        e.setQueryText("   ");
        e.setRerankModel("  m1 ");
        e.setCreatedAt(LocalDateTime.now());
        when(eventsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));

        Page<RetrievalEventLogDTO> out = service.listEvents(null, null, -9, 999);
        assertThat(out.getContent()).hasSize(1);
        RetrievalEventLogDTO dto = out.getContent().get(0);
        assertThat(dto.getQueryText()).isNull();
        assertThat(dto.getRerankModel()).isEqualTo("m1");
    }

    @Test
    void listHits_shouldMapAndSortNullRanksToTail() {
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository hitsRepository = mock(RetrievalHitsRepository.class);
        HybridRetrievalLogsService service = new HybridRetrievalLogsService(eventsRepository, hitsRepository);

        RetrievalHitsEntity h1 = new RetrievalHitsEntity();
        h1.setId(10L);
        h1.setEventId(7L);
        h1.setRank(null);
        h1.setHitType(RetrievalHitType.POST);
        h1.setScore(0.9);
        RetrievalHitsEntity h2 = new RetrievalHitsEntity();
        h2.setId(11L);
        h2.setEventId(7L);
        h2.setRank(2);
        h2.setHitType(RetrievalHitType.VEC);
        h2.setScore(0.3);
        when(hitsRepository.findByEventId(7L)).thenReturn(List.of(h1, h2));

        List<RetrievalHitLogDTO> out = service.listHits(7L);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getRank()).isEqualTo(2);
        assertThat(out.get(1).getRank()).isNull();
        assertThat(out.get(0).getHitType()).isEqualTo(RetrievalHitType.VEC);
    }

    @Test
    void listEvents_shouldCoverExplicitRangeAndTrimToNull() {
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository hitsRepository = mock(RetrievalHitsRepository.class);
        HybridRetrievalLogsService service = new HybridRetrievalLogsService(eventsRepository, hitsRepository);

        RetrievalEventsEntity e = new RetrievalEventsEntity();
        e.setId(2L);
        e.setQueryText(" q2 ");
        e.setRerankModel("   ");
        when(eventsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));

        Page<RetrievalEventLogDTO> out = service.listEvents(LocalDateTime.now().minusHours(1), LocalDateTime.now(), 1, 1);
        assertThat(out.getContent()).hasSize(1);
        assertThat(out.getContent().get(0).getQueryText()).isEqualTo("q2");
        assertThat(out.getContent().get(0).getRerankModel()).isNull();
    }

    @Test
    void listHits_shouldCoverComparatorWhenBothRanksNull() {
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository hitsRepository = mock(RetrievalHitsRepository.class);
        HybridRetrievalLogsService service = new HybridRetrievalLogsService(eventsRepository, hitsRepository);

        RetrievalHitsEntity h1 = new RetrievalHitsEntity();
        h1.setId(1L);
        h1.setEventId(9L);
        h1.setRank(null);
        RetrievalHitsEntity h2 = new RetrievalHitsEntity();
        h2.setId(2L);
        h2.setEventId(9L);
        h2.setRank(null);
        when(hitsRepository.findByEventId(9L)).thenReturn(List.of(h1, h2));

        List<RetrievalHitLogDTO> out = service.listHits(9L);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getRank()).isNull();
        assertThat(out.get(1).getRank()).isNull();
    }

    @Test
    void trimOrNull_shouldCoverBranches() throws Exception {
        Method m = HybridRetrievalLogsService.class.getDeclaredMethod("trimOrNull", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, (Object) null)).isNull();
        assertThat(m.invoke(null, "   ")).isNull();
        assertThat(m.invoke(null, " x ")).isEqualTo("x");
    }

    @Test
    void listHits_shouldCoverComparatorWhenAllRanksPresent() {
        RetrievalEventsRepository eventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository hitsRepository = mock(RetrievalHitsRepository.class);
        HybridRetrievalLogsService service = new HybridRetrievalLogsService(eventsRepository, hitsRepository);

        RetrievalHitsEntity a = new RetrievalHitsEntity();
        a.setEventId(10L);
        a.setRank(3);
        RetrievalHitsEntity b = new RetrievalHitsEntity();
        b.setEventId(10L);
        b.setRank(1);
        RetrievalHitsEntity c = new RetrievalHitsEntity();
        c.setEventId(10L);
        c.setRank(2);
        when(hitsRepository.findByEventId(10L)).thenReturn(List.of(a, b, c));

        List<RetrievalHitLogDTO> out = service.listHits(10L);
        assertThat(out).hasSize(3);
        assertThat(out.get(0).getRank()).isEqualTo(1);
        assertThat(out.get(1).getRank()).isEqualTo(2);
        assertThat(out.get(2).getRank()).isEqualTo(3);
    }
}
