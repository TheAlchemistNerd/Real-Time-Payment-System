package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NotificationServiceFactoryTest {

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private SMSNotificationService smsService;

    @Mock
    private WebhookNotificationService webhookService;

    private NotificationServiceFactory notificationServiceFactory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationServiceFactory = new NotificationServiceFactory(emailService, smsService, webhookService);
    }

    @Test
    void testGetNotificationService_Email() {
        NotificationService service = notificationServiceFactory.getNotificationService(NotificationType.EMAIL);
        assertInstanceOf(EmailNotificationService.class, service);
    }

    @Test
    void testGetNotificationService_Sms() {
        NotificationService service = notificationServiceFactory.getNotificationService(NotificationType.SMS);
        assertInstanceOf(SMSNotificationService.class, service);
    }

    @Test
    void testGetNotificationService_Webhook() {
        NotificationService service = notificationServiceFactory.getNotificationService(NotificationType.WEBHOOK);
        assertInstanceOf(WebhookNotificationService.class, service);
    }

    @Test
    void testGetNotificationService_Default() {
        NotificationService service = notificationServiceFactory.getNotificationService(null);
        assertInstanceOf(EmailNotificationService.class, service);
    }
}
