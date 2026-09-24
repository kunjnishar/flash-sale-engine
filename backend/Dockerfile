# ---------- Stage 1: Build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only the POM first so Docker can cache the downloaded dependencies
# in their own layer — this layer only gets rebuilt when pom.xml actually changes,
# not on every source code edit.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: Run ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user for security — never run production containers as root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/flash-sale-engine-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]