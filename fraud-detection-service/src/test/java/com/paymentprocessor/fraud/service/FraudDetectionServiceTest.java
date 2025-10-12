package com.paymentprocessor.fraud.service;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.FraudCheckRequestedEvent;
import com.paymentprocessor.fraud.client.ExternalFraudApiClient;
import com.paymentprocessor.fraud.model.FraudCheckResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private ExternalFraudApiClient fraudApiClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private MeterRegistry meterRegistry;

    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        fraudDetectionService = new FraudDetectionService(fraudApiClient, kafkaTemplate, 0.7, meterRegistry);
    }

    private FraudCheckRequestedEvent createTestEvent() {
        return new FraudCheckRequestedEvent(
                UUID.randomUUID().toString(),
                "user123",
                new BigDecimal("100.00"),
                "127.0.0.1",
                "Test-User-Agent"
        );
    }

    @Test
    @DisplayName("should return passed=true when external API indicates low risk")
    void checkFraud_whenLowRisk_returnsPassed() {
        // Given
        var event = createTestEvent();
        var apiResponse = new FraudCheckResponse(0.5, "ACCEPT", "Low risk score");
        when(fraudApiClient.checkFraud(any())).thenReturn(CompletableFuture.completedFuture(apiResponse));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<SendResult<String, Object>>());

        // When
        fraudDetectionService.checkFraud(event).join();

        // Then
        var eventCaptor = ArgumentCaptor.forClass(FraudCheckCompletedEvent.class);
        verify(kafkaTemplate).send(eq("fraud-check-completed"), eq(event.transactionId()), eventCaptor.capture());

        var capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.passed()).isTrue();
        assertThat(capturedEvent.riskScore()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("should return passed=false when external API indicates high risk")
    void checkFraud_whenHighRisk_returnsFailed() {
        // Given
        var event = createTestEvent();
        var apiResponse = new FraudCheckResponse(0.9, "DECLINE", "High risk score");
        when(fraudApiClient.checkFraud(any())).thenReturn(CompletableFuture.completedFuture(apiResponse));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<SendResult<String, Object>>());

        // When
        fraudDetectionService.checkFraud(event).join();

        // Then
        var eventCaptor = ArgumentCaptor.forClass(FraudCheckCompletedEvent.class);
        verify(kafkaTemplate).send(eq("fraud-check-completed"), eq(event.transactionId()), eventCaptor.capture());

        var capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.passed()).isFalse();
        assertThat(capturedEvent.riskScore()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("should handle exceptions from external API gracefully")
    void checkFraud_whenApiThrowsException_handlesGracefully() {
        // Given
        var event = createTestEvent();
        when(fraudApiClient.checkFraud(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("API unavailable")));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<SendResult<String, Object>>());

        // When
        fraudDetectionService.checkFraud(event).join();

        // Then
        var eventCaptor = ArgumentCaptor.forClass(FraudCheckCompletedEvent.class);
        verify(kafkaTemplate).send(eq("fraud-check-completed"), eq(event.transactionId()), eventCaptor.capture());

        var capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.passed()).isTrue(); // Fails safe
        assertThat(capturedEvent.reason()).contains("Fraud check service error");
    }
}
