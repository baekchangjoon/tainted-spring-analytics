package com.tainted.analytics.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * MoodPointRepository 의 커스텀 쿼리(JPQL)를 실제 PostgreSQL(Testcontainers)로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MoodPointRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("analytics")
            .withUsername("postgres")
            .withPassword("postgrespw");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    MoodPointRepository repository;

    private MoodPoint point(String id, String user, String source, String label, int score, String at) {
        return new MoodPoint(id, user, source, label, score, Instant.parse(at));
    }

    @Test
    void findByUserIdOrderByOccurredAtAsc_returnsChronologicalOrder() {
        repository.save(point("a2", "u1", "diary", "기쁨", 8, "2026-01-02T00:00:00Z"));
        repository.save(point("a1", "u1", "diary", "불안", 3, "2026-01-01T00:00:00Z"));
        repository.save(point("a3", "u1", "community", "분노", 6, "2026-01-03T00:00:00Z"));
        repository.save(point("other", "u2", "diary", "슬픔", 1, "2026-01-01T00:00:00Z"));

        List<MoodPoint> points = repository.findByUserIdOrderByOccurredAtAsc("u1");

        assertThat(points).extracting(MoodPoint::getId).containsExactly("a1", "a2", "a3");
    }

    @Test
    void countByUserId_countsOnlyThatUser() {
        repository.save(point("c1", "u1", "diary", "x", 5, "2026-01-01T00:00:00Z"));
        repository.save(point("c2", "u1", "diary", "y", 5, "2026-01-02T00:00:00Z"));
        repository.save(point("c3", "u2", "diary", "z", 5, "2026-01-03T00:00:00Z"));

        assertThat(repository.countByUserId("u1")).isEqualTo(2L);
        assertThat(repository.countByUserId("nobody")).isZero();
    }

    @Test
    void avgScoreByUserId_returnsAverage_andZeroWhenEmpty() {
        repository.save(point("v1", "u1", "diary", "x", 4, "2026-01-01T00:00:00Z"));
        repository.save(point("v2", "u1", "diary", "y", 8, "2026-01-02T00:00:00Z"));

        assertThat(repository.avgScoreByUserId("u1")).isCloseTo(6.0, within(1e-9));
        // COALESCE(..., 0) 로 데이터 없는 사용자는 0 을 반환한다
        assertThat(repository.avgScoreByUserId("nobody")).isEqualTo(0.0);
    }

    @Test
    void countAll_countsEveryRow() {
        repository.save(point("t1", "u1", "diary", "x", 1, "2026-01-01T00:00:00Z"));
        repository.save(point("t2", "u2", "community", "y", 2, "2026-01-02T00:00:00Z"));

        assertThat(repository.countAll()).isEqualTo(2L);
    }

    @Test
    void countGroupedBySource_groupsAndCounts() {
        repository.save(point("g1", "u1", "diary", "x", 1, "2026-01-01T00:00:00Z"));
        repository.save(point("g2", "u2", "diary", "y", 2, "2026-01-02T00:00:00Z"));
        repository.save(point("g3", "u3", "community", "z", 3, "2026-01-03T00:00:00Z"));

        Map<String, Long> bySource = repository.countGroupedBySource().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (String) row[0], row -> (Long) row[1]));

        assertThat(bySource).containsExactlyInAnyOrderEntriesOf(
                Map.of("diary", 2L, "community", 1L));
    }
}
