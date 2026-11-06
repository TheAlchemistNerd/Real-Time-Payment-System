package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.notification.model.NotificationMessage;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "sendgrid")
public class SendGridEmailService implements NotificationService {

    private final String sendGridApiKey;
    private final String fromEmail;

    public SendGridEmailService(@Value("${notification.email.providers.sendgrid.api-key}") String sendGridApiKey,
                                @Value("${notification.email.providers.sendgrid.from-email}") String fromEmail) {
        this.sendGridApiKey = sendGridApiKey;
        this.fromEmail = fromEmail;
    }

    @Override
    public NotificationType channel() {
        return NotificationType.EMAIL;
    }

    @Override
    @Async("NotificationExecutor")
    @Retry(name = "emailNotification")
    @Timed(value = "notification.email.send.time", description = "Time taken to send email notifications")
    @Counted(value = "notification.email.send.count", description = "Number of email notifications sent")
    public CompletableFuture<Boolean> sendNotification(NotificationMessage message) {
        log.info("Sending email notification for transaction: {} to: {} using SendGrid",
                message.getTransactionId(), message.getRecipient());

        // This is a placeholder implementation.
        // In a real-world scenario, you would use the SendGrid API to send the email.
        log.info("SendGrid API Key: {}", sendGridApiKey);
        log.info("From Email: {}", fromEmail);
        log.info("Email sent successfully via SendGrid for transaction: {} to: {}",
                message.getTransactionId(), message.getRecipient());

        return CompletableFuture.completedFuture(true);
    }
}
