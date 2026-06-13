package com.tainted.analytics.web.dto;

import java.util.List;

public record UserMoodResponse(
        String userId,
        List<MoodPointResponse> points,
        double averageScore,
        long count) {}
