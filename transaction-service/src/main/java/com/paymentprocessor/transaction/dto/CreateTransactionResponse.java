package com.paymentprocessor.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paymentprocessor.common.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTransactionResponse(
        @JsonProperty("transactionId")
        String transactionId,

        @JsonProperty("status")
        TransactionStatus status,

        @JsonProperty("message")
        String message,

        @JsonProperty("timestamp")
        LocalDateTime timestamp,

        @JsonProperty("webhookUrl")
        String webhookUrl
) {
    public CreateTransactionResponse {
        timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    // Convenience constructor without timestamp (will use current time)
    public CreateTransactionResponse(String transactionId, TransactionStatus status,
                                     String message, String webhookUrl) {
        this(transactionId, status, message, LocalDateTime.now(), webhookUrl);
    }

    // Factory methods for common responses
    public static CreateTransactionResponse success(String transactionId, String webhookUrl) {
        return new CreateTransactionResponse(
                transactionId,
                TransactionStatus.PENDING,
                "Transaction created successfully",
                webhookUrl
        );
    }


    public static CreateTransactionResponse success(String transactionId) {
        return success(transactionId, null);
    }

    public static CreateTransactionResponse failed(String transactionId, String message) {
        return new CreateTransactionResponse(
                transactionId,
                TransactionStatus.FAILED,
                message,
                null
        );
    }

    // Helper methods
    public boolean isSuccessful() {
        return status == TransactionStatus.PENDING || status == TransactionStatus.COMPLETED;
    }

    public boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

}
