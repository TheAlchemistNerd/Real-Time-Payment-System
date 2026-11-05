package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.notification.model.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailService smtpEmailService;

    @BeforeEach
    void setUp() {
        smtpEmailService = new SmtpEmailService(mailSender);
    }

    @Test
    void sendsTheRecipientSubjectBodyAndSenderBeforeReportingSuccess() {
        var notification = message();

        var delivered = smtpEmailService.sendNotification(notification).join();

        assertThat(delivered).isTrue();
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        var email = captor.getValue();
        assertThat(email.getTo()).containsExactly(notification.getRecipient());
        assertThat(email.getSubject()).isEqualTo(notification.getSubject());
        assertThat(email.getText()).isEqualTo(notification.getContent());
        assertThat(email.getFrom()).isEqualTo("noreply@paymentprocessor.com");
        verifyNoMoreInteractions(mailSender);
    }

    @Test
    void reportsFailureWhenTheMailTransportRejectsDelivery() {
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        var delivered = smtpEmailService.sendNotification(message()).join();

        assertThat(delivered).isFalse();
        verify(mailSender).send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(mailSender);
    }

    private NotificationMessage message() {
        return new NotificationMessage("tx-123", "user-456", NotificationType.EMAIL,
                "recipient@example.com", "Payment completed", "Amount: 100.00\nReference: tx-123",
                TransactionStatus.COMPLETED, new BigDecimal("100.00"));
    }
}
