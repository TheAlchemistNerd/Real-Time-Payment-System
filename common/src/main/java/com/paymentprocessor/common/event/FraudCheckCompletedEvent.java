package com.paymentprocessor.common.event;

import java.time.LocalDateTime;

public record FraudCheckCompletedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        boolean passed,
        double riskScore,
        String reason
) implements BaseEvent {

    public FraudCheckCompletedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public FraudCheckCompletedEvent(String transactionId, boolean passed, double riskScore, String reason) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                passed, riskScore, reason);
    }

    @Override
    public String eventType() {
        return "FraudCheckCompleted";
    }
}