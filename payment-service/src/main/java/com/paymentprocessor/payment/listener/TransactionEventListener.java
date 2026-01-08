package com.paymentprocessor.payment.listener;

import com.paymentprocessor.common.command.CreateTransactionCommand;
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

    @KafkaListener(topics = "create-transaction", groupId = "payment-service")
    public void handleCreateTransaction(CreateTransactionCommand command) {
        log.info("Received CreateTransactionCommand for transaction: {}", command.transactionId());
        try {
            commandService.handle(command);

            log.info("Successfully processed CreateTransactionCommand for transaction: {}",
                    command.transactionId());

        } catch (Exception e) {
            log.error("Failed to process CreateTransactionCommand for transaction: {}",
                    command.transactionId(), e);
            throw e; // Allow Kafka's retry mechanism to handle it
        }
    }
}
