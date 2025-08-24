package com.paymentprocessor.fraud.service;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.FraudCheckRequestedEvent;
import com.paymentprocessor.common.exception.FraudDetectionException;
import com.paymentprocessor.fraud.client.ExternalFraudApiClient;
import com.paymentprocessor.fraud.model.FraudCheckRequest;
import com.paymentprocessor.fraud.model.FraudCheckResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class FraudDetectionService {
    private final ExternalFraudApiClient fraudApiClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final double fraudThreshold;
    private final Timer fraudCheckTimer;
    private final Counter fraudDetectedCounter;
    private final Counter fraudCheckPassedCounter;

    @Autowired
    public FraudDetectionService(ExternalFraudApiClient fraudApiClient,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 @Value("${fraud.threshold:0.7}") double fraudThreshold,
                                 MeterRegistry meterRegistry) {
        this.fraudApiClient = fraudApiClient;
        this.kafkaTemplate = kafkaTemplate;
        this.fraudThreshold = fraudThreshold;
        this.fraudCheckTimer = Timer.builder("fraud.check.duration")
                .description("Time taken for fraud checks")
                .register(meterRegistry);
        this.fraudDetectedCounter = Counter.builder("fraud.detected")
                .description("Number of fraudulent transactions detected")
                .register(meterRegistry);
        this.fraudCheckPassedCounter = Counter.builder("fraud.check.passed")
                .description("Number of transactions that passed fraud check")
                .register(meterRegistry);
    }

    public CompletableFuture<Void> checkFraud(FraudCheckRequestedEvent event) {
        Timer.Sample sample = Timer.start();
        log.info("Starting fraud check for transaction: {}", event.transactionId());

        try {
            FraudCheckRequest request = buildFraudCheckRequest(event);

            // You might want to manually validate the request before sending it
            // This can be done using a Validator bean if you are in a controller context,
            // or by calling the record's compact constructor which contains validation.
            // For services, ensure the FraudCheckRequest is correctly constructed
            // as its compact constructor handles basic validations.

            return fraudApiClient.checkFraud(request)
                    .thenAccept(response -> {
                        sample.stop(fraudCheckTimer);
                        processFraudCheckResponse(event.transactionId(), response);
                    })
                    .exceptionally(throwable -> {
                        sample.stop(fraudCheckTimer);
                        log.error("Fraud check failed for transaction: {}", event.transactionId(), throwable);
                        handleFraudCheckError(event.transactionId(), throwable);
                        return null;
                    });
        } catch (Exception e) {
            sample.stop(fraudCheckTimer);
            log.error("Failed to initiate fraud check for transaction: {}", event.transactionId(), e);
            throw new FraudDetectionException("Failed to initiate fraud check", e);
        }
    }

    private FraudCheckRequest buildFraudCheckRequest(FraudCheckRequestedEvent event) {
        return new FraudCheckRequest(
                event.userId(),
                event.amount(),
                "USD",  // Default currency - in real implementation, get from event
                event.ipAddress(),
                event.userAgent(),
                event.transactionId()
        );
    }

    private void processFraudCheckResponse(String transactionId, FraudCheckResponse response) {
        log.info("Processing fraud check response for transaction: {} with risk score: {}",
                transactionId, response.riskScore());
        boolean fraudDetected = response.riskScore() > fraudThreshold ||
                "DECLINE".equals(response.decision());
        if(fraudDetected) {
            fraudDetectedCounter.increment();
            log.warn("Fraud detected for transaction: {} with risk score: {}",
                    transactionId, response.riskScore());
        } else {
            fraudCheckPassedCounter.increment();
            log.info("Fraud check passed for transaction: {} with risk score: {}",
                    transactionId, response.riskScore());
        }
        FraudCheckCompletedEvent completedEvent = new FraudCheckCompletedEvent(
                transactionId,
                !fraudDetected, // passed = !fraudDetected
                response.riskScore(),
                response.reason()
        );
        publishFraudCheckCompleted(completedEvent);
    }

    private void handleFraudCheckError(String transactionId, Throwable error) {
        log.error("Fraud check error for transaction: {}", transactionId, error);
        // In case of error, fail safe by allowing the transaction (low risk score)
        // In production, this decision should be configurable
        FraudCheckCompletedEvent completedEvent = new FraudCheckCompletedEvent(
                transactionId,
                true, // Allow transaction on error
                0.1,    // low risk score
                "Fraud check service error - defaulting to approve: " + error.getMessage()
        );
        publishFraudCheckCompleted(completedEvent);
    }

    private void publishFraudCheckCompleted(FraudCheckCompletedEvent event) {
        try {
            kafkaTemplate.send("fraud-check-completed", event.transactionId(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Failed to publish FraudCheckCompletedEvent for transaction: {}",
                                    event.transactionId(), failure);
                        } else {
                            log.info("Successfully published FraudCheckCompletedEvent for transaction: {}",
                                    event.transactionId());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing FraudCheckCompletedEvent for transaction: {}",
                    event.transactionId(), e);
            throw new FraudDetectionException("Failed to publish fraud check result", e);
        }
    }
}