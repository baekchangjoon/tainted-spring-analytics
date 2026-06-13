package com.tainted.analytics.web.dto;

import java.time.Instant;

public record MoodPointResponse(
        String id,
        String source,
        String label,
        int score,
        Instant occurredAt) {}
