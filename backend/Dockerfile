# ============================================
# Stage 1: Build (Maven + JDK 17)
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first so dependency layer is cached across builds
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source and build the jar (skip tests to keep image builds fast;
# run `mvn test` in CI separately)
COPY src ./src
RUN mvn -B clean package -DskipTests

# ============================================
# Stage 2: Runtime (JRE only — small image)
# ============================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Only one jar matches (Spring Boot renames the thin jar to *.jar.original)
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# All secrets/config come from environment variables at `docker run` time —
# nothing from .env is baked into this image. See application.properties
# for the full list of env vars this app reads (DB_URL, JWT_SECRET, etc.)
ENTRYPOINT ["java", "-jar", "/app/app.jar"]