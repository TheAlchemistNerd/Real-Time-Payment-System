package com.paymentprocessor.fraud.service;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.FraudCheckRequestedEvent;
import com.paymentprocessor.common.exception.ExternalServiceException;
import com.paymentprocessor.fraud.client.ExternalFraudApiClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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

    @Captor
    private ArgumentCaptor<FraudCheckCompletedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        fraudDetectionService = new FraudDetectionService(fraudApiClient, kafkaTemplate, 0.7, meterRegistry);
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any(FraudCheckCompletedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void checkFraud_shouldRetryWithJitter_whenApiFails() {
        // Given
        FraudCheckRequestedEvent event = mock(FraudCheckRequestedEvent.class);
        when(event.transactionId()).thenReturn("tx-123");
        when(event.userId()).thenReturn("user-456");
        when(event.amount()).thenReturn(BigDecimal.valueOf(100.0);
        when(event.ipAddress()).thenReturn("127.0.0.1");
        when(event.userAgent()).thenReturn("test-agent");
        when(fraudApiClient.checkFraud(any()))
                .thenReturn(CompletableFuture.failedFuture(new ExternalServiceException("FRAUDAPI", "API is down", null)));
                public record FraudCheckRequestedEvent(
                    String transactionId,
                    String userId,
                    double amount,
                    String ipAddress,
                    String userAgent
                ) {}
        // When
        fraudDetectionService.checkFraud(event);

        // Then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(fraudApiClient, times(3)).checkFraud(any());
            verify(kafkaTemplate).send(eq("fraud-check-completed"), eq("tx-123"), eventCaptor.capture());
        });

        FraudCheckCompletedEvent completedEvent = eventCaptor.getValue();
        assertThat(completedEvent.passed()).isTrue();
        assertThat(completedEvent.riskScore()).isEqualTo(0.1);
    }
}