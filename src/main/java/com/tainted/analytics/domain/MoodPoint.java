package com.tainted.analytics.domain;

import com.tainted.analytics.event.DiaryCreatedEvent;
import com.tainted.analytics.event.MoodLoggedEvent;
import com.tainted.analytics.event.PostCreatedEvent;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Kafka 이벤트 하나를 집계한 무드 데이터 포인트.
 * id == eventId 이므로 중복 소비 시 INSERT OR IGNORE 로 idempotency 보장.
 */
@Entity
@Table(name = "mood_point")
public class MoodPoint {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 이벤트 출처 (diary / community). */
    @Column(nullable = false, length = 32)
    private String source;

    /** 감정 레이블 (primaryEmotion, mood, moodEmoji). */
    @Column(nullable = false, length = 64)
    private String label;

    /** 0~10 정수. post.created 는 0 고정. */
    @Column(nullable = false)
    private int score;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected MoodPoint() {}

    public MoodPoint(String id, String userId, String source, String label, int score, Instant occurredAt) {
        this.id = id;
        this.userId = userId;
        this.source = source;
        this.label = label;
        this.score = score;
        this.occurredAt = occurredAt;
    }

    // ── 팩터리 메서드 ──────────────────────────────────────────────

    public static MoodPoint fromDiaryCreated(DiaryCreatedEvent e) {
        return new MoodPoint(e.eventId(), e.userId(), "diary",
                e.primaryEmotion(), e.energyScore(), e.occurredAt());
    }

    public static MoodPoint fromMoodLogged(MoodLoggedEvent e) {
        return new MoodPoint(e.eventId(), e.userId(), e.source(),
                e.mood(), e.score(), e.occurredAt());
    }

    public static MoodPoint fromPostCreated(PostCreatedEvent e) {
        return new MoodPoint(e.eventId(), e.userId(), "community",
                e.moodEmoji(), 0, e.occurredAt());
    }

    // ── 접근자 ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSource() { return source; }
    public String getLabel() { return label; }
    public int getScore() { return score; }
    public Instant getOccurredAt() { return occurredAt; }
}
