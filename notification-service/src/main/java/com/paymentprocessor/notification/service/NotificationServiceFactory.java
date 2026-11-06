package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationServiceFactory {

    private final Map<NotificationType, NotificationService> notificationServices;

    public NotificationServiceFactory(List<NotificationService> services) {
        this.notificationServices = new EnumMap<>(NotificationType.class);
        for (NotificationService service : services) {
            NotificationType channel = service.channel();
            if (channel == null) {
                throw new IllegalArgumentException("Notification service must declare its channel");
            }
            if (notificationServices.putIfAbsent(channel, service) != null) {
                throw new IllegalStateException("Multiple notification services for " + channel);
            }
        }
    }

    public NotificationService getNotificationService(NotificationType type) {
        return notificationServices.get(type);
    }
}
