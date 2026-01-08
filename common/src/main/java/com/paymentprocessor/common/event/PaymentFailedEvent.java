package com.paymentprocessor.common.event;

import com.paymentprocessor.common.model.NotificationType;

import java.time.LocalDateTime;

public record PaymentFailedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        String reason,
        String errorCode,
        boolean retryable,
        NotificationType notificationType,
        String webhookUrl
) implements BaseEvent {

    public PaymentFailedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public PaymentFailedEvent(String transactionId, String reason, String errorCode, boolean retryable, NotificationType notificationType, String webhookUrl) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                reason, errorCode, retryable, notificationType, webhookUrl);
    }

    @Override
    public String eventType() {
        return "PaymentFailed";
    }
}