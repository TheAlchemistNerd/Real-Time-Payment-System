FROM eclipse-temurin:21.0.13_9-jre-jammy

ARG APP_VERSION=1.0.0
RUN command -v curl && groupadd --gid 10001 payment && useradd --uid 10001 --gid payment --no-create-home payment
WORKDIR /app
COPY --chown=10001:10001 payment-service/target/payment-service-${APP_VERSION}.jar app.jar
USER 10001:10001
ENV SERVER_PORT=8081 \
    MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=240s --retries=12 \
    CMD curl --fail --silent --max-time 4 "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
