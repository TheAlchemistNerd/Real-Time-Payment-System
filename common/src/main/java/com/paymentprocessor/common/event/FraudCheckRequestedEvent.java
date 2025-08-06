package com.paymentprocessor.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FraudCheckRequestedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        String userId,
        BigDecimal amount,
        String ipAddress,
        String userAgent
) implements BaseEvent {

    public FraudCheckRequestedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public FraudCheckRequestedEvent(String transactionId, String userId, BigDecimal amount,
                                    String ipAddress, String userAgent) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                userId, amount, ipAddress, userAgent);
    }

    @Override
    public String eventType() {
        return "FraudCheckRequested";
    }
}