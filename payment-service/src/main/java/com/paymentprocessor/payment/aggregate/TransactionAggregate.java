package com.paymentprocessor.payment.aggregate;

import com.paymentprocessor.common.event.*;
import com.paymentprocessor.common.exception.InvalidTransactionException;
import com.paymentprocessor.common.exception.PaymentProcessingFailedException;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.payment.command.CreateTransactionCommand;
import com.paymentprocessor.payment.command.ProcessFraudCheckCommand;
import com.paymentprocessor.payment.command.ProcessPaymentCommand;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Slf4j
public class TransactionAggregate {

    private String transactionId;

    private String userId;

    private BigDecimal amount;

    private Currency currency;

    private PaymentMethod paymentMethod;

    private String description;

    private TransactionStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private double riskScore;

    private String fraudReason;

    private String paymentGatewayTransactionId;

    private long version;

    private final List<BaseEvent> uncommittedEvents = new ArrayList<>();

    public TransactionAggregate(String transactionId) {
        if(transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        this.transactionId = transactionId;
        this.version = 0L;
    }

    public TransactionAggregate() {}

    public void handle(CreateTransactionCommand command) {
        log.info("Handling CreateTransactionCommand for transaction: {}",
                command.transactionId());

        validateNewTransaction();

        var event = new TransactionCreatedEvent(
                command.transactionId(),
                command.userId(),
                command.amount(),
                command.currency(),
                command.paymentMethod(),
                command.description()
        );
        apply(event);
        log.info("Transaction created successfully: {}",
                command.transactionId());
    }

    public void handle(ProcessFraudCheckCommand command) {
        log.info("Handling ProcessFraudCheckCommand for transaction: {}", command.transactionId());

        validateTransactionExists();
        validateTransactionStatus(TransactionStatus.PENDING);

        var event = new FraudCheckCompletedEvent(
                command.transactionId(),
                command.fraudCheckPassed(),
                command.riskScore(),
                command.reason()
        );

        apply(event);
        log.info("Fraud check completed for transaction: {} - Result: {}",
                command.transactionId(), command.fraudCheckPassed() ? "PASSED" : "FAILED");
    }

    public void handle(ProcessPaymentCommand command) {
        log.info("Handling ProcessPaymentCommand for transaction: {}",
                command.transactionId());

        validateTransactionExists();
        validateTransactionStatus(TransactionStatus.FRAUD_CHECK_PASSED);

        var startedEvent = new PaymentProcessingStartedEvent(
                command.transactionId(),
                command.amount(),
                this.paymentMethod,
                command.paymentGateway()
        );
        apply(startedEvent);

        try{
            var gatewayTransactionId = processWithPaymentGateway(command);

            var processedEvent = new PaymentProcessedEvent(
                    command.transactionId(),
                    command.amount(),
                    gatewayTransactionId,
                    command.paymentGateway()
            );
            apply(processedEvent);

            log.info("Payment processed successfully for transaction: {}",
                    command.transactionId());
        } catch (Exception e) {
            log.error("Payment processing failed for transaction: {}",
                    command.transactionId(), e);

            var failedEvent = new PaymentFailedEvent(
                    command.transactionId(),
                    e.getMessage(),
                    "PAYMENT_GATEWAY_ERROR",
                    true // retryable
            );
            apply(failedEvent);
        }
    }

    public void on(TransactionCreatedEvent event) {
        this.transactionId = event.transactionId();
        this.userId = event.userId();
        this.amount = event.amount();
        this.currency = event.currency();
        this.paymentMethod = event.paymentMethod();
        this.description = event.description();
        this.status = TransactionStatus.PENDING;
        this.createdAt = event.timestamp();
        this.version++;

        log.debug("Applied TransactionCreatedEvent for transaction: {}",
                this.transactionId);
    }

    public void on(FraudCheckCompletedEvent event) {
        this.riskScore = event.riskScore();
        this.fraudReason = event.reason();
        this.status = event.passed() ?
                                TransactionStatus.FRAUD_CHECK_PASSED : TransactionStatus.FRAUD_CHECK_FAILED;
        this.version++;

        log.debug("Applied FraudCheckCompletedEvent for transaction: {} - Status: {}",
                this.transactionId, this.status);
    }

    public void on(PaymentProcessingStartedEvent event) {
        this.status = TransactionStatus.PAYMENT_PROCESSING;
        this.version++;

        log.debug("Applied PaymentProcessingStartedEvent for transaction: {}", this.transactionId);
    }

    public void on(PaymentProcessedEvent event) {
        this.status = TransactionStatus.COMPLETED;
        this.paymentGatewayTransactionId = event.paymentGatewayTransactionId();
        this.completedAt = event.timestamp();
        this.version++;

        log.debug("Applied PaymentProcessedEvent for transaction: {}", this.transactionId);
    }

    public void on(PaymentFailedEvent event) {
        this.status = TransactionStatus.FAILED;
        this.completedAt = event.timestamp();
        this.version++;

        log.debug("Applied PaymentFailedEvent for transaction: {}", this.transactionId);
    }

    private void apply(BaseEvent event) {
        applyEvent(event);
        this.uncommittedEvents.add(event);
    }

    private void applyEvent(BaseEvent event) {
        switch (event) {
            case TransactionCreatedEvent e  -> on(e);
            case FraudCheckCompletedEvent e -> on(e);
            case PaymentProcessingStartedEvent e -> on(e);
            case PaymentProcessedEvent e -> on(e);
            case PaymentFailedEvent e -> on(e);
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass().getSimpleName());
        }
    }

    private void validateNewTransaction() {
        if (this.transactionId != null) {
            throw new InvalidTransactionException("Transaction already exists: " + this.transactionId);
        }
    }

    private void validateTransactionExists() {
        if (this.transactionId == null) {
            throw new InvalidTransactionException("Transaction does not exist");
        }
    }

    private void validateTransactionStatus(TransactionStatus expectedStatus) {
        if (this.status != expectedStatus) {
            throw new InvalidTransactionException(
                    "Invalid transaction status. Expected: %s, Actual: %s".formatted(expectedStatus, this.status));
        }
    }

    private String processWithPaymentGateway(ProcessPaymentCommand command) {
        // Simulate Payment Gateway
        try {
            Thread.sleep(100); // Simulate network call

            // Simulate random failure for testing resilience
            if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% failure rate
                throw new PaymentProcessingFailedException("Payment gateway timeout");
            }

            return "GW-" + System.currentTimeMillis();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProcessingFailedException("Payment processing interrupted", e);
        }
    }

    public List<BaseEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void clearUncommittedEvents() {
        uncommittedEvents.clear();
    }

    public void loadFromHistory(List<BaseEvent> history) {
        history.forEach(this::applyEvent);
        uncommittedEvents.clear();
    }
}
