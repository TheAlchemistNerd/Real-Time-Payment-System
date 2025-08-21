package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.event.NotificationSentEvent;
import com.paymentprocessor.notification.model.NotificationMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class NotificationOrchestrator {

    private final NotificationServiceFactory serviceFactory;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private Counter notificationSuccessCounter;
    private Counter notificationFailureCounter;

    @Autowired
    public NotificationOrchestrator(NotificationServiceFactory serviceFactory,
                                    KafkaTemplate<String, Object> kafkaTemplate,
                                    MeterRegistry meterRegistry) {
        this.serviceFactory = serviceFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.notificationSuccessCounter = Counter.builder("notification.success")
                .description("Number of successful notifications")
                .register(meterRegistry);
        this.notificationFailureCounter = Counter.builder("notification.faillure")
                .description("Number of failed notifications")
                .register(meterRegistry);
    }

    public CompletableFuture<Void> sendNotification(NotificationMessage message) {
        log.info("Orchestrating notification for transaction: {} of type: {}",
                message.getTransactionId(), message.getType());

        NotificationService notificationService = serviceFactory.getNotificationService(message.getType());

        return notificationService.sendNotification(message)
                .thenAccept(success -> {
                    if (success) {
                        notificationSuccessCounter.increment();
                        log.info("Notification sent successfully for transaction: {}",
                                message.getTransactionId());
                    } else {
                        notificationFailureCounter.increment();
                        log.warn("Notification sent failed for transaction: {}",
                                message.getTransactionId());
                    }

                    publishNotificationSentEvent (message, success);
                })
                .exceptionally(throwable -> {
                    notificationFailureCounter.increment();
                    log.error("Notification orchestration failed for transaction: {}",
                            message.getTransactionId(), throwable);
                    publishNotificationSentEvent(message, false);
                    return null;
                });
    }

    private void publishNotificationSentEvent(NotificationMessage message, boolean success) {
        try {
            NotificationSentEvent event = new NotificationSentEvent(
                    message.getTransactionId(),
                    message.getType(),
                    message.getRecipient(),
                    success,
                    success ? "Notification sent successfully" : "Notification failed"
            );

            KafkaTemplate.send("notification-sent", message.getTransactionId(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Failed to publish NotificationSentEvent for transaction: {}",
                                    message.getTransactionId(), failure);
                        } else {
                            log.debug("Published NotificationSentEvent for transaction: {}",
                                    message.getTransactionId());
                        }
            });
        } catch (Exception e) {
            log.error("Error publishing NotificationSentEvent for transaction: {}",
            message.getTransactionId(), e);
        }
    }
}
