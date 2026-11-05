package com.paymentprocessor.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the packaged intake service through its public HTTP and Kafka interfaces. */
@Testcontainers
class TransactionKafkaIT {

    private static final String TOPIC = "create-transaction";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @TempDir
    Path portDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private Process service;

    @AfterEach
    void stopService() throws InterruptedException {
        if (service != null) {
            service.destroy();
            if (!service.waitFor(5, TimeUnit.SECONDS)) {
                service.destroyForcibly();
                assertThat(service.waitFor(10, TimeUnit.SECONDS)).as("packaged service stopped").isTrue();
            }
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void packagedServicePublishesTheHttpTransactionAsAKeyedJsonCommand() throws Exception {
        try (var admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20_000))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
        }

        var baseUri = startService();
        var request = new CreateTransactionRequest("merchant-user-1", new BigDecimal("150.25"),
                Currency.EUR, PaymentMethod.BANK_TRANSFER, "Kafka boundary order",
                "https://merchant.example/payment-events", "192.0.2.1", "integration-test");

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "transaction-intake-boundary-test",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000,
                ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20_000))) {
            var partition = new TopicPartition(TOPIC, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));

            var post = HttpRequest.newBuilder(baseUri.resolve("/api/v1/transactions"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            var response = http.send(post, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("transaction response: %s", response.body()).isEqualTo(202);
            var body = objectMapper.readTree(response.body());
            assertThat(body.path("status").asText()).isEqualTo("PENDING");
            var transactionId = body.path("transactionId").asText();
            assertThat(transactionId).isNotBlank();

            var record = awaitRecord(consumer);
            assertThat(record.topic()).isEqualTo(TOPIC);
            assertThat(record.key()).isEqualTo(transactionId);

            var command = objectMapper.readValue(record.value(), CreateTransactionCommand.class);
            assertThat(command.transactionId()).isEqualTo(transactionId);
            assertThat(command.userId()).isEqualTo(request.userId());
            assertThat(command.amount()).isEqualByComparingTo(request.amount());
            assertThat(command.currency()).isEqualTo(request.currency());
            assertThat(command.paymentMethod()).isEqualTo(request.paymentMethod());
            assertThat(command.description()).isEqualTo(request.description());
            assertThat(command.webhookUrl()).isEqualTo(request.webhookUrl());
        }
    }

    private URI startService() throws Exception {
        Path jar = Path.of(System.getProperty("transaction.jar")).toAbsolutePath();
        assertThat(jar).as("packaged transaction service").isRegularFile();
        Path log = jar.resolveSibling("failsafe-reports").resolve("packaged-transaction.log");
        Files.createDirectories(log.getParent());
        String java = ProcessHandle.current().info().command().orElseThrow();
        Path portFile = portDirectory.resolve("application.port").toAbsolutePath();
        service = new ProcessBuilder(java, "-Xmx256m", "-DPORTFILE=" + portFile, "-jar", jar.toString(),
                "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                "--server.address=127.0.0.1", "--server.port=0",
                "--context.listener.classes=org.springframework.boot.web.context.WebServerPortFileWriter",
                "--management.endpoint.health.probes.enabled=true")
                .directory(jar.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(service.isAlive()).as("packaged process exited; inspect %s", log).isTrue();
            String port = Files.exists(portFile) ? Files.readString(portFile).trim() : "";
            if (!port.isEmpty()) {
                URI base = URI.create("http://127.0.0.1:" + port);
                var probe = HttpRequest.newBuilder(base.resolve("/actuator/health/readiness"))
                        .timeout(Duration.ofSeconds(2)).GET().build();
                try {
                    var result = http.send(probe, HttpResponse.BodyHandlers.ofString());
                    if (result.statusCode() == 200 && "UP".equals(objectMapper.readTree(result.body()).path("status").asText())) {
                        return base;
                    }
                } catch (ConnectException | HttpTimeoutException starting) {
                    // The server can publish its port before it can serve HTTP requests.
                }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Transaction service did not report readiness within two minutes. See " + log);
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("No serialized creation command arrived on " + TOPIC + " within 20 seconds");
    }
}
