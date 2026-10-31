# Optional container image. NOTE: not yet built or tested — verify with `docker build` before relying on it.
# The app itself needs only a Java 25 runtime; see README.md → Deployment.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
