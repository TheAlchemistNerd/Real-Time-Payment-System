package com.paymentprocessor.fraud.service;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.FraudCheckRequestedEvent;
import com.paymentprocessor.common.exception.ExternalServiceException;
import com.paymentprocessor.common.exception.FraudDetectionException;
import com.paymentprocessor.fraud.client.ExternalFraudApiClient;
import com.paymentprocessor.fraud.model.FraudCheckRequest;
import com.paymentprocessor.fraud.model.FraudCheckResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private ExternalFraudApiClient fraudApiClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        fraudDetectionService = new FraudDetectionService(fraudApiClient, kafkaTemplate, 0.7, meterRegistry);
    }

    @ParameterizedTest(name = "score {0}, decision {1}: passed = {2}")
    @CsvSource({
            "0.2, APPROVE, true",
            "0.7, APPROVE, true",
            "0.7001, APPROVE, false",
            "0.2, DECLINE, false",
            "0.9, DECLINE, false"
    })
    void publishesDecisionUsingThresholdAndProviderDecline(double score, String decision, boolean passed) {
        var event = requestedEvent(new BigDecimal("100.00"));
        var response = new FraudCheckResponse("tx-123", score, decision, "Provider assessment", 0.95);
        when(fraudApiClient.checkFraud(any(FraudCheckRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        stubPublication();

        fraudDetectionService.checkFraud(event).join();

        var requestCaptor = ArgumentCaptor.forClass(FraudCheckRequest.class);
        verify(fraudApiClient).checkFraud(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.transactionId()).isEqualTo(event.transactionId());
        assertThat(request.userId()).isEqualTo(event.userId());
        assertThat(request.transactionAmount()).isEqualByComparingTo(event.amount());
        assertThat(request.ipAddress()).isEqualTo(event.ipAddress());
        assertThat(request.userAgent()).isEqualTo(event.userAgent());

        var result = publishedResult();
        assertThat(result.transactionId()).isEqualTo(event.transactionId());
        assertThat(result.passed()).isEqualTo(passed);
        assertThat(result.riskScore()).isEqualTo(score);
        assertThat(result.reason()).isEqualTo("Provider assessment");
        assertThat(meterRegistry.get("fraud.check.passed").counter().count()).isEqualTo(passed ? 1.0 : 0.0);
        assertThat(meterRegistry.get("fraud.detected").counter().count()).isEqualTo(passed ? 0.0 : 1.0);
        verifyNoMoreInteractions(fraudApiClient, kafkaTemplate);
    }

    @Test
    void waitsForTheProviderResultBeforePublishingADecision() {
        var providerResult = new CompletableFuture<FraudCheckResponse>();
        when(fraudApiClient.checkFraud(any(FraudCheckRequest.class))).thenReturn(providerResult);
        stubPublication();

        var completion = fraudDetectionService.checkFraud(requestedEvent(new BigDecimal("100.00")));

        assertThat(completion.isDone()).isFalse();
        verifyNoInteractions(kafkaTemplate);

        providerResult.complete(new FraudCheckResponse("tx-123", 0.8, "DECLINE", "High risk", 0.95));
        completion.join();

        assertThat(publishedResult().passed()).isFalse();
    }

    @Test
    @DisplayName("Characterizes legacy approval on failed provider future; retry policy is outside this unit")
    void failedProviderFuturePublishesTheExistingErrorApproval() {
        when(fraudApiClient.checkFraud(any(FraudCheckRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new ExternalServiceException("FRAUDAPI", "API is down", null)));
        stubPublication();

        fraudDetectionService.checkFraud(requestedEvent(new BigDecimal("100.00"))).join();

        var result = publishedResult();
        assertThat(result.transactionId()).isEqualTo("tx-123");
        assertThat(result.passed()).isTrue();
        assertThat(result.riskScore()).isEqualTo(0.1);
        assertThat(result.reason()).contains("defaulting to approve", "API is down");
        verify(fraudApiClient).checkFraud(any(FraudCheckRequest.class));
        verifyNoMoreInteractions(fraudApiClient, kafkaTemplate);
    }

    @Test
    void synchronousProviderFailureIsReportedWithoutPublishingADecision() {
        var failure = new IllegalStateException("Provider client unavailable");
        when(fraudApiClient.checkFraud(any(FraudCheckRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> fraudDetectionService.checkFraud(requestedEvent(new BigDecimal("100.00"))))
                .isInstanceOf(FraudDetectionException.class)
                .hasMessageContaining("Failed to initiate fraud check")
                .hasCause(failure);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void invalidAmountPrecisionIsRejectedBeforeCallingTheProvider() {
        assertThatThrownBy(() -> fraudDetectionService.checkFraud(requestedEvent(new BigDecimal("100.001"))))
                .isInstanceOf(FraudDetectionException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(fraudApiClient, kafkaTemplate);
    }

    private void stubPublication() {
        when(kafkaTemplate.send(eq("fraud-check-completed"), eq("tx-123"), any(FraudCheckCompletedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private FraudCheckCompletedEvent publishedResult() {
        var captor = ArgumentCaptor.forClass(FraudCheckCompletedEvent.class);
        verify(kafkaTemplate).send(eq("fraud-check-completed"), eq("tx-123"), captor.capture());
        return captor.getValue();
    }

    private FraudCheckRequestedEvent requestedEvent(BigDecimal amount) {
        return new FraudCheckRequestedEvent("event-123", "tx-123", LocalDateTime.of(2026, 10, 20, 9, 0),
                "user-456", amount, "192.0.2.1", "test-agent");
    }
}
