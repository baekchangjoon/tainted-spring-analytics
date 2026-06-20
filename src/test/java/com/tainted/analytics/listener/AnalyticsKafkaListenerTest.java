package com.tainted.analytics.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tainted.analytics.event.DiaryCreatedEvent;
import com.tainted.analytics.event.MoodLoggedEvent;
import com.tainted.analytics.event.PostCreatedEvent;
import com.tainted.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 리스너 메서드를 페이로드 문자열로 직접 호출하여 역직렬화 + 위임 + 예외 격리를 검증한다.
 * (Kafka 브로커 없이 순수 단위 테스트)
 */
class AnalyticsKafkaListenerTest {

    private AnalyticsService service;
    private AnalyticsKafkaListener listener;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = mock(AnalyticsService.class);
        listener = new AnalyticsKafkaListener(service, mapper);
    }

    // ── 정상 페이로드: 역직렬화 후 서비스에 위임 ──────────────────────

    @Test
    void onDiaryCreated_deserializesAndDelegates() {
        String payload = """
                {"eventId":"evt-d","userId":"u1","diaryId":"d1",
                 "primaryEmotion":"불안","energyScore":3,"occurredAt":"2026-01-01T00:00:00Z"}
                """;

        listener.onDiaryCreated(payload);

        ArgumentCaptor<DiaryCreatedEvent> captor = ArgumentCaptor.forClass(DiaryCreatedEvent.class);
        verify(service).processDiaryCreated(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("evt-d");
        assertThat(captor.getValue().primaryEmotion()).isEqualTo("불안");
        assertThat(captor.getValue().energyScore()).isEqualTo(3);
    }

    @Test
    void onMoodLogged_deserializesAndDelegates() {
        String payload = """
                {"eventId":"evt-m","userId":"u1","source":"community",
                 "mood":"기쁨","score":8,"occurredAt":"2026-01-02T00:00:00Z"}
                """;

        listener.onMoodLogged(payload);

        ArgumentCaptor<MoodLoggedEvent> captor = ArgumentCaptor.forClass(MoodLoggedEvent.class);
        verify(service).processMoodLogged(captor.capture());
        assertThat(captor.getValue().mood()).isEqualTo("기쁨");
        assertThat(captor.getValue().score()).isEqualTo(8);
    }

    @Test
    void onPostCreated_deserializesAndDelegates() {
        String payload = """
                {"eventId":"evt-p","postId":"post-1","userId":"u1",
                 "category":"daily","moodEmoji":"😊","occurredAt":"2026-01-03T00:00:00Z"}
                """;

        listener.onPostCreated(payload);

        ArgumentCaptor<PostCreatedEvent> captor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(service).processPostCreated(captor.capture());
        assertThat(captor.getValue().moodEmoji()).isEqualTo("😊");
        assertThat(captor.getValue().postId()).isEqualTo("post-1");
    }

    // ── 알 수 없는 필드는 무시(@JsonIgnoreProperties) ────────────────

    @Test
    void onDiaryCreated_ignoresUnknownFields() {
        String payload = """
                {"eventId":"evt-x","userId":"u1","diaryId":"d1","primaryEmotion":"분노",
                 "energyScore":7,"occurredAt":"2026-01-01T00:00:00Z","extraneous":"ignored"}
                """;

        listener.onDiaryCreated(payload);

        verify(service).processDiaryCreated(org.mockito.ArgumentMatchers.any());
    }

    // ── 잘못된 페이로드: 예외를 잡아 삼키고 서비스는 호출하지 않는다 ──

    @Test
    void onDiaryCreated_malformedJson_isSwallowed_noDelegation() {
        listener.onDiaryCreated("not-json-at-all{{{");
        verifyNoInteractions(service);
    }

    @Test
    void onMoodLogged_malformedJson_isSwallowed_noDelegation() {
        listener.onMoodLogged("");
        verify(service, never()).processMoodLogged(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onPostCreated_malformedJson_isSwallowed_noDelegation() {
        listener.onPostCreated("{\"eventId\": ");  // truncated JSON
        verifyNoInteractions(service);
    }
}
