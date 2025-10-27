package com.paymentprocessor.common.event;

import com.paymentprocessor.common.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentProcessingStartedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String paymentGateway
) implements BaseEvent {

    public PaymentProcessingStartedEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public PaymentProcessingStartedEvent(String transactionId, BigDecimal amount,
                                         PaymentMethod paymentMethod, String paymentGateway) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                amount, paymentMethod, paymentGateway);
    }

    @Override
    public String eventType() {
        return "PaymentProcessingStarted";
    }
}
