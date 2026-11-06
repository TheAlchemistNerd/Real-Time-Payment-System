package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.notification.model.NotificationMessage;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "smtp")
public class SmtpEmailService implements NotificationService {

    private final JavaMailSender mailSender;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
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
        log.info("Sending email notification for transaction: {} to: {}",
                message.getTransactionId(), message.getRecipient());

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(message.getRecipient());
            mailMessage.setSubject(message.getSubject());
            mailMessage.setText(message.getContent());
            mailMessage.setFrom("noreply@paymentprocessor.com");

            mailSender.send(mailMessage);

            log.info("Email sent successfully for transaction: {} to: {}",
                    message.getTransactionId(), message.getRecipient());
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send email notification for transaction: {}",
                    message.getTransactionId(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
}
