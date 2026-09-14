# syntax=docker/dockerfile:1

# 빌드 스테이지. 러너의 build 잡 산출물에 기대지 않고 컨텍스트 안에서 다시 굽습니다.
# 이미지에 들어가는 jar가 무엇으로 만들어졌는지 Dockerfile만 보면 알 수 있게 하려는 것입니다.
FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /workspace

# 의존성 해석에 필요한 파일만 먼저 넣어 레이어를 캐시합니다. 소스만 바뀌면 다시 받지 않습니다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src

# bootJar만 실행하므로 -plain.jar는 생성되지 않지만, 방어적으로 걸러냅니다.
RUN ./gradlew clean bootJar --no-daemon     && JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"     && test -n "$JAR_FILE"     && cp "$JAR_FILE" /app.jar

FROM eclipse-temurin:21-jre-noble

WORKDIR /app

# 루트로 돌지 않게 전용 계정을 만듭니다.
RUN groupadd --system --gid 10001 komit     && useradd --system --uid 10001 --gid komit komit

COPY --from=builder --chown=komit:komit /app.jar app.jar

USER komit

EXPOSE 8080

# 컨테이너 메모리 제한을 힙 계산에 반영시킵니다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
