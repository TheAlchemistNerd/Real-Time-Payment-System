package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationServiceFactory {

    private final Map<NotificationType, NotificationService> notificationServices;

    public NotificationServiceFactory(List<NotificationService> services) {
        this.notificationServices = services.stream()
                .collect(Collectors.toMap(this::getNotificationType, Function.identity()));
    }

    public NotificationService getNotificationService(NotificationType type) {
        return notificationServices.get(type);
    }

    private NotificationType getNotificationType(NotificationService service) {
        if (service instanceof SmtpEmailService || service instanceof SendGridEmailService) {
            return NotificationType.EMAIL;
        } else if (service instanceof SMSNotificationService) {
            return NotificationType.SMS;
        } else if (service instanceof WebhookNotificationService) {
            return NotificationType.WEBHOOK;
        }
        throw new IllegalArgumentException("Unknown notification service type: " + service.getClass().getName());
    }
}
