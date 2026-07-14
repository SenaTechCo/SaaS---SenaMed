# Multi-stage build so the final image only ships the compiled jar, not the whole Maven toolchain.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render sets $PORT at runtime; server.port must follow it (see application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
