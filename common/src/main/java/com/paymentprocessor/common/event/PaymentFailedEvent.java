package com.paymentprocessor.common.event;

import java.time.LocalDateTime;

public record PaymentFailedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        String reason,
        String errorCode,
        boolean retryable
) implements BaseEvent {

    public PaymentFailedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public PaymentFailedEvent(String transactionId, String reason, String errorCode, boolean retryable) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                reason, errorCode, retryable);
    }

    @Override
    public String eventType() {
        return "PaymentFailed";
    }
}