# analytics-service — 감정·활력 시계열 집계 마이크로서비스

Kafka 이벤트를 소비해 사용자별 감정/활력 시계열(MoodPoint)을 집계하고, 조회 API를 제공하는 분석 서비스입니다.

> 전체 시스템: [tainted-spring-msa](https://github.com/baekchangjoon/tainted-spring-msa)

---

## 역할

- `diary.created`, `post.created`, `mood.logged` Kafka 이벤트를 소비하여 `MoodPoint` 엔티티로 변환·저장
- `eventId` 기반 멱등성 보장 (중복 이벤트 무시)
- 사용자별 감정/활력 시계열 및 전체 집계 데이터 조회 API 제공
- PostgreSQL(`analytics` DB)에 시계열 데이터 영속화

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 23 |
| Framework | Spring Boot 3.4.1 |
| Messaging | Apache Kafka (Spring Kafka) |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| API Docs | springdoc-openapi 2.6 (Swagger UI) |
| Testing | Testcontainers (Postgres + Kafka) + RestAssured |
| Container | Docker (eclipse-temurin:23-jre) |

---

## 빌드 & 테스트

> **사전 요건:** Docker 실행 중, Java 23

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 23)
mvn verify
```

통합 테스트 7개가 Testcontainers(Postgres + Kafka)를 자동으로 기동하여 실행됩니다. Docker가 필요합니다.

---

## 주요 API

> Base path: `/internal/analytics`  
> Port: `8086`

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/internal/analytics/mood/{userId}` | 특정 사용자의 MoodPoint 목록 + 집계(averageScore, count), occurredAt 오름차순 |
| GET | `/internal/analytics/global` | 전체 집계: totalPoints + source 별 count |

```bash
# 사용자 감정 시계열 조회
curl http://localhost:8086/internal/analytics/mood/{userId}

# 전체 집계 조회
curl http://localhost:8086/internal/analytics/global
```

Swagger UI: `http://localhost:8086/swagger-ui.html`

---

## 이벤트 (소비)

| 토픽 | 이벤트 | 설명 |
|------|--------|------|
| `diary.created` | DiaryCreatedEvent | 일기 작성 시 감정/활력 점수 반영 |
| `post.created` | PostCreatedEvent | 게시글 작성 시 감정/활력 점수 반영 |
| `mood.logged` | MoodLoggedEvent | 직접 기분 기록 시 감정/활력 점수 반영 |

모든 이벤트는 `eventId`로 멱등성이 보장됩니다.

---

## Docker

```bash
# 이미지 빌드
docker build -t tainted-spring/analytics:0.1.0 .

# 컨테이너 실행 (환경 변수 설정 필요)
docker run -p 8086:8086 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/analytics \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host:9092 \
  tainted-spring/analytics:0.1.0
```

전체 스택 실행은 [tainted-spring-msa](https://github.com/baekchangjoon/tainted-spring-msa)의 `docker-compose.yml`을 참고하세요.

---

## 라이선스

[MIT](LICENSE) © 2026 baekchangjoon
