# Build stage
FROM gradle:jdk21-alpine AS build

WORKDIR /app

# Copy Gradle wrapper and build configuration for dependency caching
COPY gradlew .
COPY gradlew.bat .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
COPY resource-service/build.gradle resource-service/build.gradle
COPY song-service/build.gradle song-service/build.gradle
COPY eureka-server/build.gradle eureka-server/build.gradle
COPY api-gateway/build.gradle api-gateway/build.gradle

# Fix Windows line endings and ensure gradlew is executable
RUN sed -i 's/\r//' gradlew && chmod +x gradlew

# Download dependencies (cached as a separate layer)
RUN ./gradlew :resource-service:dependencies --no-daemon

# Copy source code and build
COPY resource-service/src resource-service/src
RUN ./gradlew :resource-service:assemble --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /app/resource-service/build/libs/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
