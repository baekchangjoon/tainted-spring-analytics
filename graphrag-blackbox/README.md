# analytics — graph-rag Method 1 블랙박스 테스트 (out-of-process RestAssured)

이 디렉터리는 **graph-rag-test-generator (Method 1)** 로 `analytics` 마이크로서비스에 대해
생성한 **out-of-process 블랙박스 테스트** 아티팩트다. 테스트는 analytics 애플리케이션 코드를
전혀 import 하지 않고, 실제로 기동된 SUT(HTTP :8086) 에 RestAssured 로 요청을 보내고
postgres 에 시드 데이터를 넣어 응답을 검증한다. 실행은 graph-rag 저장소의 `:e2e` 하니스
(testlib + RestAssured + JUnit5 + postgres/kafka 드라이버 포함) 를 통해 한다.

## 무엇이 들어있나

```
graphrag-blackbox/
├── generated-tests/
│   ├── io/graphrag/generated/analytics/AnalyticsGlobalGetTest.java       # GET /internal/analytics/global (1 test)
│   ├── io/graphrag/generated/analytics/AnalyticsMoodByUserGetTest.java   # GET /internal/analytics/mood/{userId} (3 tests)
│   ├── junit-platform.properties                                         # 생성기가 emit 한 병렬 실행 설정
│   └── generation-result.json                                            # Tool 2 생성 메타데이터
├── graph/
│   ├── graph.json                  # Tool 1 산출 그래프(엔드포인트/경로/SQL 캡처)
│   └── exploration-report.json     # 탐색 커버리지 리포트
├── requests/
│   ├── request-analytics-global.json
│   └── request-analytics-mood-userid.json
└── README.md
```

## 결과 요약

- 발견 엔드포인트: **2** (`GET /internal/analytics/global`, `GET /internal/analytics/mood/{userId}`)
- 탐색 경로: **4** (global 1, mood 3) / SQL 캡처 **10** / 테이블 **1** (`mood_point`)
- 생성 테스트: **4** (pass 4 / quarantine 0 / fail 0)
- 앱 라인 커버리지(탐색 기준): **70/119 (58%)**, 앱 브랜치 **4/4 (100%)**
- Kafka: analytics 는 Kafka 를 **소비(consume)** 만 하고 발행하지 않으므로 outbound produce 캡처
  대상이 없다 → 다이어리 파일럿에서 보였던 per-request UUID/timestamp JSONAssert 비결정성 이슈가
  **발생하지 않는다**. 시드는 전부 postgres `mood_point` 에 직접 INSERT 한다.
- **quarantine 없음**: 4개 모두 결정적으로 GREEN. `KNOWN-LIMITATIONS.md` 불필요.

## 재현 방법

전제: JDK 23, Docker, graph-rag 저장소가
`/Users/changjoonbaek/github_graph-rag-test-generator/graph-rag` 에 빌드된 jar 와 함께 있어야 한다
(`graph-rag-builder/build/libs/`, `test-generator/build/libs/`).

경로 변수(이 머신 기준):

```bash
export JAVA_HOME=/Users/changjoonbaek/Library/Java/JavaVirtualMachines/jdk-23.0.2+7/Contents/Home
GR=/Users/changjoonbaek/github_graph-rag-test-generator/graph-rag
ANALYTICS=/Users/changjoonbaek/github_tainted-spring/tainted-spring-analytics
PLATFORM=/Users/changjoonbaek/github_tainted-spring/tainted-spring-platform
W=$GR/.work/analytics      # 작업 출력 디렉터리
```

### 0) SUT jar 빌드 (Maven)

```bash
cd "$ANALYTICS"
mvn -q -DskipTests package         # → target/analytics-service-0.1.0.jar
```

### 1) Tool 1 — 분기 탐색 + 그래프 빌드

`--db-service postgres` 는 **필수**다. 플랫폼 compose 는 mysql+postgres 멀티 DB라 자동탐지가
mysql 을 골라 JDBC 가 어긋나면 SUT 가 부팅 시 죽는다. analytics 는 postgres 를 쓴다.

```bash
cd "$GR"
./gradlew :graph-rag-builder:run --args="build \
  --sut-src   $ANALYTICS/src/main/java \
  --sut-resources $ANALYTICS/src/main/resources \
  --sut-jar   $ANALYTICS/target/analytics-service-0.1.0.jar \
  --sut-compose $PLATFORM/docker-compose.yml \
  --sut-id analytics --with-kafka --db-service postgres \
  --sut-java-home $JAVA_HOME \
  --out $W/graph-out"
```

산출: `$W/graph-out/graph.json`, `exploration-report.json` (이 디렉터리의 `graph/` 에 복사됨).

### 2) Tool 2 — 엔드포인트별 테스트 생성 (요청 1개당 한 번, 같은 --out 으로 누적)

요청 JSON 은 이 디렉터리의 `requests/` 에 있다(`packageName: io.graphrag.generated.analytics`,
`authMode: DISABLED`).

```bash
cd "$GR"
for req in request-analytics-global request-analytics-mood-userid; do
  ./gradlew :test-generator:run --args="generate \
    --request $W/requests/$req.json \
    --graph   $W/graph-out \
    --out     $W/generated"
done
# → $W/generated/io/graphrag/generated/analytics/*.java + junit-platform.properties
```

### 3) SUT 스택 기동 (analytics + postgres + kafka)

플랫폼 compose 로 띄운다. postgres init 스크립트(`init/postgres/01-create-databases.sql`)가
`analytics` DB 를 생성한다. analytics 는 호스트 포트 **8086**, postgres **5432**(DB `analytics`,
user `postgres`, pw `postgrespw`), kafka **9092**.

```bash
docker compose -p granalytics -f $PLATFORM/docker-compose.yml up -d --build analytics
# health 대기
until curl -fsS http://localhost:8086/actuator/health | grep -q UP; do sleep 3; done
```

### 4) 생성 테스트 실행 (graph-rag :e2e 하니스)

```bash
cp -R $W/generated/io  $GR/e2e/build/generated-tests/
cp $W/generated/junit-platform.properties $GR/e2e/src/test/resources/

cd "$GR"
APP_BASE_URI=http://localhost:8086 \
JDBC_URL=jdbc:postgresql://localhost:5432/analytics \
JDBC_USER=postgres JDBC_PASS=postgrespw \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  ./gradlew :e2e:test --rerun
# 결과: tests=4 skipped=0 failures=0 errors=0
```

### 5) 정리

```bash
rm -rf $GR/e2e/build/generated-tests/io
docker compose -p granalytics -f $PLATFORM/docker-compose.yml down -v
```

## 다이어리 레시피 대비 analytics 노브

| 항목 | 값 |
|------|-----|
| 빌드 | Maven (`mvn -DskipTests package`) |
| jar 이름 | `analytics-service-0.1.0.jar` |
| JDK | 23 (`pom.xml` java.version=23) |
| DB | postgres, DB 이름 `analytics`, user `postgres`/pw `postgrespw` |
| 호스트 포트 | analytics 8086, postgres 5432, kafka 9092 |
| Kafka | `--with-kafka` (소비 전용; produce 캡처 대상 없음) |
| auth | 없음 (`authMode: DISABLED`) |
| inter-service HTTP | 없음 |
