# build
FROM maven:3.9-eclipse-temurin-23 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# run
FROM eclipse-temurin:23-jre
WORKDIR /app
# curl: compose/k8s 헬스체크가 /actuator/health 를 호출하는 데 사용.
# temurin JRE 기본 이미지에는 curl/wget 이 없으므로 명시적으로 설치한다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 appuser
COPY --from=build /app/target/analytics-service-0.1.0.jar app.jar
USER appuser
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
