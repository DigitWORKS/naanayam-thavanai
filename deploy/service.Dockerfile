# syntax=docker/dockerfile:1
# API image. Build from the repo root: docker compose -f deploy/docker-compose.yml build api
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY db/migrations db/migrations
COPY service service
WORKDIR /src/service
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:25-jre
RUN groupadd -r app && useradd -r -g app app \
 && mkdir -p /data/documents && chown app:app /data/documents
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Kolkata \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -Duser.timezone=Asia/Kolkata"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
