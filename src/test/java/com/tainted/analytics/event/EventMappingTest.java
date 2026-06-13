package com.tainted.analytics.event;

import com.tainted.analytics.domain.MoodPoint;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EventMappingTest {

    @Test
    void diaryCreatedMapsToMoodPoint() {
        DiaryCreatedEvent event = new DiaryCreatedEvent(
                "evt-1", "user-1", "diary-1", "불안", 3, Instant.parse("2026-01-01T00:00:00Z"));
        MoodPoint point = MoodPoint.fromDiaryCreated(event);
        assertEquals("evt-1", point.getId());
        assertEquals("user-1", point.getUserId());
        assertEquals("diary", point.getSource());
        assertEquals("불안", point.getLabel());
        assertEquals(3, point.getScore());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), point.getOccurredAt());
    }

    @Test
    void moodLoggedMapsToMoodPoint() {
        MoodLoggedEvent event = new MoodLoggedEvent(
                "evt-2", "user-1", "community", "기쁨", 8, Instant.parse("2026-01-02T00:00:00Z"));
        MoodPoint point = MoodPoint.fromMoodLogged(event);
        assertEquals("evt-2", point.getId());
        assertEquals("user-1", point.getUserId());
        assertEquals("community", point.getSource());
        assertEquals("기쁨", point.getLabel());
        assertEquals(8, point.getScore());
    }

    @Test
    void postCreatedMapsToMoodPointWithScoreZero() {
        PostCreatedEvent event = new PostCreatedEvent(
                "evt-3", "post-1", "user-1", "daily", "😊", Instant.parse("2026-01-03T00:00:00Z"));
        MoodPoint point = MoodPoint.fromPostCreated(event);
        assertEquals("evt-3", point.getId());
        assertEquals("user-1", point.getUserId());
        assertEquals("community", point.getSource());
        assertEquals("😊", point.getLabel());
        assertEquals(0, point.getScore());
    }

    @Test
    void sameDiaryEventIdProducesSamePointId() {
        DiaryCreatedEvent e1 = new DiaryCreatedEvent(
                "evt-dup", "user-1", "diary-1", "슬픔", 2, Instant.parse("2026-01-01T00:00:00Z"));
        DiaryCreatedEvent e2 = new DiaryCreatedEvent(
                "evt-dup", "user-1", "diary-1", "슬픔", 2, Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(MoodPoint.fromDiaryCreated(e1).getId(),
                     MoodPoint.fromDiaryCreated(e2).getId(),
                     "같은 eventId는 같은 MoodPoint id — idempotency 기반");
    }
}
