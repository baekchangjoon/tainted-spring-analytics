package com.tainted.analytics.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** diary-service 가 diary.created 토픽에 발행하는 이벤트. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiaryCreatedEvent(
        String eventId,
        String userId,
        String diaryId,
        String primaryEmotion,
        int energyScore,
        Instant occurredAt) {}
