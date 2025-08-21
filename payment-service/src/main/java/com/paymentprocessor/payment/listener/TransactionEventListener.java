package com.paymentprocessor.payment.listener;

import com.paymentprocessor.common.event.TransactionCreatedEvent;
import com.paymentprocessor.payment.command.CreateTransactionCommand;
import com.paymentprocessor.payment.service.TransactionCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionEventListener {

    private final TransactionCommandService commandService;

    public TransactionEventListener(TransactionCommandService commandService) {
        this.commandService = commandService;
    }

    @KafkaListener(topics = "transaction-created", groupId = "payment-service")
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        log.info("Received TransactionCreatedEvent for transaction: {}", event.transactionId());
        try {
            var command = new CreateTransactionCommand(
                    event.transactionId(),
                    event.userId(),
                    event.amount(),
                    event.currency(),
                    event.paymentMethod(),
                    event.description()
            );
            commandService.handle(command);

            log.info("Successfully processed TransactionCreatedEvent for transaction: {}",
                    event.transactionId());

        } catch (Exception e) {
            log.error("Failed to process TransactionCreatedEvent for transaction: {}",
                    event.transactionId(), e);
            throw e; // Allow Kafka's retry mechanism to handle it
        }
    }
}
