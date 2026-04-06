# -- Stage 1: Build ----------------------------------------------------------
FROM maven:3.9.8-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml ./
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# -- Stage 2: Run ------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/jsonl-mock-generator-0.1.0.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
