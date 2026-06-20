package com.tainted.analytics.service;

import com.tainted.analytics.domain.MoodPoint;
import com.tainted.analytics.domain.MoodPointRepository;
import com.tainted.analytics.event.DiaryCreatedEvent;
import com.tainted.analytics.event.MoodLoggedEvent;
import com.tainted.analytics.event.PostCreatedEvent;
import com.tainted.analytics.web.dto.GlobalAggregateResponse;
import com.tainted.analytics.web.dto.UserMoodResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    MoodPointRepository repository;

    @InjectMocks
    AnalyticsService service;

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    // ── 이벤트 처리: 신규 이벤트는 저장된다 ──────────────────────────

    @Test
    void processDiaryCreated_savesNewPoint() {
        DiaryCreatedEvent e = new DiaryCreatedEvent("evt-d1", "u1", "d1", "불안", 3, t0);
        when(repository.existsById("evt-d1")).thenReturn(false);

        service.processDiaryCreated(e);

        ArgumentCaptor<MoodPoint> captor = ArgumentCaptor.forClass(MoodPoint.class);
        verify(repository).save(captor.capture());
        MoodPoint saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("evt-d1");
        assertThat(saved.getSource()).isEqualTo("diary");
        assertThat(saved.getLabel()).isEqualTo("불안");
        assertThat(saved.getScore()).isEqualTo(3);
    }

    @Test
    void processMoodLogged_savesNewPoint() {
        MoodLoggedEvent e = new MoodLoggedEvent("evt-m1", "u1", "community", "기쁨", 8, t0);
        when(repository.existsById("evt-m1")).thenReturn(false);

        service.processMoodLogged(e);

        ArgumentCaptor<MoodPoint> captor = ArgumentCaptor.forClass(MoodPoint.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("community");
        assertThat(captor.getValue().getScore()).isEqualTo(8);
    }

    @Test
    void processPostCreated_savesNewPointWithScoreZero() {
        PostCreatedEvent e = new PostCreatedEvent("evt-p1", "post-1", "u1", "daily", "😊", t0);
        when(repository.existsById("evt-p1")).thenReturn(false);

        service.processPostCreated(e);

        ArgumentCaptor<MoodPoint> captor = ArgumentCaptor.forClass(MoodPoint.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("community");
        assertThat(captor.getValue().getScore()).isZero();
        assertThat(captor.getValue().getLabel()).isEqualTo("😊");
    }

    // ── 멱등성: 이미 존재하는 eventId 는 저장하지 않는다 ──────────────

    @Test
    void process_skipsSave_whenIdAlreadyExists() {
        DiaryCreatedEvent e = new DiaryCreatedEvent("evt-dup", "u1", "d1", "슬픔", 2, t0);
        when(repository.existsById("evt-dup")).thenReturn(true);

        service.processDiaryCreated(e);

        verify(repository, never()).save(any());
    }

    // ── 동시 소비: existsById 통과 후 PK 충돌(DataIntegrityViolation)은 삼킨다 ──

    @Test
    void process_swallowsDataIntegrityViolation_onConcurrentInsert() {
        DiaryCreatedEvent e = new DiaryCreatedEvent("evt-race", "u1", "d1", "분노", 7, t0);
        when(repository.existsById("evt-race")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup PK"));

        // 예외가 밖으로 새지 않아야 한다 (멱등성)
        service.processDiaryCreated(e);

        verify(repository, times(1)).save(any());
    }

    // ── 조회: getUserMood ───────────────────────────────────────────

    @Test
    void getUserMood_computesAverageAndCount_andPreservesOrder() {
        MoodPoint p1 = new MoodPoint("e1", "u1", "diary", "불안", 4, t0);
        MoodPoint p2 = new MoodPoint("e2", "u1", "community", "기쁨", 8, t0.plusSeconds(60));
        when(repository.findByUserIdOrderByOccurredAtAsc("u1")).thenReturn(List.of(p1, p2));

        UserMoodResponse resp = service.getUserMood("u1");

        assertThat(resp.userId()).isEqualTo("u1");
        assertThat(resp.count()).isEqualTo(2);
        assertThat(resp.averageScore()).isEqualTo(6.0);
        assertThat(resp.points()).hasSize(2);
        assertThat(resp.points().get(0).id()).isEqualTo("e1");
        assertThat(resp.points().get(0).label()).isEqualTo("불안");
        assertThat(resp.points().get(1).score()).isEqualTo(8);
    }

    @Test
    void getUserMood_returnsZeroAverage_whenNoPoints() {
        when(repository.findByUserIdOrderByOccurredAtAsc("ghost")).thenReturn(List.of());

        UserMoodResponse resp = service.getUserMood("ghost");

        assertThat(resp.count()).isZero();
        assertThat(resp.averageScore()).isEqualTo(0.0);
        assertThat(resp.points()).isEmpty();
    }

    // ── 조회: getGlobal ─────────────────────────────────────────────

    @Test
    void getGlobal_buildsTotalsAndCountBySource() {
        when(repository.countAll()).thenReturn(5L);
        when(repository.countGroupedBySource()).thenReturn(List.of(
                new Object[]{"diary", 3L},
                new Object[]{"community", 2L}
        ));

        GlobalAggregateResponse resp = service.getGlobal();

        assertThat(resp.totalPoints()).isEqualTo(5L);
        assertThat(resp.countBySource()).containsExactly(
                Map.entry("diary", 3L),
                Map.entry("community", 2L));
    }

    @Test
    void getGlobal_emptyWhenNoData() {
        when(repository.countAll()).thenReturn(0L);
        when(repository.countGroupedBySource()).thenReturn(List.of());

        GlobalAggregateResponse resp = service.getGlobal();

        assertThat(resp.totalPoints()).isZero();
        assertThat(resp.countBySource()).isEmpty();
    }
}
