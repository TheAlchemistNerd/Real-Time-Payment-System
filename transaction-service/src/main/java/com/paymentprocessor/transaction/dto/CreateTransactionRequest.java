package com.paymentprocessor.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paymentprocessor.common.model.Currency;
import com.paymentprocessor.common.model.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Objects;

public record CreateTransactionRequest(
        @NotNull(message = "User ID is required")
        @NotBlank(message = "User ID cannot be blank")
        @JsonProperty("userId")
        String userId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
        @JsonProperty("amount")
        BigDecimal amount,

        @NotNull(message = "Currency is required")
        @JsonProperty("currency")
        Currency currency,

        @NotNull(message = "Payment method is required")
        @JsonProperty("paymentMethod")
        PaymentMethod paymentMethod,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        @JsonProperty("description")
        String description,

        @JsonProperty("webhookUrl")
        String webhookUrl,

        @JsonProperty("ipAddress")
        String ipAddress,

        @JsonProperty("userAgent")
        String userAgent
) {
    //Compact constructor for validation and normalization
    public CreateTransactionRequest {
        // Additional validation that can't be done with annotations
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        Objects.requireNonNull(paymentMethod, "Payment method cannot be null");

        // Normalize data
        userId = userId.trim();
        description = description != null ? description.trim() : null;

        // Validate amount precision
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }
    }

    // Convenience constructor; for basic transaction creation
    public CreateTransactionRequest(String userId, BigDecimal amount, Currency currency,
                                    PaymentMethod paymentMethod, String description) {
        this(userId, amount, currency, paymentMethod, description, null, null, null);
    }

    // Factory method for creating with metadata
    public static CreateTransactionRequest withMetadata(
            String userId,
            BigDecimal amount,
            Currency currency,
            PaymentMethod paymentMethod,
            String description,
            String ipAddress,
            String userAgent) {
        return new CreateTransactionRequest(userId, amount, currency, paymentMethod,
                description, null, ipAddress, userAgent);
    }

    // Helper methods
    public boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    public boolean hasMetadata() {
        return ipAddress != null || userAgent != null;
    }
}
