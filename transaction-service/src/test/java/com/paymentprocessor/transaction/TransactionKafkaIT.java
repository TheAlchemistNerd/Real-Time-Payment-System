package com.paymentprocessor.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import com.paymentprocessor.transaction.service.TransactionService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Real intake serialization boundary; downstream payment execution is outside this test. */
@SpringBootTest(classes = TransactionServiceApplication.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionKafkaIT {

    private static final String TOPIC = "create-transaction";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry properties) {
        properties.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Timeout(60)
    void publishesTheReturnedTransactionAsAKeyedJsonCommand() throws Exception {
        try (var admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20_000))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
        }

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

            var response = transactionService.createTransaction(request, "kafka-boundary-correlation-1");
            assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(response.transactionId()).isNotBlank();

            var record = awaitRecord(consumer);
            assertThat(record.topic()).isEqualTo(TOPIC);
            assertThat(record.key()).isEqualTo(response.transactionId());

            var command = objectMapper.readValue(record.value(), CreateTransactionCommand.class);
            assertThat(command.transactionId()).isEqualTo(response.transactionId());
            assertThat(command.userId()).isEqualTo(request.userId());
            assertThat(command.amount()).isEqualByComparingTo(request.amount());
            assertThat(command.currency()).isEqualTo(request.currency());
            assertThat(command.paymentMethod()).isEqualTo(request.paymentMethod());
            assertThat(command.description()).isEqualTo(request.description());
            assertThat(command.webhookUrl()).isEqualTo(request.webhookUrl());
        }
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
