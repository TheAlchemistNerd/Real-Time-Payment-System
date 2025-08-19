package com.paymentprocessor.payment.saga;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.PaymentFailedEvent;
import com.paymentprocessor.common.event.PaymentProcessedEvent;
import com.paymentprocessor.payment.command.ProcessFraudCheckCommand;
import com.paymentprocessor.payment.service.TransactionCommandService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionSagaOrchestrator {

    private final TransactionCommandService commandService;

    public TransactionSagaOrchestrator(TransactionCommandService commandService) {
        this.commandService = commandService;
    }

    @KafkaListener(topics = "fraud-check-completed", groupId = "payment-saga")
    @Retry(name = "sagaProcessing")
    public void handleFraudCheckCompleted(FraudCheckCompletedEvent event) {
        log.info("Saga: Processing fraud check result for transaction: {}", event.transactionId());
        try {
            var command = new ProcessFraudCheckCommand(
                    event.transactionId(),
                    event.passed(),
                    event.riskScore(),
                    event.reason()
            );
            commandService.handle(command);

            if (event.passed()) {
                log.info("Saga: Fraud check passed, proceeding with payment for transaction: {}", event.transactionId());
                // The ProcessFraudCheckCommand handler will have published the PaymentProcessingStartedEvent
                // which will be handled by the payment gateway integration (not part of saga).
            } else {
                log.warn("Saga: Fraud detected for transaction: {}. Saga will be completed as failed.", event.transactionId());
                // The command handler will have published the TransactionCompletedEvent, ending the saga.
            }
        } catch (Exception e) {
            log.error("Saga: Failed to process fraud check result for transaction: {}", event.transactionId(), e);
            // In a real scenario, we might publish a saga compensation event here.
        }
    }

    @KafkaListener(topics = "payment-processed", groupId = "payment-saga")
    @Retry(name = "sagaProcessing")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Saga: Payment processed successfully for transaction: {}", event.transactionId());
        // The command handler for the payment gateway's response would have already
        // published the final TransactionCompletedEvent. This listener confirms the saga's success.
    }

    @KafkaListener(topics = "payment-failed", groupId = "payment-saga")
    @Retry(name = "sagaProcessing")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("Saga: Payment failed for transaction: {} - {}", event.transactionId(), event.reason());
        // The command handler for the payment gateway's response would have already
        // published the final TransactionCompletedEvent. This listener confirms the saga's failure
        // and could trigger compensating actions.
    }
}
