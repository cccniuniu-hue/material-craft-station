FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S workshop && adduser -S workshop -G workshop && mkdir -p /app/storage && chown -R workshop:workshop /app
WORKDIR /app
COPY --from=build /build/target/material-workshop-1.0.0.jar app.jar
USER workshop
ENV STORAGE_DIR=/app/storage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
