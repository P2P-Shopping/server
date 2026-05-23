FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradle /app/gradle
COPY gradlew /app/
COPY build.gradle.kts settings.gradle.kts /app/

RUN ./gradlew dependencies --no-daemon

COPY src /app/src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app

# Copiază doar artifact-ul compilat din faza anterioară
COPY --from=builder /app/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Xss256k"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]