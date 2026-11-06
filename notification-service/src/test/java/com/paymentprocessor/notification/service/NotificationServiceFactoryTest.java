package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class NotificationServiceFactoryTest {

    private final SmtpEmailService smtpEmailService = new SmtpEmailService(mock(JavaMailSender.class));
    private final SendGridEmailService sendGridEmailService = new SendGridEmailService("test-key", "test@example.com");
    private final SMSNotificationService smsService = new SMSNotificationService();
    private final WebhookNotificationService webhookService = new WebhookNotificationService(WebClient.builder());

    @Test
    void routesEmailToTheConfiguredSmtpService() {
        var proxiedSmtp = (NotificationService) new ProxyFactory(smtpEmailService).getProxy();
        var factory = new NotificationServiceFactory(List.of(proxiedSmtp, smsService, webhookService));

        assertThat(factory.getNotificationService(NotificationType.EMAIL)).isSameAs(proxiedSmtp);
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
                .hasMessageContaining("Multiple notification services for EMAIL");
    }

    @Test
    void rejectsAnAdapterWhoseChannelIsUnknown() {
        var undeclared = mock(NotificationService.class);
        assertThatThrownBy(() -> new NotificationServiceFactory(List.of(undeclared)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must declare its channel");
    }
}
