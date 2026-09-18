# ==========================================
# Stage 1: Build the Spring Boot application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install dos2unix to ensure Windows CRLF endings do not break mvnw on Linux
RUN apk add --no-cache dos2unix

# Copy Maven wrapper configuration and dependencies descriptor
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Ensure line endings are in unix format and script is executable
RUN dos2unix mvnw && chmod +x mvnw

# Resolve dependencies for Docker layer caching
RUN ./mvnw dependency:go-offline -B || true

# Copy application source code and package JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Stage 2: Minimal runtime container
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root system user and group for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built JAR artifact from build stage
COPY --from=build /app/target/*.jar app.jar

# Set ownership to non-root user
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
