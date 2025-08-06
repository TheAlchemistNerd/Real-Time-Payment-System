package com.paymentprocessor.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentProcessedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        BigDecimal amount,
        String paymentGatewayTransactionId,
        String paymentGateway
) implements BaseEvent {

    public PaymentProcessedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public PaymentProcessedEvent(String transactionId, BigDecimal amount,
                                 String paymentGatewayTransactionId, String paymentGateway) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                amount, paymentGatewayTransactionId, paymentGateway);
    }

    @Override
    public String eventType() {
        return "PaymentProcessed";
    }
}