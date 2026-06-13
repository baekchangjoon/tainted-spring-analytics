package com.tainted.analytics.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** community-service 가 post.created 토픽에 발행하는 이벤트. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostCreatedEvent(
        String eventId,
        String postId,
        String userId,
        String category,
        String moodEmoji,
        Instant occurredAt) {}
