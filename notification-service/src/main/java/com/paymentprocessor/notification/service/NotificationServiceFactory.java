package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationServiceFactory {

    private final EmailNotificationService  emailService;
    private final SMSNotificationService smsService;
    private final WebhookNotificationService webhookervice;

    @Autowired
    public NotificationServiceFactory(EmailNotificationService emailService, SMSNotificationService smsService, WebhookNotificationService webhookService) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.webhookervice = webhookService;
    }

    public NotificationService getNotificationService(NotificationType type) {
        return switch (type) {
            case EMAIL -> emailService;
            case SMS -> smsService;
            case WEBHOOK -> webhookervice;
            default -> emailService;
        };
    }
}
