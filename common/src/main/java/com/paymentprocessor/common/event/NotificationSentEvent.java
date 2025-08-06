package com.paymentprocessor.common.event;

import com.paymentprocessor.common.model.NotificationType;
import java.time.LocalDateTime;

public record NotificationSentEvent(
        String eventId,
        String transactionId,
        LocalDateTime timestamp,
        NotificationType notificationType,
        String recipient,
        boolean success,
        String message
) implements BaseEvent {

    public NotificationSentEvent {
        eventId = eventId != null ? eventId : BaseEvent.generateEventId();
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public NotificationSentEvent(String transactionId, NotificationType notificationType,
                                 String recipient, boolean success, String message) {
        this(BaseEvent.generateEventId(), transactionId, LocalDateTime.now(),
                notificationType, recipient, success, message);
    }

    @Override
    public String eventType() {
        return "NotificationSent";
    }
}
