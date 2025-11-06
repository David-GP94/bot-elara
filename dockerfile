# Dockerfile
FROM eclipse-temurin:21-jdk-alpine

VOLUME /tmp
COPY target/elara-bot-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]