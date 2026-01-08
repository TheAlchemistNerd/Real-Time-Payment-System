package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;
import java.math.BigDecimal;    
import com.paymentprocessor.notification.model.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailService smtpEmailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        smtpEmailService = new SmtpEmailService(mailSender);
    }

    @Test
    void testSendNotification() throws ExecutionException, InterruptedException {
        NotificationMessage message = new NotificationMessage("123", 
        "user1", NotificationType.EMAIL, "test@example.com", "Test Subject", "Test message", TransactionStatus.COMPLETED, BigDecimal.TEN);

        //doNothing().when(mailSender).send(new SimpleMailMessage());

        CompletableFuture<Boolean> result = smtpEmailService.sendNotification(message);

        assertTrue(result.get());
//        ArgumentCaptor<SimpleMailMessage> mailMessageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
//        verify(mailSender).send(mailMessageCaptor.capture());
//
//        SimpleMailMessage sentMessage = mailMessageCaptor.getValue();
//        assertEquals("test@example.com", sentMessage.getTo()[0]);
//        assertEquals("Test Subject", sentMessage.getSubject());
//        assertEquals("Test message", sentMessage.getText());
//        assertEquals("noreply@paymentprocessor.com", sentMessage.getFrom());
    }
}
