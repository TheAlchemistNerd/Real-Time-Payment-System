package com.paymentprocessor.notification.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;
import com.paymentprocessor.notification.model.NotificationMessage;
import jakarta.mail.Address;
import jakarta.mail.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Real SMTP transport boundary; Spring wiring and delivery retries are separate concerns. */
class SmtpEmailServiceIT {
    @Test
    @Timeout(20)
    void deliversTheNotificationToALoopbackMailServerBeforeReportingSuccess() throws Exception {
        var setup = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);
        setup.setServerStartupTimeout(5_000);
        setup.setReadTimeout(3_000);
        var server = new GreenMail(setup);
        try {
            server.start();
            var mailSender = new JavaMailSenderImpl();
            mailSender.setHost("127.0.0.1");
            mailSender.setPort(server.getSmtp().getPort());
            mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
            var properties = new Properties();
            properties.setProperty("mail.smtp.connectiontimeout", "3000");
            properties.setProperty("mail.smtp.timeout", "3000");
            properties.setProperty("mail.smtp.writetimeout", "3000");
            properties.setProperty("mail.smtp.localhost", "localhost");
            mailSender.setJavaMailProperties(properties);
            var notification = new NotificationMessage("smtp-transaction-1", "user-1", NotificationType.EMAIL,
                    "recipient@example.com", "Payment completed", "Amount: 100.00\nReference: smtp-transaction-1",
                    TransactionStatus.COMPLETED, new BigDecimal("100.00"));

            var delivered = new SmtpEmailService(mailSender).sendNotification(notification).join();

            assertThat(delivered).isTrue();
            assertThat(server.waitForIncomingEmail(5_000, 1))
                    .as("notification received by the local SMTP server").isTrue();
            var messages = server.getReceivedMessages();
            assertThat(messages).hasSize(1);
            var received = messages[0];
            assertThat(received.getRecipients(Message.RecipientType.TO))
                    .extracting(Address::toString).containsExactly(notification.getRecipient());
            assertThat(received.getFrom())
                    .extracting(Address::toString).containsExactly("noreply@paymentprocessor.com");
            assertThat(received.getSubject()).isEqualTo(notification.getSubject());
            assertThat(received.getContent()).isInstanceOf(String.class);
            assertThat(((String) received.getContent()).replace("\r\n", "\n").stripTrailing())
                    .isEqualTo(notification.getContent());
        } finally {
            server.stop();
        }
    }
}
