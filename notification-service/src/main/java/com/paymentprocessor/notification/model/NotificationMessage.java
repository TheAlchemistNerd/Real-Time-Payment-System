package com.paymentprocessor.notification.model;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.common.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class NotificationMessage {

    private String transactionId;
    private String userId;
    private NotificationType type;
    private String recipient;
    private String subject;
    private String content;
    private TransactionStatus transactionStatus;
    private BigDecimal amount;
    private LocalDateTime timestamp;

    public NotificationMessage() {}

    public NotificationMessage(String transactionId, String userId, NotificationType type,
                               String recipient, String subject, String content,
                               TransactionStatus transactionStatus, BigDecimal amount) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.type = type;
        this.recipient = recipient;
        this.subject = subject;
        this.content = content;
        this.transactionStatus = transactionStatus;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public TransactionStatus getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(TransactionStatus transactionStatus) { this.transactionStatus = transactionStatus; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}