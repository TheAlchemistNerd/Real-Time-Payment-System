package com.paymentprocessor.payment.service;

import com.paymentprocessor.common.event.BaseEvent;
import com.paymentprocessor.payment.aggregate.TransactionAggregate;
import com.paymentprocessor.payment.command.CreateTransactionCommand;
import com.paymentprocessor.payment.command.ProcessFraudCheckCommand;
import com.paymentprocessor.payment.command.ProcessPaymentCommand;
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

    public TransactionCommandService(TransactionAggregateRepository repository,
                                     EventPublisher eventPublisher,
                                     PaymentMetrics metrics) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
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
            log.error("Failed to process CreateTransactionCommand for transaction: {}", command.transactionId(), e);
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
            repository.save(aggregate);
            publishEventsAfterCommit(aggregate);
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