package com.tainted.analytics.web;

import com.tainted.analytics.error.GlobalExceptionHandler;
import com.tainted.analytics.service.AnalyticsService;
import com.tainted.analytics.web.dto.GlobalAggregateResponse;
import com.tainted.analytics.web.dto.MoodPointResponse;
import com.tainted.analytics.web.dto.UserMoodResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class AnalyticsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AnalyticsService analyticsService;

    @Test
    void getUserMood_returnsPointsAndAggregate() throws Exception {
        UserMoodResponse resp = new UserMoodResponse("u1",
                List.of(new MoodPointResponse("e1", "diary", "불안", 3,
                        Instant.parse("2026-01-01T00:00:00Z"))),
                3.0, 1);
        when(analyticsService.getUserMood("u1")).thenReturn(resp);

        mockMvc.perform(get("/internal/analytics/mood/u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.averageScore").value(3.0))
                .andExpect(jsonPath("$.points[0].label").value("불안"))
                .andExpect(jsonPath("$.points[0].score").value(3));
    }

    @Test
    void getUserMood_emptyUser_returnsEmptyList() throws Exception {
        when(analyticsService.getUserMood("ghost"))
                .thenReturn(new UserMoodResponse("ghost", List.of(), 0.0, 0));

        mockMvc.perform(get("/internal/analytics/mood/ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.points").isEmpty());
    }

    @Test
    void getGlobal_returnsTotalsAndCountBySource() throws Exception {
        when(analyticsService.getGlobal())
                .thenReturn(new GlobalAggregateResponse(5L, Map.of("diary", 3L, "community", 2L)));

        mockMvc.perform(get("/internal/analytics/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(5))
                .andExpect(jsonPath("$.countBySource.diary").value(3))
                .andExpect(jsonPath("$.countBySource.community").value(2));
    }

    /**
     * 서비스에서 예기치 못한 예외가 나면 GlobalExceptionHandler.handleGeneral 이
     * 500 ProblemDetail 을 반환한다 (실제 동작 검증).
     */
    @Test
    void serviceThrows_returns500ProblemDetail() throws Exception {
        when(analyticsService.getGlobal()).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/internal/analytics/global"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("boom"));
    }
}
