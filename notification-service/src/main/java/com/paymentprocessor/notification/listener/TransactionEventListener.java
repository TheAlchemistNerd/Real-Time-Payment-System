package com.paymentprocessor.notification.listener;

import com.paymentprocessor.common.event.FraudCheckCompletedEvent;
import com.paymentprocessor.common.event.PaymentFailedEvent;
import com.paymentprocessor.common.event.TransactionCompletedEvent;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.notification.model.NotificationMessage;
import com.paymentprocessor.notification.service.NotificationOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionEventListener {

    private final NotificationOrchestrator notificationOrchestrator;

    public TransactionEventListener(NotificationOrchestrator notificationOrchestrator) {
        this.notificationOrchestrator = notificationOrchestrator;
    }

    @KafkaListener(topics = "transaction-completed", groupId = "notification-service")
    public void handleTransactionCompleted(TransactionCompletedEvent event, Acknowledgment acknowledgment) {
        log.info("Received TransactionCompletedEvent for transaction: {}", event.transactionId());
        try {
            NotificationMessage notification = createTransactionCompletedNotification(event);
            notificationOrchestrator.sendNotification(notification)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to send transaction completed notification for: {}",
                                    event.transactionId(), throwable);
                        } else {
                            log.info("Transaction completed notification processed for: {}",
                                    event.transactionId());
                        }
                        acknowledgment.acknowledge();
                    });
        } catch (Exception e) {
            log.error("Error processing TransactionCompletedEvent for: {}", event.transactionId(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
        }
    }

    @KafkaListener(topics = "fraud-check-completed", groupId = "notification-service-fraud")
    public void handleFraudCheckCompleted(FraudCheckCompletedEvent event, Acknowledgment acknowledgment) {
        log.info("Received FraudCheckCompletedEvent for transaction: {}", event.transactionId());

        try {
            // Only send notification if fraud was detected
            if (!event.passed()) {
                NotificationMessage notification = createFraudDetectedNotification(event);

                notificationOrchestrator.sendNotification(notification)
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                log.error("Failed to send fraud detected notification for: {}",
                                        event.transactionId(), throwable);
                            } else {
                                log.info("Fraud detected notification processed for: {}",
                                        event.transactionId());
                            }
                            acknowledgment.acknowledge();
                        });
            } else {
                log.debug("Fraud check passed for transaction: {}, no notification needed",
                        event.transactionId());
                acknowledgment.acknowledge();
            }

        } catch (Exception e) {
            log.error("Error processing FraudCheckCompletedEvent for: {}", event.transactionId(), e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "notification-service-payment")
    public void handlePaymentFailed(PaymentFailedEvent event, Acknowledgment acknowledgment) {
        log.info("Received PaymentFailedEvent for transaction: {}", event.transactionId());

        try {
            NotificationMessage notification = createPaymentFailedNotification(event);

            notificationOrchestrator.sendNotification(notification)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Failed to send payment failed notification for: {}",
                                    event.transactionId(), throwable);
                        } else {
                            log.info("Payment failed notification processed for: {}",
                                    event.transactionId());
                        }
                        acknowledgment.acknowledge();
                    });

        } catch (Exception e) {
            log.error("Error processing PaymentFailedEvent for: {}", event.transactionId(), e);
            acknowledgment.acknowledge();
        }
    }

    private NotificationMessage createTransactionCompletedNotification(TransactionCompletedEvent event) {
        String subject = "Transaction " + (event.finalStatus() == TransactionStatus.COMPLETED ?
                "Completed" : "Failed");

        String content = String.format(
                "Your transaction %s has been %s.\n\nAmount: %s\n\nThank you for using our service.",
                event.transactionId(),
                event.finalStatus() == TransactionStatus.COMPLETED ? "completed successfully" : "declined",
                event.amount() != null ? "$" + event.amount() : "N/A"
        );

        return new NotificationMessage(
                event.transactionId(),
                event.userId(),
                NotificationType.EMAIL, // Default to email
                getUserEmail(event.userId()), // Mock method to get user email
                subject,
                content,
                event.finalStatus(),
                event.amount()
        );
    }

    private NotificationMessage createFraudDetectedNotification(FraudCheckCompletedEvent event) {
        String subject = "Transaction Security Alert";

        String content = String.format(
                "Your transaction %s has been declined for security reasons.\n\n" +
                        "Risk Score: %.2f\n" +
                        "Reason: %s\n\n" +
                        "If this was a legitimate transaction, please contact our support team.",
                event.transactionId(),
                event.riskScore(),
                event.reason()
        );

        return new NotificationMessage(
                event.transactionId(),
                null, // userId not available in fraud event
                NotificationType.EMAIL,
                "user@example.com", // Mock recipient
                subject,
                content,
                TransactionStatus.FRAUD_CHECK_FAILED,
                null
        );
    }

    private NotificationMessage createPaymentFailedNotification(PaymentFailedEvent event) {
        String subject = "Payment Processing Failed";

        String content = String.format(
                "Your payment for transaction %s could not be processed.\n\n" +
                        "Reason: %s\n" +
                        "Error Code: %s\n\n" +
                        "Please try again or use a different payment method.",
                event.transactionId(),
                event.reason(),
                event.errorCode()
        );

        return new NotificationMessage(
                event.transactionId(),
                null, // userId not available in payment event
                NotificationType.EMAIL,
                "user@example.com", // Mock recipient
                subject,
                content,
                TransactionStatus.FAILED,
                null
        );
    }

    private String getUserEmail(String userId) {
        // Mock method - in real implementation, this would lookup user email from database
        return userId + "@example.com";
    }
}
