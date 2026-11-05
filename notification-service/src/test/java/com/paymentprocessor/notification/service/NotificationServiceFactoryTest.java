package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class NotificationServiceFactoryTest {

    @Mock
    private SmtpEmailService smtpEmailService;

    @Mock
    private SendGridEmailService sendGridEmailService;

    @Mock
    private SMSNotificationService smsService;

    @Mock
    private WebhookNotificationService webhookService;

    @Mock
    private NotificationService unknownService;

    @Test
    void routesEmailToTheConfiguredSmtpService() {
        var factory = new NotificationServiceFactory(List.of(smtpEmailService, smsService, webhookService));

        assertThat(factory.getNotificationService(NotificationType.EMAIL)).isSameAs(smtpEmailService);
    }

    @Test
    void routesEmailToTheConfiguredSendGridService() {
        var factory = new NotificationServiceFactory(List.of(sendGridEmailService, smsService, webhookService));

        assertThat(factory.getNotificationService(NotificationType.EMAIL)).isSameAs(sendGridEmailService);
    }

    @Test
    void routesSmsToTheRegisteredSmsService() {
        var factory = new NotificationServiceFactory(List.of(smtpEmailService, smsService, webhookService));

        assertThat(factory.getNotificationService(NotificationType.SMS)).isSameAs(smsService);
    }

    @Test
    void routesWebhookToTheRegisteredWebhookService() {
        var factory = new NotificationServiceFactory(List.of(smtpEmailService, smsService, webhookService));

        assertThat(factory.getNotificationService(NotificationType.WEBHOOK)).isSameAs(webhookService);
    }

    @Test
    void rejectsAmbiguousEmailProviderRegistration() {
        assertThatThrownBy(() -> new NotificationServiceFactory(List.of(smtpEmailService, sendGridEmailService)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate key EMAIL");
    }

    @Test
    void rejectsAnAdapterWhoseChannelIsUnknown() {
        assertThatThrownBy(() -> new NotificationServiceFactory(List.of(unknownService)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown notification service type");
    }
}
