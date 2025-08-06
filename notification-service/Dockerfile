
FROM openjdk:17-jre-slim

VOLUME /tmp

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} notification-service.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/notification-service.jar"]