package com.paymentprocessor.notification.service;

import com.paymentprocessor.notification.model.NotificationMessage;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class SMSNotificationService implements NotificationService {

    @Override
    @Async
    @Retry(name = "SmsNotification")
    @Timed(value = "notification.sms.send.time", description = "Time taken to send SMS notifications")
    @Counted(value = "notification.sms.send.count", description = "Number of SMS notifications sent")
    public CompletableFuture<Boolean> sendNotification(NotificationMessage message) {
        log.info("Sending SMS notification for transaction: {} to: {}",
                message.getTransactionId(), message.getRecipient());

        try {
            // Simulate SMS sending delay
            Thread.sleep(50);

            // For demo purposes, we'll just log instead of actually sending
            log.info("SMS sent successfully for transaction: {} to: {}",
                    message.getTransactionId(), message.getRecipient());

            return CompletableFuture.completedFuture(true);

        } catch (Exception e) {
            log.error("Failed to send SMS notification for transaction: {}",
                    message.getTransactionId(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
}
