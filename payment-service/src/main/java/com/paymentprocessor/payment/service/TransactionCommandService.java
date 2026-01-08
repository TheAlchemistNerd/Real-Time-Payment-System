package com.paymentprocessor.payment.service;

import com.paymentprocessor.common.event.BaseEvent;
import com.paymentprocessor.payment.aggregate.TransactionAggregate;
import com.paymentprocessor.common.command.CreateTransactionCommand;
import com.paymentprocessor.common.command.ProcessFraudCheckCommand;
import com.paymentprocessor.common.command.ProcessPaymentCommand;
import com.paymentprocessor.payment.metrics.PaymentMetrics;
import com.paymentprocessor.payment.publisher.EventPublisher;
import com.paymentprocessor.payment.repository.TransactionAggregateRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TransactionCommandService {

    private final TransactionAggregateRepository repository;
    private final EventPublisher eventPublisher;
    private final PaymentMetrics metrics;
    private final PaymentGatewayFactory paymentGatewayFactory;

    public TransactionCommandService(TransactionAggregateRepository repository,
                                     EventPublisher eventPublisher,
                                     PaymentMetrics metrics,
                                     PaymentGatewayFactory paymentGatewayFactory) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.paymentGatewayFactory = paymentGatewayFactory;
    }

    @Transactional
    @Retry(name = "commandProcessing")
    @Bulkhead(name = "commandProcessing", type = Bulkhead.Type.SEMAPHORE)
    public void handle(CreateTransactionCommand command) {
        var sample = Timer.start();
        try {
            log.info("Processing CreateTransactionCommand for transaction: {}", command.transactionId());
            var aggregate = new TransactionAggregate(command.transactionId());
            aggregate.handle(command);
            repository.save(aggregate);
            publishEventsAfterCommit(aggregate);
            metrics.recordCommandSuccess("CreateTransactionCommand");
        } catch (Exception e) {
            metrics.recordCommandFailure("CreateTransactionCommand");
            log.error("Failed to process CreateTransactionCommand for transaction: {}",
                    command.transactionId(), e);
            throw e;
        } finally {
            sample.stop(metrics.getCommandProcessingTimer());
        }
    }

    @Transactional
    @Retry(name = "commandProcessing")
    @Bulkhead(name = "commandProcessing", type = Bulkhead.Type.SEMAPHORE)
    public void handle(ProcessFraudCheckCommand command) {
        var sample = Timer.start();
        try {
            log.info("Processing ProcessFraudCheckCommand for transaction: {}", command.transactionId());
            var aggregate = repository.findById(command.transactionId());
            aggregate.handle(command);
            repository.save(aggregate);
            publishEventsAfterCommit(aggregate);
            metrics.recordCommandSuccess("ProcessFraudCheckCommand");
        } catch (Exception e) {
            metrics.recordCommandFailure("ProcessFraudCheckCommand");
            log.error("Failed to process ProcessFraudCheckCommand for transaction: {}", command.transactionId(), e);
            throw e;
        } finally {
            sample.stop(metrics.getCommandProcessingTimer());
        }
    }

    @Transactional
    @Retry(name = "commandProcessing")
    @Bulkhead(name = "commandProcessing", type = Bulkhead.Type.SEMAPHORE)
    public void handle(ProcessPaymentCommand command) {
        var sample = Timer.start();
        try {
            log.info("Processing ProcessPaymentCommand for transaction: {}", command.transactionId());
            var aggregate = repository.findById(command.transactionId());
            aggregate.handle(command);

            var gateway = paymentGatewayFactory.getGateway(command.paymentGateway());
            var paymentRequest = new com.paymentprocessor.payment.dto.PaymentRequest(
                command.transactionId(),
                command.amount(),
                aggregate.getCurrency(),
                aggregate.getPaymentMethod(),
                "1234-5678-9012-3456", // dummy card details
                "12/25",
                "123"
            );

            gateway.processPayment(paymentRequest)
                .doOnSuccess(response -> {
                    if (response.success()) {
                        var processedEvent = new com.paymentprocessor.common.event.PaymentProcessedEvent(
                            command.transactionId(),
                            command.amount(),
                            response.gatewayTransactionId(),
                            command.paymentGateway()
                        );
                        aggregate.apply(processedEvent);
                        var transactionCompletedEvent = new com.paymentprocessor.common.event.TransactionCompletedEvent(
                                command.transactionId(),
                                com.paymentprocessor.common.model.TransactionStatus.COMPLETED,
                                command.amount(),
                                aggregate.getUserId(),
                                aggregate.getNotificationType(),
                                aggregate.getWebhookUrl()
                        );
                        aggregate.apply(transactionCompletedEvent);
                    } else {
                        var failedEvent = new com.paymentprocessor.common.event.PaymentFailedEvent(
                            command.transactionId(),
                            response.message(),
                            "PAYMENT_GATEWAY_ERROR",
                            true, // retryable
                            aggregate.getNotificationType(),
                            aggregate.getWebhookUrl()
                        );
                        aggregate.apply(failedEvent);
                    }
                    repository.save(aggregate);
                    publishEventsAfterCommit(aggregate);
                })
                .doOnError(ex -> {
                    log.error("Payment processing failed for transaction: {}", command.transactionId(), ex);
                    var failedEvent = new com.paymentprocessor.common.event.PaymentFailedEvent(
                        command.transactionId(),
                        ex.getMessage(),
                        "PAYMENT_GATEWAY_ERROR",
                        true, // retryable
                        aggregate.getNotificationType(),
                        aggregate.getWebhookUrl()
                    );
                    aggregate.apply(failedEvent);
                    repository.save(aggregate);
                    publishEventsAfterCommit(aggregate);
                }).block(); // This is a blocking call, consider making the entire flow reactive

            metrics.recordCommandSuccess("ProcessPaymentCommand");
        } catch (Exception e) {
            metrics.recordCommandFailure("ProcessPaymentCommand");
            log.error("Failed to process ProcessPaymentCommand for transaction: {}", command.transactionId(), e);
            throw e;
        } finally {
            sample.stop(metrics.getCommandProcessingTimer());
        }
    }

    private void publishEventsAfterCommit(TransactionAggregate aggregate) {
        List<BaseEvent> events = new ArrayList<>(aggregate.getUncommittedEvents());
        aggregate.clearUncommittedEvents();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Transaction committed, publishing {} events for aggregate {}", events.size(), aggregate.getTransactionId());
                events.forEach(event -> {
                    String topic = getTopicForEvent(event.getClass().getSimpleName());
                    eventPublisher.publishAsync(topic, event.transactionId(), event)
                            .thenRun(() -> log.debug("Published event: {} for transaction: {}",
                                    event.getClass().getSimpleName(), event.transactionId()))
                            .exceptionally(ex -> {
                                log.error("Failed to publish event: {} for transaction: {}",
                                        event.getClass().getSimpleName(), event.transactionId(), ex);
                                // In production, this might go to a dead-letter queue
                                return null;
                            });
                });
            }
        });
    }

    private String getTopicForEvent(String eventType) {
        return switch (eventType) {
            case "FraudCheckRequestedEvent" -> "fraud-check-requested";
            case "PaymentProcessingStartedEvent" -> "payment-processing-started";
            case "TransactionCompletedEvent" -> "transaction-completed";
            default -> {
                log.warn("No specific topic mapping for event type: {}, using default.", eventType);
                yield "payment-events";
            }
        };
    }
}