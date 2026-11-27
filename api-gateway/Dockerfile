# Build the jar first on your machine:  ./mvnw -q package -DskipTests
# This image only copies that jar in, so it builds in seconds and needs no Maven.
FROM eclipse-temurin:21-jre-alpine

# Do not run as root inside the container.
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

COPY target/api-gateway.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
