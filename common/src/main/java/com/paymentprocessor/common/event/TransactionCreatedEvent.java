package com.paymentprocessor.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionCreatedEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        String userId,
        BigDecimal amount,
        Currency currency,
        PaymentMethod paymentMethod,
        String description
) implements BaseEvent {

    public TransactionCreatedEvent {
        // Compact constructor for validation and defaults
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public TransactionCreatedEvent(String transactionId, String userId, BigDecimal amount,
                                   Currency currency, PaymentMethod paymentMethod, String description) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                userId, amount, currency, paymentMethod, description);
    }

    @Override
    public String eventType() {
        return "TransactionCreated";
    }
}