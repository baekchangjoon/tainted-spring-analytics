package com.tainted.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MoodPointTest {

    @Test
    void allArgsConstructor_exposesEveryField() {
        Instant when = Instant.parse("2026-05-05T12:00:00Z");
        MoodPoint p = new MoodPoint("id-1", "user-1", "diary", "평온", 6, when);

        assertThat(p.getId()).isEqualTo("id-1");
        assertThat(p.getUserId()).isEqualTo("user-1");
        assertThat(p.getSource()).isEqualTo("diary");
        assertThat(p.getLabel()).isEqualTo("평온");
        assertThat(p.getScore()).isEqualTo(6);
        assertThat(p.getOccurredAt()).isEqualTo(when);
    }
}
