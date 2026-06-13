package com.tainted.analytics.web;

import com.tainted.analytics.service.AnalyticsService;
import com.tainted.analytics.web.dto.GlobalAggregateResponse;
import com.tainted.analytics.web.dto.UserMoodResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 특정 사용자의 MoodPoint 목록 + 단순 집계(averageScore, count)를 반환.
     * occurredAt 오름차순 정렬.
     */
    @GetMapping("/mood/{userId}")
    public UserMoodResponse getUserMood(@PathVariable String userId) {
        return analyticsService.getUserMood(userId);
    }

    /**
     * 전체 집계: totalPoints + source 별 count.
     */
    @GetMapping("/global")
    public GlobalAggregateResponse getGlobal() {
        return analyticsService.getGlobal();
    }
}
