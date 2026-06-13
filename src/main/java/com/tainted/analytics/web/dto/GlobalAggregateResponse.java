package com.tainted.analytics.web.dto;

import java.util.Map;

public record GlobalAggregateResponse(
        long totalPoints,
        Map<String, Long> countBySource) {}
