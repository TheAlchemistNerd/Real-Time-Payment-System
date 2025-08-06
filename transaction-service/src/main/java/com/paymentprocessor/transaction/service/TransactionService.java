package com.paymentprocessor.transaction.service;

import com.paymentprocessor.common.event.TransactionCreatedEvent;
import com.paymentprocessor.common.exception.InvalidTransactionException;
import com.paymentprocessor.common.util.TransactionIdGenerator;
import com.paymentprocessor.transaction.dto.CreateTransactionRequest;
import com.paymentprocessor.transaction.dto.CreateTransactionResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Objects;

@Slf4j
@Service
public class TransactionService {

    private static final String TRANSACTION_CREATED_TOPIC = "transaction-created";
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("999999.99");

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionIdGenerator transactionIdGenerator;
    private final Counter transactionCreatedCounter;
    private final Counter transactionRejectedCounter;

    public TransactionService(KafkaTemplate<String, Object> kafkaTemplate,
                              TransactionIdGenerator transactionIdGenerator,
                              MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.transactionIdGenerator = transactionIdGenerator;
        this.transactionCreatedCounter = Counter.builder("transactions.created")
                .description("Number of transactions created")
                .register(meterRegistry);
        this.transactionRejectedCounter = Counter.builder("transactions.rejected")
                .description("Number of transactions rejected")
                .register(meterRegistry);
    }

    public CreateTransactionResponse createTransaction(CreateTransactionRequest request, String correlationId) {
        log.info("Processing transaction creation request for user: {}", request.userId());

        try {
            // Validate transaction request
            validateTransactionRequest(request);

            // Generate unique transaction ID
            var transactionId = transactionIdGenerator.generateTransactionId();

            // Create and publish transaction created event
            var event = new TransactionCreatedEvent(
                    transactionId,
                    request.userId(),
                    request.amount(),
                    request.currency(),
                    request.paymentMethod(),
                    request.description()
            );

            publishTransactionCreatedEvent(transactionId, event, correlationId);

            // Increment success counter
            transactionCreatedCounter.increment();

            // Create successful response
            var response = CreateTransactionResponse.success(transactionId, request.webhookUrl());

            log.info("Transaction {} created successfully for user: {}",
                    transactionId, request.userId());

            return response;

        } catch (Exception e) {
            transactionRejectedCounter.increment();
            log.error("Failed to create transaction for user: {}", request.userId(), e);
            throw e;
        }
    }

    private void validateTransactionRequest(CreateTransactionRequest request) {
        Objects.requireNonNull(request.amount(), "Transaction amount is required");
        Objects.requireNonNull(request.userId(), "User ID is required");
        Objects.requireNonNull(request.currency(), "Currency is required");
        Objects.requireNonNull(request.paymentMethod(), "Payment method is required");

        // Business validation
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be greater than zero");
        }

        if (request.amount().compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new InvalidTransactionException("Transaction amount exceeds maximum limit");
        }

        if (request.userId().isBlank()) {
            throw new InvalidTransactionException("User ID cannot be blank");
        }

        // Validate webhook URL format if provided
        if (request.hasWebhook() && !isValidUrl(request.webhookUrl())) {
            throw new InvalidTransactionException("Invalid webhook URL format");
        }
    }

    private void publishTransactionCreatedEvent(String transactionId, TransactionCreatedEvent event,
                                                String correlationId) {
        try {
            log.info("Publishing TransactionCreatedEvent for transaction: {}", transactionId);

            kafkaTemplate.send(TRANSACTION_CREATED_TOPIC, transactionId, event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Failed to publish TransactionCreatedEvent for transaction: {}",
                                    transactionId, failure);
                        } else {
                            log.info("Successfully published TransactionCreatedEvent for transaction: {}",
                                    transactionId);
                        }
                    });

        } catch (Exception e) {
            log.error("Error publishing TransactionCreatedEvent for transaction: {}", transactionId, e);
            throw new InvalidTransactionException("Failed to initiate transaction processing", e);
        }
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }
}