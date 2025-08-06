package com.paymentprocessor.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionCreatedEvent.class, name = "TransactionCreated"),
        @JsonSubTypes.Type(value = FraudCheckRequestedEvent.class, name = "FraudCheckRequested"),
        @JsonSubTypes.Type(value = FraudCheckCompletedEvent.class, name = "FraudCheckCompleted"),
        @JsonSubTypes.Type(value = PaymentProcessingStartedEvent.class, name = "PaymentProcessingStarted"),
        @JsonSubTypes.Type(value = PaymentProcessedEvent.class, name = "PaymentProcessed"),
        @JsonSubTypes.Type(value = PaymentFailedEvent.class, name = "PaymentFailed"),
        @JsonSubTypes.Type(value = TransactionCompletedEvent.class, name = "TransactionCompleted"),
        @JsonSubTypes.Type(value = NotificationSentEvent.class, name = "NotificationSent")
})
public sealed interface BaseEvent permits
        TransactionCreatedEvent,
        FraudCheckRequestedEvent,
        FraudCheckCompletedEvent,
        PaymentProcessingStartedEvent,
        PaymentProcessedEvent,
        PaymentFailedEvent,
        TransactionCompletedEvent,
        NotificationSentEvent {

    String eventId();
    String transactionId();
    LocalDateTime timestamp();
    String eventType();

    // Default method for creating event ID if not provided
    static String generateEventId() {
        return java.util.UUID.randomUUID().toString();
    }
}