FROM openjdk:17-jre-slim

VOLUME /tmp

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} fraud-detection-service.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/fraud-detection-service.jar"]