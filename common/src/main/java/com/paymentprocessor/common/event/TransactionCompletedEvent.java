package com.paymentprocessor.common.event;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionCompletedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        TransactionStatus finalStatus,
        BigDecimal amount,
        String userId,
        NotificationType notificationType,
        String webhookUrl
) implements BaseEvent {

    public TransactionCompletedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public TransactionCompletedEvent(String transactionId, TransactionStatus finalStatus,
                                     BigDecimal amount, String userId, NotificationType notificationType, String webhookUrl) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                finalStatus, amount, userId, notificationType, webhookUrl);
    }

    @Override
    public String eventType() {
        return "TransactionCompleted";
    }
}
