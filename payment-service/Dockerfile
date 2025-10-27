FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/payment-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8081/api/v1/payments/health || exit 1
