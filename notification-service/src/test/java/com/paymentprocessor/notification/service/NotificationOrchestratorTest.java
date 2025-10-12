package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.event.NotificationSentEvent;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.notification.model.NotificationMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationOrchestratorTest {

    @Mock
    private NotificationServiceFactory serviceFactory;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private NotificationService notificationService;

    private NotificationOrchestrator notificationOrchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        notificationOrchestrator = new NotificationOrchestrator(serviceFactory, kafkaTemplate, meterRegistry);
    }

    @Test
    void testSendNotification_Success() {
        NotificationMessage message = new NotificationMessage("123", NotificationType.EMAIL, "test@example.com", "Test message");
        when(serviceFactory.getNotificationService(NotificationType.EMAIL)).thenReturn(notificationService);
        when(notificationService.sendNotification(message)).thenReturn(CompletableFuture.completedFuture(true));
        when(kafkaTemplate.send(anyString(), anyString(), any(NotificationSentEvent.class))).thenReturn(CompletableFuture.completedFuture(null));

        notificationOrchestrator.sendNotification(message);

        verify(notificationService).sendNotification(message);
        ArgumentCaptor<NotificationSentEvent> eventCaptor = ArgumentCaptor.forClass(NotificationSentEvent.class);
        verify(kafkaTemplate).send(eq("notification-sent"), eq("123"), eventCaptor.capture());
        assertEquals(true, eventCaptor.getValue().success());
    }

    @Test
    void testSendNotification_Failure() {
        NotificationMessage message = new NotificationMessage("123", NotificationType.EMAIL, "test@example.com", "Test message");
        when(serviceFactory.getNotificationService(NotificationType.EMAIL)).thenReturn(notificationService);
        when(notificationService.sendNotification(message)).thenReturn(CompletableFuture.completedFuture(false));
        when(kafkaTemplate.send(anyString(), anyString(), any(NotificationSentEvent.class))).thenReturn(CompletableFuture.completedFuture(null));

        notificationOrchestrator.sendNotification(message);

        verify(notificationService).sendNotification(message);
        ArgumentCaptor<NotificationSentEvent> eventCaptor = ArgumentCaptor.forClass(NotificationSentEvent.class);
        verify(kafkaTemplate).send(eq("notification-sent"), eq("123"), eventCaptor.capture());
        assertFalse(eventCaptor.getValue().isSuccess());
    }

    @Test
    void testSendNotification_Exception() {
        NotificationMessage message = new NotificationMessage("123", NotificationType.EMAIL, "test@example.com", "Test message");
        when(serviceFactory.getNotificationService(NotificationType.EMAIL)).thenReturn(notificationService);
        when(notificationService.sendNotification(message)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Test exception")));
        when(kafkaTemplate.send(anyString(), anyString(), any(NotificationSentEvent.class))).thenReturn(CompletableFuture.completedFuture(null));

        notificationOrchestrator.sendNotification(message);

        verify(notificationService).sendNotification(message);
        ArgumentCaptor<NotificationSentEvent> eventCaptor = ArgumentCaptor.forClass(NotificationSentEvent.class);
        verify(kafkaTemplate).send(eq("notification-sent"), eq("123"), eventCaptor.capture());
        assertFalse(eventCaptor.getValue().isSuccess());
    }
}
