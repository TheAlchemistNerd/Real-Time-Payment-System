package com.paymentprocessor.startup;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@Timeout(value = 4, unit = TimeUnit.MINUTES)
class PackagedServicesIT {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void createListenerTopics() throws Exception {
        var topics = List.of("create-transaction", "fraud-check-completed", "payment-processed", "payment-failed",
                "transaction-events", "fraud-check-requested", "transaction-completed");
        try (var admin = kafkaAdmin()) {
            admin.createTopics(topics.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
                    .all().get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void paymentStartsWithPostgresAndKafka() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:16.6-alpine")) {
            postgres.start();
            try (var service = start("payment-service", "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword())) {
                service.awaitReadiness();
                var summary = service.getJson("/api/v1/payments/summary");
                assertThat(summary.required("totalTransactions").longValue()).isZero();
                assertSubscriptions(Map.of("payment-service", Set.of("create-transaction"),
                        "payment-saga", Set.of("fraud-check-completed", "payment-processed", "payment-failed"),
                        "transaction-projection", Set.of("transaction-events")));
            }
        }
    }

    @Test
    void fraudStartsWithKafkaAndALocalProviderAddress() throws Exception {
        var stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.start();
        try (var service = start("fraud-detection-service", "--fraud.api.key=startup-test",
                "--fraud.api.base-url=http://127.0.0.1:" + stub.getAddress().getPort())) {
            service.awaitReadiness();
            assertThat(service.getJson("/api/v1/info").required("service").asText()).isEqualTo("fraud-detection-service");
            assertSubscriptions(Map.of("fraud-detection-service", Set.of("fraud-check-requested")));
        } finally {
            stub.stop(0);
        }
    }

    @Test
    void notificationStartsWithKafkaAndLocalSmtp() throws Exception {
        var smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        try {
            smtp.start();
            smtp.setUser("startup@example.test", "startup", "test");
            try (var service = start("notification-service", "--notification.email.providers.smtp.host=127.0.0.1",
                    "--notification.email.providers.smtp.port=" + smtp.getSmtp().getPort(),
                    "--notification.email.providers.smtp.username=startup", "--notification.email.providers.smtp.password=test")) {
                service.awaitReadiness();
                assertThat(service.getJson("/api/notification/v1/info").required("service").asText()).isEqualTo("notification-service");
                assertThat(service.getJson("/actuator/health").required("status").asText()).isEqualTo("UP");
                assertSubscriptions(Map.of("notification-service", Set.of("transaction-completed"),
                        "notification-service-fraud", Set.of("fraud-check-completed"),
                        "notification-service-payment", Set.of("payment-failed")));
            }
        } finally {
            smtp.stop();
        }
    }

    @Test
    void notificationDeclaresItsOwnDefaultHttpPort() throws Exception {
        try (var jar = new JarFile(System.getProperty("notification-service.jar"));
             var config = jar.getInputStream(jar.getJarEntry("BOOT-INF/classes/application.yml"))) {
            var yaml = new YamlPropertySourceLoader().load("packaged notification settings", new ByteArrayResource(config.readAllBytes()));
            assertThat(yaml.getFirst().getProperty("server.port")).isEqualTo(8083);
        }
    }

    private PackagedService start(String module, String... settings) throws Exception {
        return new PackagedService(module, temporaryDirectory, KAFKA.getBootstrapServers(), settings);
    }

    private static AdminClient kafkaAdmin() {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000, AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10_000));
    }

    private static void assertSubscriptions(Map<String, Set<String>> expected) {
        try (var admin = kafkaAdmin()) {
            await().atMost(Duration.ofSeconds(45)).ignoreExceptions().untilAsserted(() -> {
                var groups = admin.describeConsumerGroups(expected.keySet()).all().get(8, TimeUnit.SECONDS);
                expected.forEach((group, topics) -> {
                    var assigned = groups.get(group).members().stream()
                            .flatMap(member -> member.assignment().topicPartitions().stream())
                            .map(TopicPartition::topic).collect(Collectors.toSet());
                    assertThat(assigned).as("active Kafka subscriptions for %s", group).isEqualTo(topics);
                });
            });
        }
    }
}
