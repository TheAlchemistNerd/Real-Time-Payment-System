package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.event.NotificationSentEvent;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.notification.model.NotificationMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private NotificationServiceFactory serviceFactory;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private NotificationService notificationService;

    private NotificationOrchestrator notificationOrchestrator;

    @BeforeEach
    void setUp() {
        notificationOrchestrator = new NotificationOrchestrator(serviceFactory, kafkaTemplate, new SimpleMeterRegistry());
        when(kafkaTemplate.send(eq("notification-sent"), eq("tx-123"), any(NotificationSentEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"EMAIL", "SMS", "WEBHOOK"})
    void publishesSuccessfulDeliveryWithTheOriginalChannelAndRecipient(NotificationType type) {
        var message = message(type);
        when(serviceFactory.getNotificationService(type)).thenReturn(notificationService);
        when(notificationService.sendNotification(message)).thenReturn(CompletableFuture.completedFuture(true));

        notificationOrchestrator.sendNotification(message).join();

        var event = publishedEvent(message);
        assertThat(event.success()).isTrue();
        assertThat(event.message()).isEqualTo("Notification sent successfully");
    }

    @Test
    void publishesFailedDeliveryWhenTheAdapterReturnsFalse() {
        var message = message(NotificationType.EMAIL);
        when(serviceFactory.getNotificationService(NotificationType.EMAIL)).thenReturn(notificationService);
        when(notificationService.sendNotification(message)).thenReturn(CompletableFuture.completedFuture(false));

        notificationOrchestrator.sendNotification(message).join();

        var event = publishedEvent(message);
        assertThat(event.success()).isFalse();
        assertThat(event.message()).isEqualTo("Notification failed");
    }

    @Test
    void publishesFailedDeliveryWhenTheAdapterFutureFails() {
        var message = message(NotificationType.EMAIL);
        when(serviceFactory.getNotificationService(NotificationType.EMAIL)).thenReturn(notificationService);
        when(notificationService.sendNotification(message))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Delivery provider unavailable")));

        notificationOrchestrator.sendNotification(message).join();

        var event = publishedEvent(message);
        assertThat(event.success()).isFalse();
        assertThat(event.message()).isEqualTo("Notification failed");
    }

    @Test
    void waitsForTheAdapterOutcomeBeforePublishingDelivery() {
        var message = message(NotificationType.EMAIL);
        var delivery = new CompletableFuture<Boolean>();
        when(serviceFactory.getNotificationService(NotificationType.EMAIL)).thenReturn(notificationService);
        when(notificationService.sendNotification(message)).thenReturn(delivery);

        var completion = notificationOrchestrator.sendNotification(message);

        assertThat(completion.isDone()).isFalse();
        verifyNoInteractions(kafkaTemplate);

        delivery.complete(true);
        completion.join();

        assertThat(publishedEvent(message).success()).isTrue();
    }

    private NotificationSentEvent publishedEvent(NotificationMessage message) {
        verify(serviceFactory).getNotificationService(message.getType());
        verify(notificationService).sendNotification(message);
        var captor = ArgumentCaptor.forClass(NotificationSentEvent.class);
        verify(kafkaTemplate).send(eq("notification-sent"), eq(message.getTransactionId()), captor.capture());
        var event = captor.getValue();
        assertThat(event.transactionId()).isEqualTo(message.getTransactionId());
        assertThat(event.notificationType()).isEqualTo(message.getType());
        assertThat(event.recipient()).isEqualTo(message.getRecipient());
        verifyNoMoreInteractions(serviceFactory, notificationService, kafkaTemplate);
        return event;
    }

    private NotificationMessage message(NotificationType type) {
        String recipient = switch (type) {
            case EMAIL -> "recipient@example.com";
            case SMS -> "+254700000001";
            case WEBHOOK -> "https://merchant.example/payment-events";
            default -> throw new IllegalArgumentException("Unsupported test channel: " + type);
        };
        return new NotificationMessage("tx-123", "user-456", type, recipient,
                "Payment completed", "Your payment has completed.", TransactionStatus.COMPLETED, new BigDecimal("100.00"));
    }
}
