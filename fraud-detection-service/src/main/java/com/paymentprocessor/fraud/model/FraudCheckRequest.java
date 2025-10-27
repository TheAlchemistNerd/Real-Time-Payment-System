package com.paymentprocessor.fraud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * Represents a request to an external fraud detection API.
 * This record includes validation constraints to ensure the integrity of the data.
 */
public record FraudCheckRequest(
        @NotNull(message = "User ID cannot be null")
        @NotBlank(message = "User ID cannot be blank")
        @JsonProperty("user_id") String userId,

        @NotNull(message = "Transaction amount cannot be null")
        @DecimalMin(value = "0.01", message = "Transaction amount must be positive")
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,

        @NotNull(message = "Currency cannot be null")
        @NotBlank(message = "Currency cannot be blank")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g., USD)")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
        @JsonProperty("currency") String currency,

        @JsonProperty("ip_address") String ipAddress, // Optional, can be null

        @JsonProperty("user_agent") String userAgent, // Optional, can be null

        @NotNull(message = "Transaction ID cannot be null")
        @NotBlank(message = "Transaction ID cannot be blank")
        @JsonProperty("transaction_id") String transactionId
) {
    // Compact constructor for validation logic
    public FraudCheckRequest {
        // Additional custom validation logic can be placed here if needed,
        // beyond what annotations provide.
        // For example, more complex business rules.
        if (transactionAmount != null && transactionAmount.scale() > 2) {
            throw new IllegalArgumentException("Transaction amount cannot have more than 2 decimal places.");
        }
    }
}