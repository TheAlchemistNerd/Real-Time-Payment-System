package com.paymentprocessor.notification.model;

public class WebhookPayload {

    private String transactionId;
    private String status;
    private String message;
    private String timestamp;
    private Object data;

    public WebhookPayload() {}

    public WebhookPayload(String transactionId, String status, String message, Object data) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
        this.timestamp = java.time.LocalDateTime.now().toString();
        this.data = data;
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}