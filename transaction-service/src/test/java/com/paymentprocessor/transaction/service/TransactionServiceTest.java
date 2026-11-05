package com.paymentprocessor.transaction.service;

import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.util.TransactionIdGenerator;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private TransactionIdGenerator transactionIdGenerator;

    private MeterRegistry meterRegistry;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        transactionService = new TransactionService(kafkaTemplate, transactionIdGenerator, meterRegistry);
    }

    @Test
    @DisplayName("should call Kafka producer when initiating a transaction")
    void createTransaction_shouldPublishEventToKafka() {
        // Given
        var transactionId = UUID.randomUUID().toString();
        var request = new CreateTransactionRequest(
                "user123",
                new BigDecimal("150.00"),
                Currency.EUR,
                PaymentMethod.BANK_TRANSFER,
                "Test Transaction",
                "http://webhook.site/test",
                "127.0.0.1",
                "Test-User-Agent"
        );
        var correlationId = UUID.randomUUID().toString();

        when(transactionIdGenerator.generateTransactionId()).thenReturn(transactionId);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<SendResult<String, Object>>());

        // When
        transactionService.createTransaction(request, correlationId);

        // Then
        var eventCaptor = ArgumentCaptor.forClass(CreateTransactionCommand.class);
        verify(kafkaTemplate).send(eq("create-transaction"), eq(transactionId), eventCaptor.capture());

        var capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.transactionId()).isEqualTo(transactionId);
        assertThat(capturedEvent.userId()).isEqualTo(request.userId());
        assertThat(capturedEvent.amount()).isEqualTo(request.amount());
    }
}
