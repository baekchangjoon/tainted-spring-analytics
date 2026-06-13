package com.tainted.analytics.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** community-service 가 mood.logged 토픽에 발행하는 이벤트. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoodLoggedEvent(
        String eventId,
        String userId,
        String source,
        String mood,
        int score,
        Instant occurredAt) {}
