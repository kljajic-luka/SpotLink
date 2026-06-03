# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY apps/backend/pom.xml apps/backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f apps/backend/pom.xml -DskipTests dependency:go-offline

COPY apps/backend/src apps/backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f apps/backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spotlink && useradd --system --gid spotlink --home-dir /app spotlink

COPY --from=build /workspace/apps/backend/target/spotlink-backend-*.jar /app/spotlink-backend.jar

USER spotlink
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/spotlink-backend.jar"]
