package com.paymentprocessor.payment.projection;

import com.paymentprocessor.common.event.*;
import com.paymentprocessor.payment.query.TransactionReadModel;
import com.paymentprocessor.payment.query.TransactionReadModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class TransactionProjectionHandler {

    private final TransactionReadModelRepository repository;


    public TransactionProjectionHandler(TransactionReadModelRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "transaction-events", groupId = "transaction-projection")
    @Transactional
    public void handleEvent(BaseEvent event) {
        log.info("Processing event: {} for transaction: {}",
                event.eventType(), event.transactionId());

        try {
            switch(event) {
                case TransactionCreatedEvent e -> handleTransactionCreated(e);
                case FraudCheckCompletedEvent e -> handleFraudCheckCompleted(e);
                case PaymentProcessingStartedEvent e -> handlePaymentProcessingStarted(e);
                case PaymentProcessedEvent e -> handlePaymentProcessed(e);
                case PaymentFailedEvent e -> handlePaymentFailed(e);
                default -> log.warn("Unknown event type: {}",
                        event.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Failed to process event: {} for transaction: {}",
                    event.eventType(), event.transactionId(), e);
            throw e; // Re-throw to trigger retry mechanism
        }
    }

    private void handleTransactionCreated(TransactionCreatedEvent event) {
        var readModel = TransactionReadModel.fromTransactionCreated(
                event.transactionId(),
                event.userId(),
                event.amount(),
                event.currency(),
                event.paymentMethod(),
                event.description(),
                event.timestamp()
        );

        repository.save(readModel);
        log.info("Created read model for transaction: {}",
                event.transactionId());
    }

    private void handleFraudCheckCompleted(FraudCheckCompletedEvent event) {
        repository.findById(event.transactionId())
                .ifPresentOrElse(
                        readModel -> {
                            readModel.updateFraudCheckResult(
                                    event.passed(),
                                    event.riskScore(),
                                    event.reason(),
                                    event.timestamp()
                            );
                            repository.save(readModel);
                            log.info("Updated read model for fraud check: {}",
                                    event.transactionId());
                        },
                        () -> log.warn("Read model not found for transaction: {}", event.transactionId())
                );
    }

    private void handlePaymentProcessingStarted(PaymentProcessingStartedEvent event) {
        repository.findById(event.transactionId())
                .ifPresent(readModel -> {
                    readModel.updatePaymentProcessingStarted();
                    repository.save(readModel);
                    log.info("Updated read model for payment processing start: {}", event.transactionId());
                });
    }

    private void handlePaymentProcessed(PaymentProcessedEvent event) {
        repository.findById(event.transactionId())
                .ifPresent(readModel -> {
                    readModel.updatePaymentCompleted(
                            event.paymentGatewayTransactionId(),
                            event.timestamp()
                    );
                    repository.save(readModel);
                    log.info("Updated read model for payment completion: {}", event.transactionId());
                });
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        repository.findById(event.transactionId())
                .ifPresent(readModel -> {
                    readModel.updatePaymentFailed(event.timestamp());
                    repository.save(readModel);
                    log.info("Updated read model for payment failure: {}", event.transactionId());
                });
    }
}
