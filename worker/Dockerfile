FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /src/target/worker-jar-with-dependencies.jar worker.jar
RUN adduser -D -H -u 10001 app
USER app
CMD ["java", "-jar", "worker.jar"]
