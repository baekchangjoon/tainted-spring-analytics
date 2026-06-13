package com.tainted.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AnalyticsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("analytics")
            .withUsername("postgres")
            .withPassword("postgrespw");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    int port;

    private KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        Map<String, Object> producerProps = Map.of(
                "bootstrap.servers", kafka.getBootstrapServers(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    /**
     * diary.created + mood.logged 각 1건 발행 → u1의 MoodPoint 2건 확인.
     * 비동기 소비이므로 최대 10초간 폴링.
     */
    @Test
    void publishDiaryAndMoodEvents_thenQueryReturns2Points() throws Exception {
        String diaryPayload = objectMapper.writeValueAsString(Map.of(
                "eventId", "evt-diary-u1",
                "userId", "u1",
                "diaryId", "d1",
                "primaryEmotion", "불안",
                "energyScore", 3,
                "occurredAt", "2026-01-01T00:00:00Z"
        ));
        String moodPayload = objectMapper.writeValueAsString(Map.of(
                "eventId", "evt-mood-u1",
                "userId", "u1",
                "source", "diary",
                "mood", "긴장",
                "score", 5,
                "occurredAt", "2026-01-01T01:00:00Z"
        ));

        kafkaTemplate.send("diary.created", diaryPayload).get();
        kafkaTemplate.send("mood.logged", moodPayload).get();

        // 비동기 소비 대기: 최대 10초간 2건이 될 때까지 폴링
        awaitMoodCount("u1", 2);

        given().when().get("/internal/analytics/mood/u1")
                .then().statusCode(200)
                .body("userId", equalTo("u1"))
                .body("count", equalTo(2))
                .body("points", hasSize(2))
                .body("points[0].label", equalTo("불안"))
                .body("points[0].score", equalTo(3))
                .body("points[1].label", equalTo("긴장"))
                .body("points[1].score", equalTo(5));
    }

    /**
     * 동일한 eventId 로 diary.created 를 두 번 발행해도 MoodPoint 1건만 저장됨(idempotency).
     */
    @Test
    void duplicateEventId_doesNotDoubleCount() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "evt-dedup-u2",
                "userId", "u2",
                "diaryId", "d2",
                "primaryEmotion", "슬픔",
                "energyScore", 2,
                "occurredAt", "2026-02-01T00:00:00Z"
        ));

        // 동일 페이로드 2회 발행
        kafkaTemplate.send("diary.created", payload).get();
        kafkaTemplate.send("diary.created", payload).get();

        // 1건만 저장될 때까지 대기
        awaitMoodCount("u2", 1);

        // 추가 지연 후에도 여전히 1건
        Thread.sleep(2000);
        given().when().get("/internal/analytics/mood/u2")
                .then().statusCode(200)
                .body("count", equalTo(1));
    }

    /**
     * /internal/analytics/global 은 totalPoints 와 countBySource 를 포함한다.
     */
    @Test
    void globalAggregateContainsTotalPoints() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "evt-global-u3",
                "userId", "u3",
                "diaryId", "d3",
                "primaryEmotion", "기쁨",
                "energyScore", 9,
                "occurredAt", "2026-03-01T00:00:00Z"
        ));
        kafkaTemplate.send("diary.created", payload).get();

        awaitTotalPointsAtLeast(1);

        given().when().get("/internal/analytics/global")
                .then().statusCode(200)
                .body("totalPoints", greaterThanOrEqualTo(1))
                .body("countBySource", notNullValue());
    }

    // ── 헬퍼: 비동기 소비 대기 ─────────────────────────────────────

    /** userId의 MoodPoint 수가 expected 이상이 될 때까지 최대 10초 폴링. */
    private void awaitMoodCount(String userId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            int count = given().when().get("/internal/analytics/mood/" + userId)
                    .then().statusCode(200)
                    .extract().path("count");
            if (count >= expected) return;
            Thread.sleep(500);
        }
        throw new AssertionError("userId=" + userId + " did not reach " + expected + " MoodPoints within 10s");
    }

    /** 전체 totalPoints 가 atLeast 이상이 될 때까지 최대 10초 폴링. */
    private void awaitTotalPointsAtLeast(int atLeast) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            int total = given().when().get("/internal/analytics/global")
                    .then().statusCode(200)
                    .extract().path("totalPoints");
            if (total >= atLeast) return;
            Thread.sleep(500);
        }
        throw new AssertionError("totalPoints did not reach " + atLeast + " within 10s");
    }
}
