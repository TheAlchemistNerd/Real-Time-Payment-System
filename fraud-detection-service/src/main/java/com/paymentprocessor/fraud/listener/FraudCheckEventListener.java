package com.paymentprocessor.fraud.listener;

import com.paymentprocessor.common.event.FraudCheckRequestedEvent;
import com.paymentprocessor.fraud.service.FraudDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FraudCheckEventListener {

    private final FraudDetectionService fraudDetectionService;

    @Autowired
    public FraudCheckEventListener(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @KafkaListener(topics = "fraud-check-requested", groupId = "fraud-detection-service")
    public void handleFraudCheckRequested(FraudCheckRequestedEvent event, Acknowledgment acknowledgment) {
        log.info("Received FraudCheckRequestedEvent for transaction: {}", event.transactionId());

        try {
            fraudDetectionService.checkFraud(event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to process fraud check for transaction: {}",
                                    event.transactionId());
                            // Don't acknowledge the message to trigger retry
                        } else {
                            log.info("Successfully processed fraud check for transaction: {}",
                                    event.transactionId());
                            acknowledgment.acknowledge();
                        }
                    });

        } catch (Exception e) {
            log.error("Error handling FraudCheckRequestedEvent for transaction: {}",
                    event.transactionId(), e);
            // Don't acknowledge to trigger retry
            throw e;
        }
    }
}